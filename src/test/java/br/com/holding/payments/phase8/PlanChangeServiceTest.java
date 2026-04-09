package br.com.holding.payments.phase8;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.charge.ChargeOrigin;
import br.com.holding.payments.charge.ChargeService;
import br.com.holding.payments.charge.ChargeStatus;
import br.com.holding.payments.charge.dto.ChargeResponse;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.company.PlanChangePolicy;
import br.com.holding.payments.creditledger.CreditLedgerOrigin;
import br.com.holding.payments.creditledger.CustomerCreditLedger;
import br.com.holding.payments.creditledger.CustomerCreditLedgerService;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanCycle;
import br.com.holding.payments.plan.PlanRepository;
import br.com.holding.payments.planchange.*;
import br.com.holding.payments.planchange.dto.PlanChangePreviewResponse;
import br.com.holding.payments.planchange.dto.PlanChangeResponse;
import br.com.holding.payments.planchange.dto.RequestPlanChangeRequest;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 8 - PlanChangeService")
class PlanChangeServiceTest {

    @Mock private SubscriptionPlanChangeRepository planChangeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private CustomerCreditLedgerService creditLedgerService;
    @Mock private ChargeService chargeService;
    @Mock private PlanLimitsValidator planLimitsValidator;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private PlanChangeMapper planChangeMapper;

    @InjectMocks
    private PlanChangeService service;

    private Company company;
    private Customer customer;
    private Plan currentPlan;
    private Plan upgradePlan;
    private Plan downgradePlan;
    private Plan samePricePlan;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);

        company = Company.builder()
                .cnpj("12345678000100")
                .razaoSocial("Test Company")
                .planChangePolicy(PlanChangePolicy.IMMEDIATE_PRORATA)
                .downgradeValidationStrategy(DowngradeValidationStrategy.BLOCK)
                .build();
        company.setId(1L);  // id is inherited from BaseEntity, not in builder

        customer = Customer.builder()
                .id(10L)
                .company(company)
                .name("Test Customer")
                .creditBalance(BigDecimal.ZERO)
                .build();

        currentPlan = Plan.builder()
                .id(100L)
                .name("Basic")
                .value(new BigDecimal("100.00"))
                .cycle(PlanCycle.MONTHLY)
                .active(true)
                .build();

        upgradePlan = Plan.builder()
                .id(200L)
                .name("Pro")
                .value(new BigDecimal("200.00"))
                .cycle(PlanCycle.MONTHLY)
                .active(true)
                .build();

        downgradePlan = Plan.builder()
                .id(300L)
                .name("Free")
                .value(new BigDecimal("50.00"))
                .cycle(PlanCycle.MONTHLY)
                .active(true)
                .build();

        samePricePlan = Plan.builder()
                .id(400L)
                .name("Basic Alt")
                .value(new BigDecimal("100.00"))
                .cycle(PlanCycle.MONTHLY)
                .active(true)
                .build();

        subscription = Subscription.builder()
                .id(1000L)
                .company(company)
                .customer(customer)
                .plan(currentPlan)
                .billingType(BillingType.PIX)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.of(2026, 4, 1, 0, 0))
                .nextDueDate(LocalDate.of(2026, 5, 1))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PlanChangeResponse dummyResponse(SubscriptionPlanChange change) {
        return new PlanChangeResponse(
                change.getId(), 1000L, 100L, "Basic", 200L, "Pro",
                change.getChangeType(), change.getPolicy(),
                change.getDeltaAmount(), change.getProrationCredit(), change.getProrationCharge(),
                change.getStatus(), null, null, null, null, null, null, null);
    }

    // ==================== PREVIEW ====================

    @Nested
    @DisplayName("previewChange()")
    class PreviewTests {

        @Test
        @DisplayName("Preview de upgrade retorna delta positivo e tipo UPGRADE")
        void previewUpgrade_shouldReturnPositiveDelta() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));

            PlanChangePreviewResponse preview = service.previewChange(1000L, 200L);

            assertThat(preview.subscriptionId()).isEqualTo(1000L);
            assertThat(preview.currentPlanId()).isEqualTo(100L);
            assertThat(preview.newPlanId()).isEqualTo(200L);
            assertThat(preview.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            assertThat(preview.delta()).isGreaterThan(BigDecimal.ZERO);
            assertThat(preview.prorationCharge()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Preview de downgrade retorna delta negativo")
        void previewDowngrade_shouldReturnNegativeDelta() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(300L)).thenReturn(Optional.of(downgradePlan));

            PlanChangePreviewResponse preview = service.previewChange(1000L, 300L);

            assertThat(preview.changeType()).isEqualTo(PlanChangeType.DOWNGRADE);
            assertThat(preview.delta()).isLessThan(BigDecimal.ZERO);
            assertThat(preview.prorationCredit()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Preview de sidegrade retorna delta zero")
        void previewSidegrade_shouldReturnZeroDelta() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(400L)).thenReturn(Optional.of(samePricePlan));

            PlanChangePreviewResponse preview = service.previewChange(1000L, 400L);

            assertThat(preview.changeType()).isEqualTo(PlanChangeType.SIDEGRADE);
            assertThat(preview.delta()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Preview com politica END_OF_CYCLE nao calcula pro-rata")
        void previewEndOfCycle_shouldNotCalculateProRata() {
            company.setPlanChangePolicy(PlanChangePolicy.END_OF_CYCLE);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));

            PlanChangePreviewResponse preview = service.previewChange(1000L, 200L);

            assertThat(preview.policy()).isEqualTo(
                    br.com.holding.payments.planchange.PlanChangePolicy.END_OF_CYCLE);
            // Delta = newValue - currentValue (sem pro-rata)
            assertThat(preview.delta()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(preview.prorationCredit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(preview.prorationCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Preview com assinatura inexistente lanca ResourceNotFoundException")
        void previewNonExistentSubscription_shouldThrow() {
            when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.previewChange(999L, 200L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Preview com plano inexistente lanca ResourceNotFoundException")
        void previewNonExistentPlan_shouldThrow() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.previewChange(1000L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Preview com assinatura nao ativa lanca BusinessException")
        void previewInactiveSubscription_shouldThrow() {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));

            assertThatThrownBy(() -> service.previewChange(1000L, 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ativas");
        }

        @Test
        @DisplayName("Preview com plano destino inativo lanca BusinessException")
        void previewInactivePlan_shouldThrow() {
            upgradePlan.setActive(false);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));

            assertThatThrownBy(() -> service.previewChange(1000L, 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("inativo");
        }

        @Test
        @DisplayName("Preview com mesmo plano lanca BusinessException")
        void previewSamePlan_shouldThrow() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(100L)).thenReturn(Optional.of(currentPlan));

            assertThatThrownBy(() -> service.previewChange(1000L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("mesmo plano");
        }
    }

    // ==================== REQUEST CHANGE ====================

    @Nested
    @DisplayName("requestChange()")
    class RequestChangeTests {

        @Test
        @DisplayName("Upgrade com PIX cria cobranca e vai para AWAITING_PAYMENT")
        void upgradeWithPix_shouldCreateChargeAndAwaitPayment() {
            subscription.setBillingType(BillingType.PIX);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(planChangeRepository.save(any())).thenAnswer(inv -> {
                SubscriptionPlanChange pc = inv.getArgument(0);
                pc.setId(1L);
                return pc;
            });
            when(chargeService.createUndefinedCharge(any())).thenReturn(
                    new ChargeResponse(50L, 1L, 10L, null, null, null,
                            BillingType.PIX, new BigDecimal("50.00"), LocalDate.now().plusDays(1),
                            ChargeStatus.PENDING, ChargeOrigin.PLAN_CHANGE, null,
                            null, null, null, null, null, null, null));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(200L, null, "admin");
            PlanChangeResponse response = service.requestChange(1000L, request);

            assertThat(response).isNotNull();
            verify(chargeService).createUndefinedCharge(any());
            verify(outboxPublisher).publish(eq("PlanChangePendingPaymentEvent"), anyString(), anyString(), any());
            // Plano NAO deve ter sido alterado ainda (aguardando pagamento)
            assertThat(subscription.getPlan().getId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Upgrade com cartao de credito aplica imediatamente")
        void upgradeWithCreditCard_shouldApplyImmediately() {
            subscription.setBillingType(BillingType.CREDIT_CARD);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(planChangeRepository.save(any())).thenAnswer(inv -> {
                SubscriptionPlanChange pc = inv.getArgument(0);
                if (pc.getId() == null) pc.setId(1L);
                return pc;
            });
            when(chargeService.createUndefinedCharge(any())).thenReturn(
                    new ChargeResponse(50L, 1L, 10L, null, null, null,
                            BillingType.CREDIT_CARD, new BigDecimal("50.00"), LocalDate.now().plusDays(1),
                            ChargeStatus.CONFIRMED, ChargeOrigin.PLAN_CHANGE, null,
                            null, null, null, null, null, null, null));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(200L, null, "admin");
            service.requestChange(1000L, request);

            // Plano deve ter sido alterado imediatamente
            assertThat(subscription.getPlan()).isEqualTo(upgradePlan);
            verify(subscriptionRepository).save(subscription);
            verify(outboxPublisher).publish(eq("PlanChangedEvent"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Downgrade gera credito no ledger e aplica imediatamente")
        void downgrade_shouldAddCreditAndApply() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(300L)).thenReturn(Optional.of(downgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(planLimitsValidator.validate(any(), any(), any()))
                    .thenReturn(PlanLimitsValidator.ValidationResult.ok());
            when(planChangeRepository.save(any())).thenAnswer(inv -> {
                SubscriptionPlanChange pc = inv.getArgument(0);
                if (pc.getId() == null) pc.setId(1L);
                return pc;
            });
            when(creditLedgerService.addCredit(anyLong(), any(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(CustomerCreditLedger.builder().id(5L).build());
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(300L, Collections.emptyMap(), "admin");
            service.requestChange(1000L, request);

            verify(creditLedgerService).addCredit(eq(10L), any(BigDecimal.class),
                    eq(CreditLedgerOrigin.DOWNGRADE_PRORATA), anyString(), anyString(), eq("admin"));
            assertThat(subscription.getPlan()).isEqualTo(downgradePlan);
            verify(outboxPublisher).publish(eq("PlanChangedEvent"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Sidegrade troca plano sem cobranca nem credito")
        void sidegrade_shouldSwapPlanOnly() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(400L)).thenReturn(Optional.of(samePricePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(planChangeRepository.save(any())).thenAnswer(inv -> {
                SubscriptionPlanChange pc = inv.getArgument(0);
                if (pc.getId() == null) pc.setId(1L);
                return pc;
            });
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(400L, null, "admin");
            service.requestChange(1000L, request);

            verify(chargeService, never()).createUndefinedCharge(any());
            verify(creditLedgerService, never()).addCredit(anyLong(), any(), any(), any(), any(), any());
            assertThat(subscription.getPlan()).isEqualTo(samePricePlan);
        }

        @Test
        @DisplayName("Mudanca pendente existente bloqueia nova solicitacao")
        void pendingChangeExists_shouldThrow() {
            SubscriptionPlanChange existing = SubscriptionPlanChange.builder()
                    .id(99L).status(PlanChangeStatus.AWAITING_PAYMENT).build();
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.of(existing));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(200L, null, "admin");

            assertThatThrownBy(() -> service.requestChange(1000L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("pendente");
        }

        @Test
        @DisplayName("Politica END_OF_CYCLE agenda mudanca para fim do ciclo")
        void endOfCyclePolicy_shouldSchedule() {
            company.setPlanChangePolicy(PlanChangePolicy.END_OF_CYCLE);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(200L)).thenReturn(Optional.of(upgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(planChangeRepository.save(any())).thenAnswer(inv -> {
                SubscriptionPlanChange pc = inv.getArgument(0);
                pc.setId(1L);
                return pc;
            });
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(200L, null, "admin");
            service.requestChange(1000L, request);

            // Plano NAO deve ter sido alterado
            assertThat(subscription.getPlan().getId()).isEqualTo(100L);
            verify(outboxPublisher).publish(eq("PlanChangeScheduledEvent"), anyString(), anyString(), any());
            verify(chargeService, never()).createUndefinedCharge(any());
        }

        @Test
        @DisplayName("Downgrade bloqueado por BLOCK strategy lanca BusinessException")
        void downgradeBlocked_shouldThrow() {
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(300L)).thenReturn(Optional.of(downgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(planLimitsValidator.validate(any(), any(), any()))
                    .thenReturn(PlanLimitsValidator.ValidationResult.blocked(List.of("users excedido")));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(300L, Map.of("users", 10), "admin");

            assertThatThrownBy(() -> service.requestChange(1000L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("bloqueado");
        }

        @Test
        @DisplayName("Downgrade com estrategia SCHEDULE forca politica END_OF_CYCLE")
        void downgradeScheduleStrategy_shouldForcEndOfCycle() {
            company.setDowngradeValidationStrategy(DowngradeValidationStrategy.SCHEDULE);
            when(subscriptionRepository.findById(1000L)).thenReturn(Optional.of(subscription));
            when(planRepository.findById(300L)).thenReturn(Optional.of(downgradePlan));
            when(planChangeRepository.findPendingBySubscriptionId(1000L)).thenReturn(Optional.empty());
            when(companyRepository.getReferenceById(1L)).thenReturn(company);
            when(planLimitsValidator.validate(any(), any(), eq(DowngradeValidationStrategy.SCHEDULE)))
                    .thenReturn(PlanLimitsValidator.ValidationResult.scheduled(List.of("users excedido")));
            when(planChangeRepository.save(any())).thenAnswer(inv -> {
                SubscriptionPlanChange pc = inv.getArgument(0);
                pc.setId(1L);
                return pc;
            });
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            RequestPlanChangeRequest request = new RequestPlanChangeRequest(300L, Map.of("users", 10), "admin");
            service.requestChange(1000L, request);

            // Deve ter agendado ao inves de aplicar imediatamente
            assertThat(subscription.getPlan().getId()).isEqualTo(100L);
            verify(outboxPublisher).publish(eq("PlanChangeScheduledEvent"), anyString(), anyString(), any());
        }
    }

    // ==================== CONFIRM AFTER PAYMENT ====================

    @Nested
    @DisplayName("confirmAfterPayment()")
    class ConfirmAfterPaymentTests {

        @Test
        @DisplayName("Confirma mudanca apos pagamento da cobranca Delta")
        void confirmAfterPayment_shouldApplyPlanChange() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(1L)
                    .status(PlanChangeStatus.AWAITING_PAYMENT)
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(br.com.holding.payments.planchange.PlanChangePolicy.IMMEDIATE_PRORATA)
                    .deltaAmount(new BigDecimal("50.00"))
                    .build();

            when(planChangeRepository.findByChargeId(50L)).thenReturn(Optional.of(planChange));
            when(planChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            service.confirmAfterPayment(50L);

            assertThat(subscription.getPlan()).isEqualTo(upgradePlan);
            assertThat(planChange.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
            assertThat(planChange.getEffectiveAt()).isNotNull();
            verify(subscriptionRepository).save(subscription);
            verify(outboxPublisher).publish(eq("PlanChangedEvent"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Ignora se nenhuma mudanca associada a cobranca")
        void noMatchingPlanChange_shouldDoNothing() {
            when(planChangeRepository.findByChargeId(999L)).thenReturn(Optional.empty());

            service.confirmAfterPayment(999L);

            verify(subscriptionRepository, never()).save(any());
            verify(outboxPublisher, never()).publish(anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Ignora se mudanca nao esta em AWAITING_PAYMENT")
        void notAwaitingPayment_shouldDoNothing() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(1L)
                    .status(PlanChangeStatus.EFFECTIVE)
                    .subscription(subscription)
                    .requestedPlan(upgradePlan)
                    .build();

            when(planChangeRepository.findByChargeId(50L)).thenReturn(Optional.of(planChange));

            service.confirmAfterPayment(50L);

            verify(subscriptionRepository, never()).save(any());
        }
    }

    // ==================== CANCEL ====================

    @Nested
    @DisplayName("cancelChange()")
    class CancelTests {

        @Test
        @DisplayName("Cancela mudanca pendente com sucesso")
        void cancelPendingChange_shouldWork() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(5L)
                    .status(PlanChangeStatus.PENDING)
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(br.com.holding.payments.planchange.PlanChangePolicy.IMMEDIATE_PRORATA)
                    .deltaAmount(BigDecimal.ZERO)
                    .build();

            when(planChangeRepository.findById(5L)).thenReturn(Optional.of(planChange));
            when(planChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            PlanChangeResponse response = service.cancelChange(1000L, 5L);

            assertThat(planChange.getStatus()).isEqualTo(PlanChangeStatus.CANCELED);
        }

        @Test
        @DisplayName("Cancela mudanca AWAITING_PAYMENT com sucesso")
        void cancelAwaitingPayment_shouldWork() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(5L)
                    .status(PlanChangeStatus.AWAITING_PAYMENT)
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(br.com.holding.payments.planchange.PlanChangePolicy.IMMEDIATE_PRORATA)
                    .deltaAmount(BigDecimal.ZERO)
                    .build();

            when(planChangeRepository.findById(5L)).thenReturn(Optional.of(planChange));
            when(planChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            service.cancelChange(1000L, 5L);

            assertThat(planChange.getStatus()).isEqualTo(PlanChangeStatus.CANCELED);
        }

        @Test
        @DisplayName("Cancela mudanca SCHEDULED com sucesso")
        void cancelScheduled_shouldWork() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(5L)
                    .status(PlanChangeStatus.SCHEDULED)
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(br.com.holding.payments.planchange.PlanChangePolicy.END_OF_CYCLE)
                    .deltaAmount(BigDecimal.ZERO)
                    .build();

            when(planChangeRepository.findById(5L)).thenReturn(Optional.of(planChange));
            when(planChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            service.cancelChange(1000L, 5L);

            assertThat(planChange.getStatus()).isEqualTo(PlanChangeStatus.CANCELED);
        }

        @Test
        @DisplayName("Cancelar mudanca inexistente lanca ResourceNotFoundException")
        void cancelNonExistent_shouldThrow() {
            when(planChangeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelChange(1000L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Cancelar mudanca de outra assinatura lanca BusinessException")
        void cancelWrongSubscription_shouldThrow() {
            Subscription otherSubscription = Subscription.builder().id(2000L).build();
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(5L)
                    .status(PlanChangeStatus.PENDING)
                    .subscription(otherSubscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .build();

            when(planChangeRepository.findById(5L)).thenReturn(Optional.of(planChange));

            assertThatThrownBy(() -> service.cancelChange(1000L, 5L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("nao pertence");
        }
    }

    // ==================== PROCESS SCHEDULED ====================

    @Nested
    @DisplayName("processScheduledChanges()")
    class ProcessScheduledTests {

        @Test
        @DisplayName("Processa mudancas agendadas prontas")
        void processScheduled_shouldApplyReadyChanges() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(1L)
                    .company(company)
                    .status(PlanChangeStatus.SCHEDULED)
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(br.com.holding.payments.planchange.PlanChangePolicy.END_OF_CYCLE)
                    .scheduledFor(LocalDateTime.now().minusDays(1))
                    .deltaAmount(new BigDecimal("100.00"))
                    .build();

            when(planChangeRepository.findScheduledReadyToProcess(any())).thenReturn(List.of(planChange));
            when(planChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planChangeMapper.toResponse(any())).thenAnswer(inv -> dummyResponse(inv.getArgument(0)));

            service.processScheduledChanges();

            assertThat(subscription.getPlan()).isEqualTo(upgradePlan);
            assertThat(planChange.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
            verify(subscriptionRepository).save(subscription);
            verify(outboxPublisher).publish(eq("PlanChangedEvent"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Nenhuma mudanca agendada - nao faz nada")
        void noScheduledChanges_shouldDoNothing() {
            when(planChangeRepository.findScheduledReadyToProcess(any())).thenReturn(Collections.emptyList());

            service.processScheduledChanges();

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Erro ao processar marca como FAILED sem interromper outros")
        void errorProcessing_shouldMarkFailed() {
            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .id(1L)
                    .company(company)
                    .status(PlanChangeStatus.SCHEDULED)
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(upgradePlan)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(br.com.holding.payments.planchange.PlanChangePolicy.END_OF_CYCLE)
                    .deltaAmount(new BigDecimal("100.00"))
                    .build();

            when(planChangeRepository.findScheduledReadyToProcess(any())).thenReturn(List.of(planChange));
            when(subscriptionRepository.save(any())).thenThrow(new RuntimeException("DB error"));
            when(planChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledChanges();

            assertThat(planChange.getStatus()).isEqualTo(PlanChangeStatus.FAILED);
            assertThat(planChange.getFailureReason()).contains("DB error");
        }
    }
}
