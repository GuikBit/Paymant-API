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
    public boolean handle(WebhookEvent event) {
        AsaasEventType eventType;
        try {
            eventType = AsaasEventType.valueOf(event.getEventType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown event type: {}, marking as processed", event.getEventType());
            return true;
        }

        if (eventType.isPaymentEvent()) {
            return handlePaymentEvent(event, eventType);
        }

        if (eventType.isSubscriptionEvent()) {
            return handleSubscriptionEvent(event, eventType);
        }

        log.warn("Unhandled event type: {}", event.getEventType());
        return true;
    }

    private boolean handlePaymentEvent(WebhookEvent event, AsaasEventType eventType) {
        AsaasWebhookPayload payload = parsePayload(event);
        if (payload == null || payload.payment() == null) {
            log.error("Invalid payment payload for event: {}", event.getAsaasEventId());
            throw new RuntimeException("Invalid payment payload: missing payment object");
        }

        String asaasPaymentId = payload.payment().id();
        Optional<Charge> chargeOpt = chargeRepository.findByAsaasId(asaasPaymentId);

        if (chargeOpt.isEmpty()) {
            log.info("Charge not found for asaasId={}, event will be deferred", asaasPaymentId);
            return false; // Resource not found -> defer
        }

        Charge charge = chargeOpt.get();

        // Events that don't trigger state transition (informational only)
        if (!eventType.triggersChargeTransition()) {
            log.info("Informational event {} for charge asaasId={}, no state change",
                    eventType, asaasPaymentId);
            publishOutboxEvent(event, eventType, charge);
            return true;
        }

        // Transition the charge state
        ChargeStatus targetStatus = eventType.toChargeStatus();
        charge.transitionTo(targetStatus);
        chargeRepository.save(charge);

        publishOutboxEvent(event, eventType, charge);

        // If payment received, check if it's linked to a plan change
        if (eventType == AsaasEventType.PAYMENT_RECEIVED && charge.getId() != null) {
            try {
                planChangeService.confirmAfterPayment(charge.getId());
            } catch (Exception e) {
                log.warn("Plan change confirmation check failed for chargeId={}: {}", charge.getId(), e.getMessage());
            }
        }

        log.info("Charge state updated: asaasId={}, newStatus={}, event={}",
                asaasPaymentId, targetStatus, eventType);
        return true;
    }

    private boolean handleSubscriptionEvent(WebhookEvent event, AsaasEventType eventType) {
        AsaasWebhookPayload payload = parsePayload(event);
        if (payload == null || payload.subscription() == null) {
            log.error("Invalid subscription payload for event: {}", event.getAsaasEventId());
            throw new RuntimeException("Invalid subscription payload: missing subscription object");
        }

        String asaasSubscriptionId = payload.subscription().id();
        Subscription subscription = subscriptionService.findByAsaasId(asaasSubscriptionId);

        if (subscription == null) {
            log.info("Subscription not found for asaasId={}, event will be deferred", asaasSubscriptionId);
            return false;
        }

        switch (eventType) {
            case SUBSCRIPTION_UPDATED -> {
                log.info("Subscription updated via webhook: asaasId={}", asaasSubscriptionId);
                publishSubscriptionOutboxEvent(event, "SubscriptionUpdatedEvent", subscription);
            }
            case SUBSCRIPTION_DELETED -> {
                if (subscription.getStatus().canTransitionTo(SubscriptionStatus.CANCELED)) {
                    subscription.transitionTo(SubscriptionStatus.CANCELED);
                    publishSubscriptionOutboxEvent(event, "SubscriptionCanceledEvent", subscription);
                    log.info("Subscription canceled via webhook: asaasId={}", asaasSubscriptionId);
                } else {
                    log.info("Subscription already in terminal state {}, ignoring DELETED event",
                            subscription.getStatus());
                }
            }
            default -> log.info("Subscription event {} received, no action needed", eventType);
        }

        return true;
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
