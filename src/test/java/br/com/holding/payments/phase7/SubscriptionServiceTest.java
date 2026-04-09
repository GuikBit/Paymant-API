package br.com.holding.payments.phase7;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.charge.ChargeMapper;
import br.com.holding.payments.charge.ChargeRepository;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.integration.asaas.gateway.AsaasGatewayService;
import br.com.holding.payments.integration.asaas.gateway.AsaasSubscriptionResult;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanCycle;
import br.com.holding.payments.plan.PlanRepository;
import br.com.holding.payments.subscription.*;
import br.com.holding.payments.subscription.dto.CreateSubscriptionRequest;
import br.com.holding.payments.subscription.dto.SubscriptionResponse;
import br.com.holding.payments.subscription.dto.UpdatePaymentMethodRequest;
import br.com.holding.payments.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 7 - SubscriptionService")
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PlanRepository planRepository;
    @Mock private ChargeRepository chargeRepository;
    @Mock private SubscriptionMapper subscriptionMapper;
    @Mock private ChargeMapper chargeMapper;
    @Mock private AsaasGatewayService asaasGateway;
    @Mock private OutboxPublisher outboxPublisher;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Company company;
    private Customer customer;
    private Plan plan;

    @BeforeEach
    void setup() {
        company = Company.builder().cnpj("11111111000111").razaoSocial("Test LTDA").build();
        company.setId(1L);

        customer = Customer.builder()
                .company(company).name("John Doe")
                .document("12345678901").asaasId("cus_test123").build();
        customer.setId(10L);

        plan = Plan.builder()
                .company(company).name("Plano Pro")
                .value(BigDecimal.valueOf(99.90)).cycle(PlanCycle.MONTHLY)
                .active(true).build();
        plan.setId(20L);

        TenantContext.setCompanyId(1L);
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    private SubscriptionResponse dummyResponse(SubscriptionStatus status) {
        return new SubscriptionResponse(
                1L, 1L, 10L, 20L, "Plano Pro", "sub_test123",
                BillingType.PIX, BigDecimal.valueOf(99.90), "MONTHLY",
                null, null, LocalDate.now().plusDays(1), status,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("Criar assinatura com sucesso - sincroniza com Asaas e publica evento")
        void subscribe_shouldCreateAndPublish() {
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
            when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
            when(asaasGateway.createSubscription(eq(1L), any())).thenReturn(
                    new AsaasSubscriptionResult("sub_test123", "cus_test123", "PIX",
                            BigDecimal.valueOf(99.90), "2026-05-01", "MONTHLY", "ACTIVE"));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });
            when(subscriptionMapper.toResponse(any())).thenReturn(dummyResponse(SubscriptionStatus.ACTIVE));

            CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                    10L, 20L, BillingType.PIX, null, null, null,
                    null, null, null, null);

            SubscriptionResponse result = subscriptionService.subscribe(request);

            assertThat(result).isNotNull();
            verify(outboxPublisher).publish(eq("SubscriptionCreatedEvent"), eq("Subscription"), eq("1"), any());
            verify(asaasGateway).createSubscription(eq(1L), any());
        }

        @Test
        @DisplayName("Cliente sem asaas_id lanca BusinessException")
        void subscribe_customerWithoutAsaasId_shouldThrow() {
            Customer noAsaas = Customer.builder().company(company).name("No Asaas").document("00000000000").build();
            noAsaas.setId(10L);

            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(customerRepository.findById(10L)).thenReturn(Optional.of(noAsaas));

            CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                    10L, 20L, BillingType.PIX, null, null, null,
                    null, null, null, null);

            assertThatThrownBy(() -> subscriptionService.subscribe(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("asaas_id");
        }

        @Test
        @DisplayName("Plano inativo lanca BusinessException")
        void subscribe_inactivePlan_shouldThrow() {
            plan.setActive(false);
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
            when(planRepository.findById(20L)).thenReturn(Optional.of(plan));

            CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                    10L, 20L, BillingType.PIX, null, null, null,
                    null, null, null, null);

            assertThatThrownBy(() -> subscriptionService.subscribe(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("inativo");
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("Cancelar assinatura ACTIVE com sucesso")
        void cancel_active_shouldWork() {
            Subscription subscription = Subscription.builder()
                    .company(company).customer(customer).plan(plan)
                    .asaasId("sub_cancel").billingType(BillingType.PIX)
                    .status(SubscriptionStatus.ACTIVE).build();
            subscription.setId(1L);

            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any())).thenReturn(subscription);
            when(subscriptionMapper.toResponse(any())).thenReturn(dummyResponse(SubscriptionStatus.CANCELED));

            subscriptionService.cancel(1L);

            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            verify(asaasGateway).cancelSubscription(1L, "sub_cancel");
            verify(outboxPublisher).publish(eq("SubscriptionCanceledEvent"), eq("Subscription"), eq("1"), any());
        }

        @Test
        @DisplayName("Cancelar assinatura ja CANCELED lanca excecao")
        void cancel_alreadyCanceled_shouldThrow() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.CANCELED).build();
            subscription.setId(2L);

            when(subscriptionRepository.findById(2L)).thenReturn(Optional.of(subscription));

            assertThatThrownBy(() -> subscriptionService.cancel(2L))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("pause() e resume()")
    class PauseResume {

        @Test
        @DisplayName("Pausar assinatura ACTIVE com sucesso")
        void pause_active_shouldWork() {
            Subscription subscription = Subscription.builder()
                    .company(company).customer(customer).plan(plan)
                    .status(SubscriptionStatus.ACTIVE).build();
            subscription.setId(1L);

            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any())).thenReturn(subscription);
            when(subscriptionMapper.toResponse(any())).thenReturn(dummyResponse(SubscriptionStatus.PAUSED));

            subscriptionService.pause(1L);

            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
            verify(outboxPublisher).publish(eq("SubscriptionPausedEvent"), eq("Subscription"), eq("1"), any());
        }

        @Test
        @DisplayName("Retomar assinatura PAUSED com sucesso")
        void resume_paused_shouldWork() {
            Subscription subscription = Subscription.builder()
                    .company(company).customer(customer).plan(plan)
                    .status(SubscriptionStatus.PAUSED).build();
            subscription.setId(1L);

            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any())).thenReturn(subscription);
            when(subscriptionMapper.toResponse(any())).thenReturn(dummyResponse(SubscriptionStatus.ACTIVE));

            subscriptionService.resume(1L);

            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(outboxPublisher).publish(eq("SubscriptionResumedEvent"), eq("Subscription"), eq("1"), any());
        }

        @Test
        @DisplayName("Pausar assinatura CANCELED lanca excecao")
        void pause_canceled_shouldThrow() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.CANCELED).build();
            subscription.setId(3L);

            when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(subscription));

            assertThatThrownBy(() -> subscriptionService.pause(3L))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("Retomar assinatura ACTIVE lanca excecao (ja ativa)")
        void resume_active_shouldThrow() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.ACTIVE).build();
            subscription.setId(4L);

            when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(subscription));

            assertThatThrownBy(() -> subscriptionService.resume(4L))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("suspend()")
    class Suspend {

        @Test
        @DisplayName("Suspender assinatura ACTIVE por inadimplencia")
        void suspend_active_shouldWork() {
            Subscription subscription = Subscription.builder()
                    .company(company).customer(customer).plan(plan)
                    .status(SubscriptionStatus.ACTIVE).build();
            subscription.setId(1L);

            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any())).thenReturn(subscription);
            when(subscriptionMapper.toResponse(any())).thenReturn(dummyResponse(SubscriptionStatus.SUSPENDED));

            subscriptionService.suspend(1L, "3 cobrancas vencidas");

            assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
            verify(outboxPublisher).publish(eq("SubscriptionSuspendedEvent"), eq("Subscription"), eq("1"), any());
        }
    }

    @Nested
    @DisplayName("updatePaymentMethod()")
    class UpdatePaymentMethod {

        @Test
        @DisplayName("Alterar metodo de pagamento de assinatura ativa")
        void updatePaymentMethod_active_shouldWork() {
            Subscription subscription = Subscription.builder()
                    .company(company).customer(customer).plan(plan)
                    .billingType(BillingType.PIX).status(SubscriptionStatus.ACTIVE).build();
            subscription.setId(1L);

            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any())).thenReturn(subscription);
            when(subscriptionMapper.toResponse(any())).thenReturn(dummyResponse(SubscriptionStatus.ACTIVE));

            UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(
                    BillingType.CREDIT_CARD, null, null, null, null);

            subscriptionService.updatePaymentMethod(1L, request);

            assertThat(subscription.getBillingType()).isEqualTo(BillingType.CREDIT_CARD);
        }

        @Test
        @DisplayName("Alterar metodo de assinatura CANCELED lanca excecao")
        void updatePaymentMethod_canceled_shouldThrow() {
            Subscription subscription = Subscription.builder()
                    .company(company).status(SubscriptionStatus.CANCELED).build();
            subscription.setId(2L);

            when(subscriptionRepository.findById(2L)).thenReturn(Optional.of(subscription));

            UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(
                    BillingType.BOLETO, null, null, null, null);

            assertThatThrownBy(() -> subscriptionService.updatePaymentMethod(2L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cancelada");
        }
    }

    @Test
    @DisplayName("findById com ID inexistente lanca ResourceNotFoundException")
    void findById_notFound_shouldThrow() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
