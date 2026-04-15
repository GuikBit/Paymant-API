package br.com.holding.payments.plan;

import br.com.holding.payments.plan.dto.PlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PlanMapper {

    private final PlanLimitCodec limitCodec;

    public PlanResponse toResponse(Plan plan) {
        BigDecimal precoSemestral;
        if (isPromoMensalActiveNow(plan)) {
            precoSemestral = plan.getPromoMensalPreco().multiply(BigDecimal.valueOf(6));
        } else {
            precoSemestral = plan.getPrecoMensal().multiply(BigDecimal.valueOf(6));
        }

        return new PlanResponse(
                plan.getId(),
                plan.getCompany().getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getCodigo(),
                plan.getPrecoMensal(),
                plan.getPrecoAnual(),
                plan.getDescontoPercentualAnual(),
                precoSemestral,
                plan.getPromoMensalAtiva(),
                plan.getPromoMensalPreco(),
                plan.getPromoMensalTexto(),
                plan.getPromoMensalInicio(),
                plan.getPromoMensalFim(),
                plan.getPromoAnualAtiva(),
                plan.getPromoAnualPreco(),
                plan.getPromoAnualTexto(),
                plan.getPromoAnualInicio(),
                plan.getPromoAnualFim(),
                plan.getTrialDays(),
                plan.getSetupFee(),
                plan.getActive(),
                plan.getVersion(),
                limitCodec.deserialize(plan.getLimits()),
                limitCodec.deserialize(plan.getFeatures()),
                plan.getTierOrder(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private boolean isPromoMensalActiveNow(Plan plan) {
        if (!Boolean.TRUE.equals(plan.getPromoMensalAtiva())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return plan.getPromoMensalInicio() != null
                && plan.getPromoMensalFim() != null
                && !now.isBefore(plan.getPromoMensalInicio())
                && !now.isAfter(plan.getPromoMensalFim());
    }
}
