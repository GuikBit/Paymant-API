package br.com.holding.payments.charge.dto;

import br.com.holding.payments.charge.ChargeOrigin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateChargeRequest(
        @NotNull(message = "ID do cliente e obrigatorio") Long customerId,
        @NotNull(message = "Valor e obrigatorio") @Positive(message = "Valor deve ser positivo") BigDecimal value,
        @NotNull(message = "Data de vencimento e obrigatoria") LocalDate dueDate,
        String description,
        String externalReference,
        ChargeOrigin origin,
        // Credit card fields (optional)
        CreditCardInfo creditCard,
        CreditCardHolderInfo creditCardHolderInfo,
        String creditCardToken,
        String remoteIp,
        // Installment fields (optional)
        Integer installmentCount,
        BigDecimal installmentValue
) {
    public record CreditCardInfo(
            String holderName,
            String number,
            String expiryMonth,
            String expiryYear,
            String ccv
    ) {}

    public record CreditCardHolderInfo(
            String name,
            String email,
            String cpfCnpj,
            String postalCode,
            String addressNumber,
            String phone
    ) {}
}
