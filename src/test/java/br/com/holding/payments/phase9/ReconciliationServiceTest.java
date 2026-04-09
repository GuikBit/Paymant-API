package br.com.holding.payments.phase9;

import br.com.holding.payments.charge.Charge;
import br.com.holding.payments.charge.ChargeRepository;
import br.com.holding.payments.charge.ChargeStatus;
import br.com.holding.payments.integration.asaas.client.AsaasApiException;
import br.com.holding.payments.integration.asaas.client.AsaasPaymentClient;
import br.com.holding.payments.integration.asaas.client.AsaasSubscriptionClient;
import br.com.holding.payments.integration.asaas.dto.AsaasPaymentResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasSubscriptionResponse;
import br.com.holding.payments.outbox.OutboxEvent;
import br.com.holding.payments.outbox.OutboxEventRepository;
import br.com.holding.payments.outbox.OutboxStatus;
import br.com.holding.payments.reconciliation.ReconciliationService;
import br.com.holding.payments.reconciliation.dto.DlqReplayResult;
import br.com.holding.payments.reconciliation.dto.ReconciliationResult;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.webhook.WebhookEvent;
import br.com.holding.payments.webhook.WebhookEventRepository;
import br.com.holding.payments.webhook.WebhookEventStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 9 - ReconciliationService")
class ReconciliationServiceTest {

    @Mock private ChargeRepository chargeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private AsaasPaymentClient paymentClient;
    @Mock private AsaasSubscriptionClient subscriptionClient;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(
                chargeRepository, subscriptionRepository,
                paymentClient, subscriptionClient,
                webhookEventRepository, outboxEventRepository,
                new SimpleMeterRegistry());
    }

    private AsaasPaymentResponse asaasPayment(String id, String status) {
        return new AsaasPaymentResponse(id, "cus_1", "PIX", new BigDecimal("100.00"),
                new BigDecimal("97.00"), status, "2026-04-10", null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private AsaasSubscriptionResponse asaasSubscription(String id, String status) {
        return new AsaasSubscriptionResponse(id, "cus_1", "PIX", new BigDecimal("100.00"),
                "2026-05-01", "MONTHLY", null, null, status, false, null);
    }

    // ==================== RECONCILE CHARGES ====================

    @Nested
    @DisplayName("reconcileChargesSince()")
    class ReconcileChargesTests {

        @Test
        @DisplayName("Sem charges - retorna resultado vazio")
        void noCharges_shouldReturnEmptyResult() {
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.totalChecked()).isZero();
            assertThat(result.divergencesFound()).isZero();
            assertThat(result.autoFixed()).isZero();
            assertThat(result.divergences()).isEmpty();
        }

        @Test
        @DisplayName("Charge sem asaasId - ignorada")
        void chargeWithoutAsaasId_shouldBeSkipped() {
            Charge charge = Charge.builder().id(1L).status(ChargeStatus.PENDING).build();
            // asaasId is null by default

            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.totalChecked()).isZero();
            verifyNoInteractions(paymentClient);
        }

        @Test
        @DisplayName("Status iguais - sem divergencia")
        void sameStatus_shouldNotCreateDivergence() {
            Charge charge = Charge.builder().id(1L).asaasId("pay_1").status(ChargeStatus.PENDING).build();
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));
            when(paymentClient.getById(1L, "pay_1")).thenReturn(asaasPayment("pay_1", "PENDING"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.totalChecked()).isEqualTo(1);
            assertThat(result.divergencesFound()).isZero();
            verify(chargeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Divergencia com transicao valida - auto-fix")
        void validTransitionDivergence_shouldAutoFix() {
            Charge charge = Charge.builder().id(1L).asaasId("pay_1").status(ChargeStatus.PENDING).build();
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));
            when(paymentClient.getById(1L, "pay_1")).thenReturn(asaasPayment("pay_1", "CONFIRMED"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.totalChecked()).isEqualTo(1);
            assertThat(result.divergencesFound()).isEqualTo(1);
            assertThat(result.autoFixed()).isEqualTo(1);
            assertThat(result.divergences().get(0).action()).isEqualTo("AUTO_FIXED");
            verify(chargeRepository).save(charge);
        }

        @Test
        @DisplayName("Divergencia com transicao invalida - manual review")
        void invalidTransitionDivergence_shouldMarkManualReview() {
            // CANCELED -> CONFIRMED is not a valid transition
            Charge charge = Charge.builder().id(2L).asaasId("pay_2").status(ChargeStatus.CANCELED).build();
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));
            when(paymentClient.getById(1L, "pay_2")).thenReturn(asaasPayment("pay_2", "CONFIRMED"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.divergencesFound()).isEqualTo(1);
            assertThat(result.autoFixed()).isZero();
            assertThat(result.divergences().get(0).action()).isEqualTo("MANUAL_REVIEW_NEEDED");
            verify(chargeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Erro ao buscar no Asaas - registra como FETCH_FAILED")
        void asaasError_shouldRegisterAsFetchFailed() {
            Charge charge = Charge.builder().id(3L).asaasId("pay_3").status(ChargeStatus.PENDING).build();
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));
            when(paymentClient.getById(1L, "pay_3"))
                    .thenThrow(new AsaasApiException(503, "Asaas indisponivel"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.totalChecked()).isEqualTo(1);
            assertThat(result.divergencesFound()).isEqualTo(1);
            assertThat(result.autoFixed()).isZero();
            assertThat(result.divergences().get(0).action()).startsWith("FETCH_FAILED");
            assertThat(result.divergences().get(0).asaasStatus()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Multiplas charges - processa todas")
        void multipleCharges_shouldProcessAll() {
            Charge charge1 = Charge.builder().id(1L).asaasId("pay_1").status(ChargeStatus.PENDING).build();
            Charge charge2 = Charge.builder().id(2L).asaasId("pay_2").status(ChargeStatus.PENDING).build();
            Charge charge3 = Charge.builder().id(3L).asaasId("pay_3").status(ChargeStatus.PENDING).build();

            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge1, charge2, charge3)));
            when(paymentClient.getById(eq(1L), eq("pay_1"))).thenReturn(asaasPayment("pay_1", "PENDING"));
            when(paymentClient.getById(eq(1L), eq("pay_2"))).thenReturn(asaasPayment("pay_2", "RECEIVED"));
            when(paymentClient.getById(eq(1L), eq("pay_3"))).thenReturn(asaasPayment("pay_3", "OVERDUE"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.totalChecked()).isEqualTo(3);
            assertThat(result.divergencesFound()).isEqualTo(2); // pay_2 and pay_3 diverge
            assertThat(result.autoFixed()).isEqualTo(2); // both PENDING->RECEIVED and PENDING->OVERDUE are valid
        }

        @Test
        @DisplayName("Status Asaas desconhecido - sem divergencia")
        void unknownAsaasStatus_shouldNotCreateDivergence() {
            Charge charge = Charge.builder().id(1L).asaasId("pay_1").status(ChargeStatus.PENDING).build();
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));
            when(paymentClient.getById(1L, "pay_1")).thenReturn(asaasPayment("pay_1", "SOME_NEW_STATUS"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.divergencesFound()).isZero();
        }

        @Test
        @DisplayName("Mapeamento RECEIVED_IN_CASH -> RECEIVED")
        void receivedInCash_shouldMapToReceived() {
            Charge charge = Charge.builder().id(1L).asaasId("pay_1").status(ChargeStatus.PENDING).build();
            when(chargeRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(charge)));
            when(paymentClient.getById(1L, "pay_1")).thenReturn(asaasPayment("pay_1", "RECEIVED_IN_CASH"));

            ReconciliationResult result = service.reconcileChargesSince(1L, LocalDate.now().minusDays(3));

            assertThat(result.divergencesFound()).isEqualTo(1);
            assertThat(result.divergences().get(0).asaasStatus()).isEqualTo("RECEIVED");
        }
    }

    // ==================== RECONCILE SUBSCRIPTIONS ====================

    @Nested
    @DisplayName("reconcileSubscriptions()")
    class ReconcileSubscriptionsTests {

        @Test
        @DisplayName("Sem subscriptions - resultado vazio")
        void noSubscriptions_shouldReturnEmpty() {
            when(subscriptionRepository.findWithFilters(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ReconciliationResult result = service.reconcileSubscriptions(1L);

            assertThat(result.totalChecked()).isZero();
            assertThat(result.divergences()).isEmpty();
        }

        @Test
        @DisplayName("Subscription sem asaasId - ignorada")
        void subscriptionWithoutAsaasId_shouldBeSkipped() {
            Subscription sub = Subscription.builder().id(1L).status(SubscriptionStatus.ACTIVE).build();

            when(subscriptionRepository.findWithFilters(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(sub)));

            ReconciliationResult result = service.reconcileSubscriptions(1L);

            assertThat(result.totalChecked()).isZero();
            verifyNoInteractions(subscriptionClient);
        }

        @Test
        @DisplayName("Status iguais - sem divergencia")
        void sameStatus_shouldNotDiverge() {
            Subscription sub = Subscription.builder().id(1L).asaasId("sub_1")
                    .status(SubscriptionStatus.ACTIVE).build();
            when(subscriptionRepository.findWithFilters(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(sub)));
            when(subscriptionClient.getById(1L, "sub_1")).thenReturn(asaasSubscription("sub_1", "ACTIVE"));

            ReconciliationResult result = service.reconcileSubscriptions(1L);

            assertThat(result.totalChecked()).isEqualTo(1);
            assertThat(result.divergencesFound()).isZero();
        }

        @Test
        @DisplayName("ACTIVE no local, EXPIRED no Asaas - auto-fix")
        void activeToExpired_shouldAutoFix() {
            Subscription sub = Subscription.builder().id(1L).asaasId("sub_1")
                    .status(SubscriptionStatus.ACTIVE).build();
            when(subscriptionRepository.findWithFilters(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(sub)));
            when(subscriptionClient.getById(1L, "sub_1")).thenReturn(asaasSubscription("sub_1", "EXPIRED"));

            ReconciliationResult result = service.reconcileSubscriptions(1L);

            assertThat(result.divergencesFound()).isEqualTo(1);
            assertThat(result.autoFixed()).isEqualTo(1);
            assertThat(result.divergences().get(0).action()).isEqualTo("AUTO_FIXED");
            verify(subscriptionRepository).save(sub);
        }

        @Test
        @DisplayName("Erro ao buscar subscription no Asaas - FETCH_FAILED")
        void asaasError_shouldFetchFailed() {
            Subscription sub = Subscription.builder().id(1L).asaasId("sub_1")
                    .status(SubscriptionStatus.ACTIVE).build();
            when(subscriptionRepository.findWithFilters(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(sub)));
            when(subscriptionClient.getById(1L, "sub_1"))
                    .thenThrow(new AsaasApiException(503, "timeout"));

            ReconciliationResult result = service.reconcileSubscriptions(1L);

            assertThat(result.divergencesFound()).isEqualTo(1);
            assertThat(result.divergences().get(0).action()).startsWith("FETCH_FAILED");
        }
    }

    // ==================== REPLAY DLQ ====================

    @Nested
    @DisplayName("replayDLQ()")
    class ReplayDlqTests {

        @Test
        @DisplayName("Replay de webhook DLQ - marca como PENDING")
        void replayWebhookDlq_shouldMarkPending() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(1L).status(WebhookEventStatus.DLQ).attemptCount(10).build();
            when(webhookEventRepository.findByStatus(eq(WebhookEventStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(event)));
            when(outboxEventRepository.findByStatus(eq(OutboxStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            DlqReplayResult result = service.replayDLQ();

            assertThat(result.webhookEventsReplayed()).isEqualTo(1);
            assertThat(result.outboxEventsReplayed()).isZero();
            assertThat(result.totalReplayed()).isEqualTo(1);
            assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PENDING);
            verify(webhookEventRepository).save(event);
        }

        @Test
        @DisplayName("Replay de outbox DLQ - reseta status e attemptCount")
        void replayOutboxDlq_shouldResetStatusAndAttempts() {
            OutboxEvent event = OutboxEvent.builder()
                    .id(1L).status(OutboxStatus.DLQ).attemptCount(5).lastError("timeout").build();
            when(webhookEventRepository.findByStatus(eq(WebhookEventStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(outboxEventRepository.findByStatus(eq(OutboxStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(event)));

            DlqReplayResult result = service.replayDLQ();

            assertThat(result.webhookEventsReplayed()).isZero();
            assertThat(result.outboxEventsReplayed()).isEqualTo(1);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttemptCount()).isZero();
            assertThat(event.getLastError()).isNull();
            verify(outboxEventRepository).save(event);
        }

        @Test
        @DisplayName("Replay de ambos webhook e outbox DLQ")
        void replayBothDlq_shouldProcessAll() {
            WebhookEvent we1 = WebhookEvent.builder().id(1L).status(WebhookEventStatus.DLQ).build();
            WebhookEvent we2 = WebhookEvent.builder().id(2L).status(WebhookEventStatus.DLQ).build();
            OutboxEvent oe1 = OutboxEvent.builder().id(10L).status(OutboxStatus.DLQ).attemptCount(3).build();

            when(webhookEventRepository.findByStatus(eq(WebhookEventStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(we1, we2)));
            when(outboxEventRepository.findByStatus(eq(OutboxStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(oe1)));

            DlqReplayResult result = service.replayDLQ();

            assertThat(result.webhookEventsReplayed()).isEqualTo(2);
            assertThat(result.outboxEventsReplayed()).isEqualTo(1);
            assertThat(result.totalReplayed()).isEqualTo(3);
        }

        @Test
        @DisplayName("Nenhum evento em DLQ - resultado zerado")
        void noDlqEvents_shouldReturnZeroes() {
            when(webhookEventRepository.findByStatus(eq(WebhookEventStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(outboxEventRepository.findByStatus(eq(OutboxStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            DlqReplayResult result = service.replayDLQ();

            assertThat(result.totalReplayed()).isZero();
            verify(webhookEventRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Resultado contem timestamp")
        void result_shouldContainTimestamp() {
            when(webhookEventRepository.findByStatus(eq(WebhookEventStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(outboxEventRepository.findByStatus(eq(OutboxStatus.DLQ), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            DlqReplayResult result = service.replayDLQ();

            assertThat(result.executedAt()).isNotNull();
        }
    }
}
