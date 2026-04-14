package br.com.holding.payments.phase6;

import br.com.holding.payments.charge.*;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionService;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.webhook.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 6 - WebhookEventHandler")
class WebhookEventHandlerTest {

    @Mock private ChargeRepository chargeRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private OutboxPublisher outboxPublisher;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WebhookEventHandler handler;

    private Company company;
    private Customer customer;

    @BeforeEach
    void setup() {
        company = Company.builder().cnpj("11111111000111").razaoSocial("Test").build();
        company.setId(1L);
        customer = Customer.builder().company(company).name("Test").document("12345678901").build();
        customer.setId(10L);
    }

    private WebhookEvent buildEvent(String eventType, String paymentPayload) {
        return WebhookEvent.builder()
                .id(1L)
                .company(company)
                .asaasEventId("evt_123")
                .eventType(eventType)
                .payload(paymentPayload)
                .build();
    }

    private String paymentPayload(String paymentId) {
        return """
                {"event":"PAYMENT_RECEIVED","payment":{"id":"%s","customer":"cus_123","billingType":"PIX","value":100.00,"dueDate":"2026-04-15","status":"RECEIVED"}}
                """.formatted(paymentId);
    }

    private String subscriptionPayload(String subscriptionId, String eventType) {
        return """
                {"event":"%s","subscription":{"id":"%s","customer":"cus_123","billingType":"CREDIT_CARD","value":99.90,"nextDueDate":"2026-05-01","cycle":"MONTHLY","status":"ACTIVE","deleted":false}}
                """.formatted(eventType, subscriptionId);
    }

    @Nested
    @DisplayName("Eventos de Payment")
    class PaymentEvents {

        @Test
        @DisplayName("PAYMENT_RECEIVED com charge existente -> transiciona para RECEIVED e publica outbox")
        void paymentReceived_withExistingCharge_shouldTransition() {
            Charge charge = Charge.builder()
                    .company(company).customer(customer).asaasId("pay_001")
                    .billingType(BillingType.PIX).value(BigDecimal.valueOf(100))
                    .dueDate(LocalDate.now()).status(ChargeStatus.PENDING).build();
            charge.setId(1L);

            when(chargeRepository.findByAsaasId("pay_001")).thenReturn(Optional.of(charge));
            when(chargeRepository.save(any())).thenReturn(charge);

            WebhookEvent event = buildEvent("PAYMENT_RECEIVED", paymentPayload("pay_001"));
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.RECEIVED);
            verify(outboxPublisher).publish(eq(1L), eq("ChargePaidEvent"), eq("Charge"), eq("1"), anyString());
        }

        @Test
        @DisplayName("PAYMENT_RECEIVED sem charge local -> retorna false (defer)")
        void paymentReceived_chargeNotFound_shouldDefer() {
            when(chargeRepository.findByAsaasId("pay_missing")).thenReturn(Optional.empty());

            WebhookEvent event = buildEvent("PAYMENT_RECEIVED", paymentPayload("pay_missing"));
            var result = handler.handle(event);

            assertThat(result.processed()).isFalse();
            verify(chargeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Transicao invalida (CANCELED -> RECEIVED) lanca IllegalStateTransitionException")
        void invalidTransition_shouldThrow() {
            Charge charge = Charge.builder()
                    .company(company).customer(customer).asaasId("pay_002")
                    .billingType(BillingType.PIX).value(BigDecimal.valueOf(100))
                    .dueDate(LocalDate.now()).status(ChargeStatus.CANCELED).build();
            charge.setId(2L);

            when(chargeRepository.findByAsaasId("pay_002")).thenReturn(Optional.of(charge));

            WebhookEvent event = buildEvent("PAYMENT_RECEIVED", paymentPayload("pay_002"));

            assertThatThrownBy(() -> handler.handle(event))
                    .isInstanceOf(IllegalStateTransitionException.class);

            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELED);
        }

        @Test
        @DisplayName("PAYMENT_CONFIRMED transiciona para CONFIRMED")
        void paymentConfirmed_shouldTransitionToConfirmed() {
            Charge charge = Charge.builder()
                    .company(company).customer(customer).asaasId("pay_003")
                    .billingType(BillingType.CREDIT_CARD).value(BigDecimal.valueOf(50))
                    .dueDate(LocalDate.now()).status(ChargeStatus.PENDING).build();
            charge.setId(3L);

            when(chargeRepository.findByAsaasId("pay_003")).thenReturn(Optional.of(charge));
            when(chargeRepository.save(any())).thenReturn(charge);

            String payload = """
                    {"event":"PAYMENT_CONFIRMED","payment":{"id":"pay_003","customer":"cus_123","billingType":"CREDIT_CARD","value":50.00,"status":"CONFIRMED"}}
                    """;
            WebhookEvent event = buildEvent("PAYMENT_CONFIRMED", payload);
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
            verify(outboxPublisher).publish(eq(1L), eq("ChargeConfirmedEvent"), eq("Charge"), eq("3"), anyString());
        }

        @Test
        @DisplayName("PAYMENT_OVERDUE transiciona para OVERDUE")
        void paymentOverdue_shouldTransitionToOverdue() {
            Charge charge = Charge.builder()
                    .company(company).customer(customer).asaasId("pay_004")
                    .billingType(BillingType.BOLETO).value(BigDecimal.valueOf(200))
                    .dueDate(LocalDate.now().minusDays(1)).status(ChargeStatus.PENDING).build();
            charge.setId(4L);

            when(chargeRepository.findByAsaasId("pay_004")).thenReturn(Optional.of(charge));
            when(chargeRepository.save(any())).thenReturn(charge);

            String payload = """
                    {"event":"PAYMENT_OVERDUE","payment":{"id":"pay_004","customer":"cus_123","billingType":"BOLETO","value":200.00,"status":"OVERDUE"}}
                    """;
            WebhookEvent event = buildEvent("PAYMENT_OVERDUE", payload);
            handler.handle(event);

            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.OVERDUE);
            verify(outboxPublisher).publish(eq(1L), eq("ChargeOverdueEvent"), eq("Charge"), eq("4"), anyString());
        }

        @Test
        @DisplayName("PAYMENT_UPDATED (informacional) nao altera status")
        void paymentUpdated_shouldNotChangeStatus() {
            Charge charge = Charge.builder()
                    .company(company).customer(customer).asaasId("pay_005")
                    .billingType(BillingType.PIX).value(BigDecimal.valueOf(100))
                    .dueDate(LocalDate.now()).status(ChargeStatus.PENDING).build();
            charge.setId(5L);

            when(chargeRepository.findByAsaasId("pay_005")).thenReturn(Optional.of(charge));

            String payload = """
                    {"event":"PAYMENT_UPDATED","payment":{"id":"pay_005","customer":"cus_123","billingType":"PIX","value":100.00,"status":"PENDING"}}
                    """;
            WebhookEvent event = buildEvent("PAYMENT_UPDATED", payload);
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
            verify(chargeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tipo de evento desconhecido eh marcado como processado")
        void unknownEventType_shouldBeProcessed() {
            WebhookEvent event = buildEvent("SOME_FUTURE_EVENT", "{}");
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Eventos de Subscription")
    class SubscriptionEvents {

        @Test
        @DisplayName("SUBSCRIPTION_DELETED cancela assinatura ativa")
        void subscriptionDeleted_shouldCancel() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.ACTIVE)
                    .asaasId("sub_001").build();
            subscription.setId(10L);

            when(subscriptionService.findByAsaasId("sub_001")).thenReturn(subscription);

            WebhookEvent event = buildEvent("SUBSCRIPTION_DELETED",
                    subscriptionPayload("sub_001", "SUBSCRIPTION_DELETED"));
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            verify(outboxPublisher).publish(eq(1L), eq("SubscriptionCanceledEvent"),
                    eq("Subscription"), eq("10"), anyString());
        }

        @Test
        @DisplayName("SUBSCRIPTION_DELETED ignora assinatura ja cancelada")
        void subscriptionDeleted_alreadyCanceled_shouldIgnore() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.CANCELED)
                    .asaasId("sub_002").build();
            subscription.setId(11L);

            when(subscriptionService.findByAsaasId("sub_002")).thenReturn(subscription);

            WebhookEvent event = buildEvent("SUBSCRIPTION_DELETED",
                    subscriptionPayload("sub_002", "SUBSCRIPTION_DELETED"));
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            verify(outboxPublisher, never()).publish(anyLong(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("SUBSCRIPTION_UPDATED publica evento outbox sem alterar estado")
        void subscriptionUpdated_shouldPublishOutbox() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.ACTIVE)
                    .asaasId("sub_003").build();
            subscription.setId(12L);

            when(subscriptionService.findByAsaasId("sub_003")).thenReturn(subscription);

            WebhookEvent event = buildEvent("SUBSCRIPTION_UPDATED",
                    subscriptionPayload("sub_003", "SUBSCRIPTION_UPDATED"));
            var result = handler.handle(event);

            assertThat(result.processed()).isTrue();
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(outboxPublisher).publish(eq(1L), eq("SubscriptionUpdatedEvent"),
                    eq("Subscription"), eq("12"), anyString());
        }

        @Test
        @DisplayName("Evento de subscription sem assinatura local -> defer")
        void subscriptionEvent_notFound_shouldDefer() {
            when(subscriptionService.findByAsaasId("sub_missing")).thenReturn(null);

            WebhookEvent event = buildEvent("SUBSCRIPTION_DELETED",
                    subscriptionPayload("sub_missing", "SUBSCRIPTION_DELETED"));
            var result = handler.handle(event);

            assertThat(result.processed()).isFalse();
        }
    }
}
