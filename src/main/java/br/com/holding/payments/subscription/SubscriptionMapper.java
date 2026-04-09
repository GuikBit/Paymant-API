package br.com.holding.payments.subscription;

import br.com.holding.payments.subscription.dto.SubscriptionResponse;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getCompany().getId(),
                subscription.getCustomer().getId(),
                subscription.getPlan().getId(),
                subscription.getPlan().getName(),
                subscription.getAsaasId(),
                subscription.getBillingType(),
                subscription.getPlan().getValue(),
                subscription.getPlan().getCycle().name(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getNextDueDate(),
                subscription.getStatus(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
