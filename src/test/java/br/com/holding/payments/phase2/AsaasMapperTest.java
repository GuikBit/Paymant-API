package br.com.holding.payments.phase2;

import br.com.holding.payments.integration.asaas.dto.AsaasCustomerResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPaymentResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasSubscriptionResponse;
import br.com.holding.payments.integration.asaas.gateway.AsaasCustomerData;
import br.com.holding.payments.integration.asaas.gateway.AsaasCustomerResult;
import br.com.holding.payments.integration.asaas.gateway.AsaasPaymentResult;
import br.com.holding.payments.integration.asaas.gateway.AsaasSubscriptionResult;
import br.com.holding.payments.integration.asaas.mapper.AsaasMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 2 - AsaasMapper nao vaza DTOs do Asaas")
class AsaasMapperTest {

    private final AsaasMapper mapper = new AsaasMapper();

    @Test
    @DisplayName("toCustomerResult retorna objeto de dominio, nao DTO do Asaas")
    void toCustomerResult_returnsDomainObject() {
        AsaasCustomerResponse asaasDto = new AsaasCustomerResponse(
                "cus_123", "John", "12345678901", "john@test.com",
                "11999999999", null, null, null, null, null, null,
                null, false, "Sao Paulo", "SP", "BR", "FISICA", false, "2024-01-01"
        );

        AsaasCustomerResult result = mapper.toCustomerResult(asaasDto);

        assertThat(result).isInstanceOf(AsaasCustomerResult.class);
        assertThat(result.asaasId()).isEqualTo("cus_123");
        assertThat(result.name()).isEqualTo("John");
        assertThat(result.cpfCnpj()).isEqualTo("12345678901");
    }

    @Test
    @DisplayName("toPaymentResult retorna objeto de dominio, nao DTO do Asaas")
    void toPaymentResult_returnsDomainObject() {
        AsaasPaymentResponse asaasDto = new AsaasPaymentResponse(
                "pay_456", "cus_123", "PIX", new BigDecimal("100.00"),
                new BigDecimal("97.00"), "PENDING", "2024-12-31", null,
                null, null, null, null, null, null, null, null, null, null, false, "2024-01-01"
        );

        AsaasPaymentResult result = mapper.toPaymentResult(asaasDto);

        assertThat(result).isInstanceOf(AsaasPaymentResult.class);
        assertThat(result.asaasId()).isEqualTo("pay_456");
        assertThat(result.value()).isEqualByComparingTo("100.00");
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("toSubscriptionResult retorna objeto de dominio, nao DTO do Asaas")
    void toSubscriptionResult_returnsDomainObject() {
        AsaasSubscriptionResponse asaasDto = new AsaasSubscriptionResponse(
                "sub_789", "cus_123", "CREDIT_CARD", new BigDecimal("49.90"),
                "2024-02-01", "MONTHLY", null, null, "ACTIVE", false, "2024-01-01"
        );

        AsaasSubscriptionResult result = mapper.toSubscriptionResult(asaasDto);

        assertThat(result).isInstanceOf(AsaasSubscriptionResult.class);
        assertThat(result.asaasId()).isEqualTo("sub_789");
        assertThat(result.cycle()).isEqualTo("MONTHLY");
    }

    @Test
    @DisplayName("toAsaasCustomerRequest converte dados de dominio para DTO Asaas")
    void toAsaasCustomerRequest_convertsDomainToAsaas() {
        AsaasCustomerData data = new AsaasCustomerData(
                "Maria", "98765432100", "maria@test.com", "11888888888",
                null, "Rua A", "100", null, "Centro", "01000000", "ref-123"
        );

        var request = mapper.toAsaasCustomerRequest(data);

        assertThat(request.name()).isEqualTo("Maria");
        assertThat(request.cpfCnpj()).isEqualTo("98765432100");
        assertThat(request.externalReference()).isEqualTo("ref-123");
    }
}
