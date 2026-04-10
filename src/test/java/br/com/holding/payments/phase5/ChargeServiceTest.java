package br.com.holding.payments.phase5;

import br.com.holding.payments.charge.*;
import br.com.holding.payments.charge.dto.ChargeResponse;
import br.com.holding.payments.charge.dto.CreateChargeRequest;
import br.com.holding.payments.charge.dto.RefundRequest;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.installment.InstallmentRepository;
import br.com.holding.payments.integration.asaas.gateway.AsaasGatewayService;
import br.com.holding.payments.integration.asaas.gateway.AsaasPaymentResult;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.tenant.TenantContext;
import br.com.holding.payments.webhook.WebhookService;
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
@DisplayName("Fase 5 - ChargeService")
class ChargeServiceTest {

    @Mock private ChargeRepository chargeRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private ChargeMapper chargeMapper;
    @Mock private AsaasGatewayService asaasGateway;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private WebhookService webhookService;

    @InjectMocks
    private ChargeService chargeService;

    private Company company;
    private Customer customer;

    @BeforeEach
    void setup() {
        company = Company.builder().cnpj("11111111000111").razaoSocial("Test LTDA").build();
        company.setId(1L);

        customer = Customer.builder()
                .company(company).name("John Doe")
                .document("12345678901").asaasId("cus_test123").build();
        customer.setId(10L);

        TenantContext.setCompanyId(1L);
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    private CreateChargeRequest buildRequest() {
        return new CreateChargeRequest(
                10L, BigDecimal.valueOf(100), LocalDate.now().plusDays(5),
                "Test charge", null, null,
                null, null, null, null, null, null, null
        );
    }

    private AsaasPaymentResult buildAsaasResult() {
        return new AsaasPaymentResult(
                "pay_test123", "cus_test123", "PIX",
                BigDecimal.valueOf(100), BigDecimal.valueOf(97),
                "PENDING", LocalDate.now().plusDays(5).toString(), null,
                "https://invoice.url", null, null, null
        );
    }

    private ChargeResponse dummyResponse() {
        return new ChargeResponse(
                1L, 1L, 10L, null, null, "pay_test123",
                BillingType.PIX, BigDecimal.valueOf(100), LocalDate.now().plusDays(5),
                ChargeStatus.PENDING, ChargeOrigin.API, null,
                null, null, null, "https://invoice.url", null,
                null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Criar cobranca PIX com sucesso - sincroniza com Asaas e publica evento outbox")
    void createPixCharge_shouldCreateAndPublishEvent() {
        when(companyRepository.getReferenceById(1L)).thenReturn(company);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(asaasGateway.createPayment(eq(1L), any())).thenReturn(buildAsaasResult());
        when(chargeRepository.save(any(Charge.class))).thenAnswer(inv -> {
            Charge c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(chargeMapper.toResponse(any())).thenReturn(dummyResponse());

        ChargeResponse result = chargeService.createPixCharge(buildRequest());

        assertThat(result).isNotNull();
        assertThat(result.asaasId()).isEqualTo("pay_test123");
        verify(outboxPublisher).publish(eq("ChargeCreatedEvent"), eq("Charge"), eq("1"), any());
        verify(webhookService).accelerateDeferredForAsaasId("pay_test123");
    }

    @Test
    @DisplayName("Criar cobranca com cliente sem asaas_id lanca BusinessException")
    void createCharge_customerWithoutAsaasId_shouldThrow() {
        Customer noAsaas = Customer.builder().company(company).name("No Asaas").document("00000000000").build();
        noAsaas.setId(10L);

        when(companyRepository.getReferenceById(1L)).thenReturn(company);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(noAsaas));

        assertThatThrownBy(() -> chargeService.createPixCharge(buildRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("asaas_id");
    }

    @Test
    @DisplayName("Criar cobranca com cliente inexistente lanca ResourceNotFoundException")
    void createCharge_customerNotFound_shouldThrow() {
        when(companyRepository.getReferenceById(1L)).thenReturn(company);
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.createPixCharge(buildRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Cancelar cobranca PENDING com sucesso")
    void cancel_pendingCharge_shouldTransitionAndSync() {
        Charge charge = Charge.builder()
                .company(company).customer(customer)
                .asaasId("pay_cancel123").billingType(BillingType.PIX)
                .value(BigDecimal.valueOf(100)).dueDate(LocalDate.now())
                .status(ChargeStatus.PENDING).build();
        charge.setId(5L);

        when(chargeRepository.findById(5L)).thenReturn(Optional.of(charge));
        when(chargeRepository.save(any())).thenReturn(charge);
        when(chargeMapper.toResponse(any())).thenReturn(dummyResponse());

        chargeService.cancel(5L);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELED);
        verify(asaasGateway).cancelPayment(1L, "pay_cancel123");
        verify(outboxPublisher).publish(eq("ChargeCanceledEvent"), eq("Charge"), eq("5"), any());
    }

    @Test
    @DisplayName("Cancelar cobranca ja REFUNDED lanca IllegalStateTransitionException")
    void cancel_refundedCharge_shouldThrow() {
        Charge charge = Charge.builder()
                .company(company).customer(customer)
                .asaasId("pay_ref").billingType(BillingType.PIX)
                .value(BigDecimal.valueOf(100)).dueDate(LocalDate.now())
                .status(ChargeStatus.REFUNDED).build();
        charge.setId(6L);

        when(chargeRepository.findById(6L)).thenReturn(Optional.of(charge));

        assertThatThrownBy(() -> chargeService.cancel(6L))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("Estornar cobranca CONFIRMED com sucesso")
    void refund_confirmedCharge_shouldWork() {
        Charge charge = Charge.builder()
                .company(company).customer(customer)
                .asaasId("pay_refund123").billingType(BillingType.CREDIT_CARD)
                .value(BigDecimal.valueOf(200)).dueDate(LocalDate.now())
                .status(ChargeStatus.CONFIRMED).build();
        charge.setId(7L);

        when(chargeRepository.findById(7L)).thenReturn(Optional.of(charge));
        when(chargeRepository.save(any())).thenReturn(charge);
        when(chargeMapper.toResponse(any())).thenReturn(dummyResponse());

        chargeService.refund(7L, new RefundRequest(BigDecimal.valueOf(200), "Cliente solicitou"));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REFUNDED);
        verify(asaasGateway).refundPayment(1L, "pay_refund123", BigDecimal.valueOf(200), "Cliente solicitou");
        verify(outboxPublisher).publish(eq("ChargeRefundedEvent"), eq("Charge"), eq("7"), any());
    }

    @Test
    @DisplayName("Criar cobranca boleto com sucesso")
    void createBoletoCharge_shouldWork() {
        when(companyRepository.getReferenceById(1L)).thenReturn(company);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(asaasGateway.createPayment(eq(1L), any())).thenReturn(buildAsaasResult());
        when(chargeRepository.save(any(Charge.class))).thenAnswer(inv -> {
            Charge c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });
        when(chargeMapper.toResponse(any())).thenReturn(dummyResponse());

        ChargeResponse result = chargeService.createBoletoCharge(buildRequest());

        assertThat(result).isNotNull();
        verify(asaasGateway).createPayment(eq(1L), any());
    }

    @Test
    @DisplayName("Criar cobranca cartao de credito com sucesso")
    void createCreditCardCharge_shouldWork() {
        when(companyRepository.getReferenceById(1L)).thenReturn(company);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(asaasGateway.createPayment(eq(1L), any())).thenReturn(buildAsaasResult());
        when(chargeRepository.save(any(Charge.class))).thenAnswer(inv -> {
            Charge c = inv.getArgument(0);
            c.setId(3L);
            return c;
        });
        when(chargeMapper.toResponse(any())).thenReturn(dummyResponse());

        ChargeResponse result = chargeService.createCreditCardCharge(buildRequest());

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Parcelamento requer pelo menos 2 parcelas")
    void installments_lessThan2_shouldThrow() {
        CreateChargeRequest req = new CreateChargeRequest(
                10L, BigDecimal.valueOf(100), LocalDate.now().plusDays(5),
                null, null, null, null, null, null, null,
                1, null, null
        );

        assertThatThrownBy(() -> chargeService.createCreditCardInstallments(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("parcelas");
    }
}
