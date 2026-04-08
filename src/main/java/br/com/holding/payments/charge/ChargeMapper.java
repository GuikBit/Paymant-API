package br.com.holding.payments.charge;

import br.com.holding.payments.charge.dto.ChargeResponse;
import org.springframework.stereotype.Component;

@Component
public class ChargeMapper {

    public ChargeResponse toResponse(Charge charge) {
        return new ChargeResponse(
                charge.getId(),
                charge.getCompany().getId(),
                charge.getCustomer().getId(),
                charge.getSubscriptionId(),
                charge.getInstallment() != null ? charge.getInstallment().getId() : null,
                charge.getAsaasId(),
                charge.getBillingType(),
                charge.getValue(),
                charge.getDueDate(),
                charge.getStatus(),
                charge.getOrigin(),
                charge.getExternalReference(),
                charge.getPixQrcode(),
                charge.getPixCopyPaste(),
                charge.getBoletoUrl(),
                charge.getInvoiceUrl(),
                charge.getInstallmentNumber(),
                charge.getCreatedAt(),
                charge.getUpdatedAt()
        );
    }
}
