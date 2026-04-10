package br.com.holding.payments.planchange;

import br.com.holding.payments.plan.PlanCycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure calculator for pro-rata plan change amounts.
 * No dependencies — all inputs are explicit.
 *
 * Formula:
 *   creditUnused = currentValue * (remainingDays / totalDays)
 *   newCost      = newValue     * (remainingDays / totalDays)
 *   delta        = newCost - creditUnused
 *
 * delta > 0 → upgrade charge
 * delta < 0 → downgrade credit (absolute value)
 * delta == 0 → sidegrade (no financial impact)
 */
public final class ProrationCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private ProrationCalculator() {}

    public static ProrationResult calculate(BigDecimal currentPlanValue,
                                             BigDecimal newPlanValue,
                                             LocalDate periodStart,
                                             LocalDate periodEnd,
                                             LocalDate changeDate) {
        if (periodStart == null || periodEnd == null || changeDate == null) {
            throw new IllegalArgumentException("Period dates and change date must not be null");
        }
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("Period end must be after period start");
        }
        if (changeDate.isBefore(periodStart) || changeDate.isAfter(periodEnd)) {
            throw new IllegalArgumentException("Change date must be within the billing period");
        }

        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long remainingDays = ChronoUnit.DAYS.between(changeDate, periodEnd);

        if (totalDays == 0) {
            return new ProrationResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    determinePlanChangeType(currentPlanValue, newPlanValue));
        }

        BigDecimal totalDaysBd = BigDecimal.valueOf(totalDays);
        BigDecimal remainingDaysBd = BigDecimal.valueOf(remainingDays);

        BigDecimal creditUnused = currentPlanValue
                .multiply(remainingDaysBd)
                .divide(totalDaysBd, SCALE + 4, ROUNDING);

        BigDecimal newCostProportional = newPlanValue
                .multiply(remainingDaysBd)
                .divide(totalDaysBd, SCALE + 4, ROUNDING);

        BigDecimal delta = newCostProportional.subtract(creditUnused)
                .setScale(SCALE, ROUNDING);

        BigDecimal prorationCredit = BigDecimal.ZERO;
        BigDecimal prorationCharge = BigDecimal.ZERO;

        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            prorationCharge = delta;
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            prorationCredit = delta.abs();
        }

        PlanChangeType changeType = determinePlanChangeType(currentPlanValue, newPlanValue);

        return new ProrationResult(delta, prorationCredit, prorationCharge, changeType);
    }

    /**
     * Cross-cycle aware proration calculation.
     * <p>
     * When {@code currentCycle == newCycle}, delegates to the existing same-cycle method.
     * When cycles differ (e.g. MONTHLY -> YEARLY), the customer receives a proportional
     * credit for the unused portion of the current cycle but pays the full price of the
     * new cycle (no proportional reduction on the new side).
     * <p>
     * Change type for cross-cycle changes is determined by comparing daily rates
     * ({@code price / cycleDays}) rather than absolute prices.
     */
    public static ProrationResult calculate(BigDecimal currentEffectivePrice,
                                             PlanCycle currentCycle,
                                             BigDecimal newEffectivePrice,
                                             PlanCycle newCycle,
                                             LocalDate periodStart,
                                             LocalDate periodEnd,
                                             LocalDate changeDate) {
        if (currentCycle == newCycle) {
            return calculate(currentEffectivePrice, newEffectivePrice, periodStart, periodEnd, changeDate);
        }

        if (periodStart == null || periodEnd == null || changeDate == null) {
            throw new IllegalArgumentException("Period dates and change date must not be null");
        }
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("Period end must be after period start");
        }
        if (changeDate.isBefore(periodStart) || changeDate.isAfter(periodEnd)) {
            throw new IllegalArgumentException("Change date must be within the billing period");
        }

        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long remainingDays = ChronoUnit.DAYS.between(changeDate, periodEnd);

        if (totalDays == 0) {
            return new ProrationResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    PlanChangeType.CYCLE_CHANGE);
        }

        BigDecimal totalDaysBd = BigDecimal.valueOf(totalDays);
        BigDecimal remainingDaysBd = BigDecimal.valueOf(remainingDays);

        // Proportional credit from the current cycle's unused portion
        BigDecimal creditUnused = currentEffectivePrice
                .multiply(remainingDaysBd)
                .divide(totalDaysBd, SCALE + 4, ROUNDING);

        // Full price of the new cycle (no proportional reduction)
        BigDecimal newCost = newEffectivePrice;

        BigDecimal delta = newCost.subtract(creditUnused)
                .setScale(SCALE, ROUNDING);

        BigDecimal prorationCredit = BigDecimal.ZERO;
        BigDecimal prorationCharge = BigDecimal.ZERO;

        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            prorationCharge = delta;
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            prorationCredit = delta.abs();
        }

        // Determine upgrade/downgrade by comparing daily rates across cycles
        BigDecimal currentDailyRate = currentEffectivePrice
                .divide(BigDecimal.valueOf(cycleDays(currentCycle)), SCALE + 4, ROUNDING);
        BigDecimal newDailyRate = newEffectivePrice
                .divide(BigDecimal.valueOf(cycleDays(newCycle)), SCALE + 4, ROUNDING);

        PlanChangeType changeType;
        int cmp = newDailyRate.compareTo(currentDailyRate);
        if (cmp > 0) {
            changeType = PlanChangeType.UPGRADE;
        } else if (cmp < 0) {
            changeType = PlanChangeType.DOWNGRADE;
        } else {
            changeType = PlanChangeType.CYCLE_CHANGE;
        }

        return new ProrationResult(delta, prorationCredit, prorationCharge, changeType);
    }

    private static int cycleDays(PlanCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> 30;
            case SEMIANNUALLY -> 180;
            case YEARLY -> 365;
        };
    }

    private static PlanChangeType determinePlanChangeType(BigDecimal currentValue, BigDecimal newValue) {
        int cmp = newValue.compareTo(currentValue);
        if (cmp > 0) return PlanChangeType.UPGRADE;
        if (cmp < 0) return PlanChangeType.DOWNGRADE;
        return PlanChangeType.SIDEGRADE;
    }

    public record ProrationResult(
            BigDecimal delta,
            BigDecimal prorationCredit,
            BigDecimal prorationCharge,
            PlanChangeType changeType
    ) {}
}
