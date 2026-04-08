package br.com.holding.payments.plan;

import br.com.holding.payments.plan.dto.PlanResponse;
import org.springframework.stereotype.Component;

@Component
public class PlanMapper {

    public PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getCompany().getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getValue(),
                plan.getCycle(),
                plan.getTrialDays(),
                plan.getSetupFee(),
                plan.getActive(),
                plan.getVersion(),
                plan.getLimits(),
                plan.getFeatures(),
                plan.getTierOrder(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
