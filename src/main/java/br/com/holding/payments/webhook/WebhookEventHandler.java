package br.com.holding.payments.webhook;

import br.com.holding.payments.charge.Charge;
import br.com.holding.payments.charge.ChargeRepository;
import br.com.holding.payments.charge.ChargeStatus;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.planchange.PlanChangeService;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionService;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.webhook.dto.AsaasWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventHandler {

    private final ChargeRepository chargeRepository;
    private final SubscriptionService subscriptionService;
    private final PlanChangeService planChangeService;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Processes a webhook event. Returns true if processed, false if resource not found (defer).
     *
     * @throws IllegalStateTransitionException if the state transition is invalid (mark as FAILED)
     */
    public HandleResult handle(WebhookEvent event) {
        AsaasEventType eventType;
        try {
            eventType = AsaasEventType.valueOf(event.getEventType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown event type: {}, marking as processed", event.getEventType());
            return HandleResult.processed("Tipo de evento desconhecido (" + event.getEventType() + "), ignorado");
        }

        if (eventType.isPaymentEvent()) {
            return handlePaymentEvent(event, eventType);
        }

        if (eventType.isSubscriptionEvent()) {
            return handleSubscriptionEvent(event, eventType);
        }

        log.warn("Unhandled event type: {}", event.getEventType());
        return HandleResult.processed("Tipo de evento sem handler, ignorado");
    }

    private HandleResult handlePaymentEvent(WebhookEvent event, AsaasEventType eventType) {
        AsaasWebhookPayload payload = parsePayload(event);
        if (payload == null || payload.payment() == null) {
            log.error("Invalid payment payload for event: {}", event.getAsaasEventId());
            throw new RuntimeException("Invalid payment payload: missing payment object");
        }

        String asaasPaymentId = payload.payment().id();
        Optional<Charge> chargeOpt = chargeRepository.findByAsaasId(asaasPaymentId);

        if (chargeOpt.isEmpty()) {
            log.info("Charge not found for asaasId={}, event will be deferred", asaasPaymentId);
            return HandleResult.deferred("Cobranca asaasId=" + asaasPaymentId + " ainda nao encontrada");
        }

        Charge charge = chargeOpt.get();
        event.linkResource("Charge", charge.getId(), asaasPaymentId);

        if (!eventType.triggersChargeTransition()) {
            log.info("Informational event {} for charge asaasId={}, no state change",
                    eventType, asaasPaymentId);
            publishOutboxEvent(event, eventType, charge);
            return HandleResult.processed("Evento informativo " + eventType + " para Charge "
                    + charge.getId() + ", sem mudanca de estado");
        }

        ChargeStatus previousStatus = charge.getStatus();
        ChargeStatus targetStatus = eventType.toChargeStatus();
        charge.transitionTo(targetStatus);
        chargeRepository.save(charge);

        publishOutboxEvent(event, eventType, charge);

        if (eventType == AsaasEventType.PAYMENT_RECEIVED && charge.getId() != null) {
            try {
                planChangeService.confirmAfterPayment(charge.getId());
            } catch (Exception e) {
                log.warn("Plan change confirmation check failed for chargeId={}: {}", charge.getId(), e.getMessage());
            }
        }

        log.info("Charge state updated: asaasId={}, newStatus={}, event={}",
                asaasPaymentId, targetStatus, eventType);
        return HandleResult.processed("Charge " + charge.getId() + ": "
                + previousStatus + " -> " + targetStatus + " (evento " + eventType + ")");
    }

    private HandleResult handleSubscriptionEvent(WebhookEvent event, AsaasEventType eventType) {
        AsaasWebhookPayload payload = parsePayload(event);
        if (payload == null || payload.subscription() == null) {
            log.error("Invalid subscription payload for event: {}", event.getAsaasEventId());
            throw new RuntimeException("Invalid subscription payload: missing subscription object");
        }

        String asaasSubscriptionId = payload.subscription().id();
        Subscription subscription = subscriptionService.findByAsaasId(asaasSubscriptionId);

        if (subscription == null) {
            log.info("Subscription not found for asaasId={}, event will be deferred", asaasSubscriptionId);
            return HandleResult.deferred("Subscription asaasId=" + asaasSubscriptionId + " ainda nao encontrada");
        }

        event.linkResource("Subscription", subscription.getId(), asaasSubscriptionId);

        return switch (eventType) {
            case SUBSCRIPTION_UPDATED -> {
                log.info("Subscription updated via webhook: asaasId={}", asaasSubscriptionId);
                publishSubscriptionOutboxEvent(event, "SubscriptionUpdatedEvent", subscription);
                yield HandleResult.processed("Subscription " + subscription.getId() + " atualizada");
            }
            case SUBSCRIPTION_DELETED -> {
                if (subscription.getStatus().canTransitionTo(SubscriptionStatus.CANCELED)) {
                    SubscriptionStatus previous = subscription.getStatus();
                    subscription.transitionTo(SubscriptionStatus.CANCELED);
                    publishSubscriptionOutboxEvent(event, "SubscriptionCanceledEvent", subscription);
                    log.info("Subscription canceled via webhook: asaasId={}", asaasSubscriptionId);
                    yield HandleResult.processed("Subscription " + subscription.getId()
                            + ": " + previous + " -> CANCELED");
                }
                log.info("Subscription already in terminal state {}, ignoring DELETED event",
                        subscription.getStatus());
                yield HandleResult.processed("Subscription " + subscription.getId()
                        + " ja esta em estado terminal " + subscription.getStatus() + ", evento ignorado");
            }
            default -> {
                log.info("Subscription event {} received, no action needed", eventType);
                yield HandleResult.processed("Evento " + eventType + " recebido, sem acao necessaria");
            }
        };
    }

    public record HandleResult(boolean processed, String summary) {
        public static HandleResult processed(String summary) { return new HandleResult(true, summary); }
        public static HandleResult deferred(String reason) { return new HandleResult(false, reason); }
    }

    private void publishOutboxEvent(WebhookEvent webhookEvent, AsaasEventType eventType, Charge charge) {
        String outboxEventType = mapToChargeOutboxEventType(eventType);
        if (outboxEventType != null) {
            outboxPublisher.publish(
                    charge.getCompany().getId(),
                    outboxEventType,
                    "Charge",
                    charge.getId().toString(),
                    webhookEvent.getPayload()
            );
        }
    }

    private void publishSubscriptionOutboxEvent(WebhookEvent webhookEvent, String outboxEventType, Subscription subscription) {
        outboxPublisher.publish(
                subscription.getCompany().getId(),
                outboxEventType,
                "Subscription",
                subscription.getId().toString(),
                webhookEvent.getPayload()
        );
    }

    private String mapToChargeOutboxEventType(AsaasEventType eventType) {
        return switch (eventType) {
            case PAYMENT_CREATED -> "ChargeCreatedEvent";
            case PAYMENT_CONFIRMED -> "ChargeConfirmedEvent";
            case PAYMENT_RECEIVED -> "ChargePaidEvent";
            case PAYMENT_OVERDUE -> "ChargeOverdueEvent";
            case PAYMENT_DELETED -> "ChargeCanceledEvent";
            case PAYMENT_REFUNDED -> "ChargeRefundedEvent";
            case PAYMENT_CHARGEBACK_REQUESTED, PAYMENT_CHARGEBACK_DISPUTE -> "ChargeChargebackEvent";
            default -> null;
        };
    }

    private AsaasWebhookPayload parsePayload(WebhookEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), AsaasWebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: eventId={}", event.getId(), e);
            return null;
        }
    }
}
