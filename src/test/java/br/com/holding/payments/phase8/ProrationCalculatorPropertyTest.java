package br.com.holding.payments.phase8;

import br.com.holding.payments.planchange.PlanChangeType;
import br.com.holding.payments.planchange.ProrationCalculator;
import br.com.holding.payments.planchange.ProrationCalculator.ProrationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Label("Fase 8 - ProrationCalculator (property-based tests com jqwik)")
class ProrationCalculatorPropertyTest {

    @Provide
    Arbitrary<BigDecimal> planValues() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("99999.99"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> cycleDays() {
        return Arbitraries.integers().between(1, 365);
    }

    @Property(tries = 500)
    @Label("delta = prorationCharge - prorationCredit (sempre)")
    void deltaEqualsChargeMinusCredit(
            @ForAll("planValues") BigDecimal currentValue,
            @ForAll("planValues") BigDecimal newValue,
            @ForAll("cycleDays") int totalDays) {

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        // changeDate no meio do ciclo
        int changeDayOffset = totalDays / 2;
        if (changeDayOffset >= totalDays) changeDayOffset = totalDays - 1;
        LocalDate change = start.plusDays(changeDayOffset);

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        // delta = charge - credit
        BigDecimal expectedDelta = result.prorationCharge().subtract(result.prorationCredit());
        assertThat(result.delta()).isEqualByComparingTo(expectedDelta);
    }

    @Property(tries = 500)
    @Label("prorationCredit e prorationCharge nunca sao ambos positivos")
    void creditAndChargeNeverBothPositive(
            @ForAll("planValues") BigDecimal currentValue,
            @ForAll("planValues") BigDecimal newValue,
            @ForAll("cycleDays") int totalDays) {

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        int changeDayOffset = Math.max(0, Math.min(totalDays, totalDays / 3));
        LocalDate change = start.plusDays(changeDayOffset);

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        boolean bothPositive = result.prorationCredit().signum() > 0 && result.prorationCharge().signum() > 0;
        assertThat(bothPositive).isFalse();
    }

    @Property(tries = 500)
    @Label("prorationCredit e prorationCharge sao sempre >= 0")
    void creditAndChargeAlwaysNonNegative(
            @ForAll("planValues") BigDecimal currentValue,
            @ForAll("planValues") BigDecimal newValue,
            @ForAll("cycleDays") int totalDays) {

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        int changeDayOffset = totalDays / 2;
        if (changeDayOffset >= totalDays) changeDayOffset = totalDays - 1;
        LocalDate change = start.plusDays(changeDayOffset);

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        assertThat(result.prorationCredit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.prorationCharge()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Property(tries = 500)
    @Label("newValue > currentValue -> UPGRADE")
    void higherNewValue_isUpgrade(
            @ForAll("planValues") BigDecimal currentValue,
            @ForAll("cycleDays") int totalDays) {

        BigDecimal newValue = currentValue.add(new BigDecimal("0.01"));
        if (newValue.compareTo(new BigDecimal("99999.99")) > 0) return;

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        LocalDate change = start;

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
    }

    @Property(tries = 500)
    @Label("newValue < currentValue -> DOWNGRADE")
    void lowerNewValue_isDowngrade(
            @ForAll("planValues") BigDecimal newValue,
            @ForAll("cycleDays") int totalDays) {

        BigDecimal currentValue = newValue.add(new BigDecimal("0.01"));
        if (currentValue.compareTo(new BigDecimal("99999.99")) > 0) return;

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        LocalDate change = start;

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        assertThat(result.changeType()).isEqualTo(PlanChangeType.DOWNGRADE);
    }

    @Property(tries = 200)
    @Label("mesmos valores -> SIDEGRADE com delta zero")
    void sameValues_isSidegradeWithZeroDelta(
            @ForAll("planValues") BigDecimal value,
            @ForAll("cycleDays") int totalDays) {

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        int changeDayOffset = totalDays / 2;
        if (changeDayOffset >= totalDays) changeDayOffset = totalDays - 1;
        LocalDate change = start.plusDays(changeDayOffset);

        ProrationResult result = ProrationCalculator.calculate(value, value, start, end, change);

        assertThat(result.changeType()).isEqualTo(PlanChangeType.SIDEGRADE);
        assertThat(result.delta()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.prorationCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.prorationCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Property(tries = 300)
    @Label("mudanca no ultimo dia (0 dias restantes) -> delta sempre zero")
    void changeOnLastDay_deltaAlwaysZero(
            @ForAll("planValues") BigDecimal currentValue,
            @ForAll("planValues") BigDecimal newValue,
            @ForAll("cycleDays") int totalDays) {

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        LocalDate change = end; // ultimo dia

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        assertThat(result.delta()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.prorationCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.prorationCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Property(tries = 300)
    @Label("delta sempre tem escala <= 2")
    void deltaAlwaysHasScale2(
            @ForAll("planValues") BigDecimal currentValue,
            @ForAll("planValues") BigDecimal newValue,
            @ForAll("cycleDays") int totalDays) {

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        int changeDayOffset = totalDays / 2;
        if (changeDayOffset >= totalDays) changeDayOffset = totalDays - 1;
        LocalDate change = start.plusDays(changeDayOffset);

        ProrationResult result = ProrationCalculator.calculate(currentValue, newValue, start, end, change);

        assertThat(result.delta().scale()).isLessThanOrEqualTo(2);
    }

    @Property(tries = 300)
    @Label("upgrade e downgrade sao simetricos: swap(current,new) inverte sinal do delta")
    void upgradeDowngradeSymmetry(
            @ForAll("planValues") BigDecimal valueA,
            @ForAll("planValues") BigDecimal valueB,
            @ForAll("cycleDays") int totalDays) {

        if (valueA.compareTo(valueB) == 0) return;

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(totalDays);
        int changeDayOffset = totalDays / 2;
        if (changeDayOffset >= totalDays) changeDayOffset = totalDays - 1;
        LocalDate change = start.plusDays(changeDayOffset);

        ProrationResult resultAB = ProrationCalculator.calculate(valueA, valueB, start, end, change);
        ProrationResult resultBA = ProrationCalculator.calculate(valueB, valueA, start, end, change);

        // delta(A->B) = -delta(B->A)
        assertThat(resultAB.delta().add(resultBA.delta())).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
