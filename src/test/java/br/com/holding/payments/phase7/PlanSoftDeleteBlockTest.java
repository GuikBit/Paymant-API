package br.com.holding.payments.phase7;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.plan.*;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 7 - Bloqueio de soft delete de plano com assinaturas ativas")
class PlanSoftDeleteBlockTest {

    @Mock private PlanRepository planRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanMapper planMapper;

    @InjectMocks
    private PlanService planService;

    private Plan plan;

    @BeforeEach
    void setup() {
        Company company = Company.builder().cnpj("11111111000111").razaoSocial("Test").build();
        company.setId(1L);

        plan = Plan.builder()
                .company(company).name("Plano Basic")
                .codigo("test-plan")
                .precoMensal(BigDecimal.valueOf(49.90))
                .active(true).build();
        plan.setId(1L);

        TenantContext.setCompanyId(1L);
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Soft delete de plano COM assinaturas ativas eh bloqueado")
    void softDelete_withActiveSubscriptions_shouldThrow() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.countByPlanIdAndStatus(1L, SubscriptionStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> planService.softDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("assinatura");

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("Soft delete de plano SEM assinaturas ativas funciona")
    void softDelete_withNoActiveSubscriptions_shouldWork() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.countByPlanIdAndStatus(1L, SubscriptionStatus.ACTIVE)).thenReturn(0L);
        when(planRepository.save(any())).thenReturn(plan);

        planService.softDelete(1L);

        verify(planRepository).save(plan);
    }
}
