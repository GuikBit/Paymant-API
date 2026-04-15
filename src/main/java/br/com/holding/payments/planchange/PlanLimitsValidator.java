package br.com.holding.payments.planchange;

import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanLimitCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Validates whether a downgrade is allowed by comparing current usage
 * against the target plan's limits.
 *
 * Uses SPI pattern: currentUsage is provided by the caller (e.g., via
 * an external system call or event).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanLimitsValidator {

    private final ObjectMapper objectMapper;
    private final PlanLimitCodec limitCodec;

    public ValidationResult validate(Plan targetPlan, Map<String, Integer> currentUsage,
                                     DowngradeValidationStrategy strategy) {
        Map<String, Integer> planLimits = parseLimits(targetPlan);

        if (planLimits.isEmpty()) {
            return ValidationResult.ok();
        }

        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, Integer> usage : currentUsage.entrySet()) {
            String key = usage.getKey();
            Integer currentValue = usage.getValue();
            Integer limit = planLimits.get(key);

            if (limit != null && currentValue > limit) {
                violations.add("Limite '%s' excedido: uso atual = %d, limite do plano = %d"
                        .formatted(key, currentValue, limit));
            }
        }

        if (violations.isEmpty()) {
            return ValidationResult.ok();
        }

        return switch (strategy) {
            case BLOCK -> ValidationResult.blocked(violations);
            case SCHEDULE -> ValidationResult.scheduled(violations);
            case GRACE_PERIOD -> ValidationResult.gracePeriod(violations);
        };
    }

    private Map<String, Integer> parseLimits(Plan plan) {
        if (plan.getLimits() == null || plan.getLimits().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Integer> structured = limitCodec.toNumberMap(plan.getLimits());
            if (!structured.isEmpty()) {
                return structured;
            }
            return objectMapper.readValue(plan.getLimits(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse plan limits for plan id={}: {}", plan.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    public record ValidationResult(
            boolean allowed,
            boolean shouldSchedule,
            boolean gracePeriod,
            List<String> violations
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, false, false, List.of());
        }

        public static ValidationResult blocked(List<String> violations) {
            return new ValidationResult(false, false, false, violations);
        }

        public static ValidationResult scheduled(List<String> violations) {
            return new ValidationResult(false, true, false, violations);
        }

        public static ValidationResult gracePeriod(List<String> violations) {
            return new ValidationResult(true, false, true, violations);
        }
    }
}
