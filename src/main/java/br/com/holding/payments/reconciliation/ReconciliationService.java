package br.com.holding.payments.reconciliation;

import br.com.holding.payments.charge.Charge;
import br.com.holding.payments.charge.ChargeRepository;
import br.com.holding.payments.charge.ChargeStatus;
import br.com.holding.payments.integration.asaas.client.AsaasPaymentClient;
import br.com.holding.payments.integration.asaas.dto.AsaasPageResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPaymentResponse;
import br.com.holding.payments.outbox.OutboxEvent;
import br.com.holding.payments.outbox.OutboxEventRepository;
import br.com.holding.payments.outbox.OutboxStatus;
import br.com.holding.payments.reconciliation.dto.DlqReplayResult;
import br.com.holding.payments.reconciliation.dto.ReconciliationResult;
import br.com.holding.payments.reconciliation.dto.ReconciliationResult.Divergence;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.integration.asaas.client.AsaasSubscriptionClient;
import br.com.holding.payments.integration.asaas.dto.AsaasSubscriptionResponse;
import br.com.holding.payments.webhook.WebhookEvent;
import br.com.holding.payments.webhook.WebhookEventRepository;
import br.com.holding.payments.webhook.WebhookEventStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ReconciliationService {

    private final ChargeRepository chargeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AsaasPaymentClient paymentClient;
    private final AsaasSubscriptionClient subscriptionClient;
    private final WebhookEventRepository webhookEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Counter reconciliationDivergenceCounter;
    private final Counter dlqReplayCounter;

    public ReconciliationService(ChargeRepository chargeRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  AsaasPaymentClient paymentClient,
                                  AsaasSubscriptionClient subscriptionClient,
                                  WebhookEventRepository webhookEventRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  MeterRegistry meterRegistry) {
        this.chargeRepository = chargeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentClient = paymentClient;
        this.subscriptionClient = subscriptionClient;
        this.webhookEventRepository = webhookEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.reconciliationDivergenceCounter = Counter.builder("reconciliation_divergences_total")
                .description("Total de divergencias encontradas na reconciliacao")
                .register(meterRegistry);
        this.dlqReplayCounter = Counter.builder("reconciliation_dlq_replay_total")
                .description("Total de eventos DLQ reprocessados")
                .register(meterRegistry);
    }

    /**
     * Reconciles charges created since the given date by comparing
     * local status with Asaas status. Auto-fixes when possible.
     */
    @Transactional
    public ReconciliationResult reconcileChargesSince(Long companyId, LocalDate since) {
        log.info("Starting charge reconciliation for company={} since={}", companyId, since);

        List<Divergence> divergences = new ArrayList<>();
        int totalChecked = 0;
        int autoFixed = 0;

        Page<Charge> charges = chargeRepository.findWithFilters(
                null, null, since, null, null, PageRequest.of(0, 500));

        for (Charge charge : charges.getContent()) {
            if (charge.getAsaasId() == null) continue;
            totalChecked++;

            try {
                AsaasPaymentResponse asaasPayment = paymentClient.getById(companyId, charge.getAsaasId());
                ChargeStatus asaasStatus = mapAsaasPaymentStatus(asaasPayment.status());

                if (asaasStatus != null && charge.getStatus() != asaasStatus) {
                    String action;
                    if (charge.getStatus().canTransitionTo(asaasStatus)) {
                        charge.transitionTo(asaasStatus);
                        chargeRepository.save(charge);
                        autoFixed++;
                        action = "AUTO_FIXED";
                        log.info("Charge reconciled: id={}, asaasId={}, {} -> {}",
                                charge.getId(), charge.getAsaasId(), charge.getStatus(), asaasStatus);
                    } else {
                        action = "MANUAL_REVIEW_NEEDED";
                        log.warn("Charge divergence (invalid transition): id={}, asaasId={}, local={}, asaas={}",
                                charge.getId(), charge.getAsaasId(), charge.getStatus(), asaasStatus);
                    }

                    divergences.add(new Divergence(
                            "Charge", charge.getId(), charge.getAsaasId(),
                            charge.getStatus().name(), asaasStatus.name(), action));
                    reconciliationDivergenceCounter.increment();
                }
            } catch (Exception e) {
                log.warn("Failed to reconcile charge id={}, asaasId={}: {}",
                        charge.getId(), charge.getAsaasId(), e.getMessage());
                divergences.add(new Divergence(
                        "Charge", charge.getId(), charge.getAsaasId(),
                        charge.getStatus().name(), "ERROR", "FETCH_FAILED: " + e.getMessage()));
            }
        }

        log.info("Charge reconciliation complete: checked={}, divergences={}, autoFixed={}",
                totalChecked, divergences.size(), autoFixed);

        return new ReconciliationResult(LocalDateTime.now(), totalChecked, divergences.size(), autoFixed, divergences);
    }

    /**
     * Reconciles active subscriptions by comparing local status with Asaas.
     */
    @Transactional
    public ReconciliationResult reconcileSubscriptions(Long companyId) {
        log.info("Starting subscription reconciliation for company={}", companyId);

        List<Divergence> divergences = new ArrayList<>();
        int totalChecked = 0;
        int autoFixed = 0;

        Page<Subscription> subscriptions = subscriptionRepository.findWithFilters(
                null, null, PageRequest.of(0, 500));

        for (Subscription sub : subscriptions.getContent()) {
            if (sub.getAsaasId() == null) continue;
            totalChecked++;

            try {
                AsaasSubscriptionResponse asaasSub = subscriptionClient.getById(companyId, sub.getAsaasId());
                SubscriptionStatus asaasStatus = mapAsaasSubscriptionStatus(asaasSub.status());

                if (asaasStatus != null && sub.getStatus() != asaasStatus) {
                    String action;
                    if (sub.getStatus().canTransitionTo(asaasStatus)) {
                        sub.transitionTo(asaasStatus);
                        subscriptionRepository.save(sub);
                        autoFixed++;
                        action = "AUTO_FIXED";
                        log.info("Subscription reconciled: id={}, asaasId={}, {} -> {}",
                                sub.getId(), sub.getAsaasId(), sub.getStatus(), asaasStatus);
                    } else {
                        action = "MANUAL_REVIEW_NEEDED";
                        log.warn("Subscription divergence (invalid transition): id={}, asaasId={}, local={}, asaas={}",
                                sub.getId(), sub.getAsaasId(), sub.getStatus(), asaasStatus);
                    }

                    divergences.add(new Divergence(
                            "Subscription", sub.getId(), sub.getAsaasId(),
                            sub.getStatus().name(), asaasStatus.name(), action));
                    reconciliationDivergenceCounter.increment();
                }
            } catch (Exception e) {
                log.warn("Failed to reconcile subscription id={}, asaasId={}: {}",
                        sub.getId(), sub.getAsaasId(), e.getMessage());
                divergences.add(new Divergence(
                        "Subscription", sub.getId(), sub.getAsaasId(),
                        sub.getStatus().name(), "ERROR", "FETCH_FAILED: " + e.getMessage()));
            }
        }

        log.info("Subscription reconciliation complete: checked={}, divergences={}, autoFixed={}",
                totalChecked, divergences.size(), autoFixed);

        return new ReconciliationResult(LocalDateTime.now(), totalChecked, divergences.size(), autoFixed, divergences);
    }

    /**
     * Replays DLQ events (webhook + outbox) for the given company by marking them as PENDING.
     */
    @Transactional
    public DlqReplayResult replayDLQ(Long companyId) {
        log.info("Starting DLQ replay for company={}", companyId);

        long webhookReplayed = 0;
        Page<WebhookEvent> webhookDlq = webhookEventRepository.findByStatusAndCompanyId(
                WebhookEventStatus.DLQ, companyId, PageRequest.of(0, 200));
        for (WebhookEvent event : webhookDlq.getContent()) {
            event.markReadyForRetry();
            webhookEventRepository.save(event);
            webhookReplayed++;
            dlqReplayCounter.increment();
        }

        long outboxReplayed = 0;
        Page<OutboxEvent> outboxDlq = outboxEventRepository.findByStatusAndCompanyId(
                OutboxStatus.DLQ, companyId, PageRequest.of(0, 200));
        for (OutboxEvent event : outboxDlq.getContent()) {
            event.setStatus(OutboxStatus.PENDING);
            event.setAttemptCount(0);
            event.setLastError(null);
            outboxEventRepository.save(event);
            outboxReplayed++;
            dlqReplayCounter.increment();
        }

        log.info("DLQ replay complete for company={}: webhook={}, outbox={}",
                companyId, webhookReplayed, outboxReplayed);

        return new DlqReplayResult(LocalDateTime.now(), webhookReplayed, outboxReplayed,
                webhookReplayed + outboxReplayed);
    }

    private ChargeStatus mapAsaasPaymentStatus(String asaasStatus) {
        if (asaasStatus == null) return null;
        return switch (asaasStatus.toUpperCase()) {
            case "PENDING" -> ChargeStatus.PENDING;
            case "CONFIRMED" -> ChargeStatus.CONFIRMED;
            case "RECEIVED" -> ChargeStatus.RECEIVED;
            case "OVERDUE" -> ChargeStatus.OVERDUE;
            case "REFUNDED" -> ChargeStatus.REFUNDED;
            case "RECEIVED_IN_CASH" -> ChargeStatus.RECEIVED;
            case "REFUND_REQUESTED", "REFUND_IN_PROGRESS" -> ChargeStatus.REFUNDED;
            case "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "AWAITING_CHARGEBACK_REVERSAL" -> ChargeStatus.CHARGEBACK;
            case "DUNNING_REQUESTED", "DUNNING_RECEIVED" -> ChargeStatus.OVERDUE;
            case "AWAITING_RISK_ANALYSIS" -> ChargeStatus.PENDING;
            default -> {
                log.warn("Unknown Asaas payment status: {}", asaasStatus);
                yield null;
            }
        };
    }

    private SubscriptionStatus mapAsaasSubscriptionStatus(String asaasStatus) {
        if (asaasStatus == null) return null;
        return switch (asaasStatus.toUpperCase()) {
            case "ACTIVE" -> SubscriptionStatus.ACTIVE;
            case "EXPIRED" -> SubscriptionStatus.EXPIRED;
            default -> {
                log.warn("Unknown Asaas subscription status: {}", asaasStatus);
                yield null;
            }
        };
    }
}
