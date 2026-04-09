package br.com.holding.payments.phase8;

import br.com.holding.payments.planchange.PlanChangeType;
import br.com.holding.payments.planchange.ProrationCalculator;
import br.com.holding.payments.planchange.ProrationCalculator.ProrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fase 8 - ProrationCalculator (testes unitarios)")
class ProrationCalculatorTest {

    private static final BigDecimal PLAN_100 = new BigDecimal("100.00");
    private static final BigDecimal PLAN_200 = new BigDecimal("200.00");
    private static final BigDecimal PLAN_50 = new BigDecimal("50.00");
    private static final BigDecimal PLAN_0 = BigDecimal.ZERO;

    // ==================== UPGRADE ====================

    @Nested
    @DisplayName("Upgrade (plano novo mais caro)")
    class UpgradeTests {

        @Test
        @DisplayName("Upgrade no meio do ciclo de 30 dias (dia 15) - delta positivo")
        void upgrade_midCycle_shouldReturnPositiveDelta() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16); // 15 dias restantes de 30

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            assertThat(result.delta()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.prorationCharge()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.prorationCredit()).isEqualByComparingTo(BigDecimal.ZERO);
            // delta = (200 * 15/30) - (100 * 15/30) = 100 - 50 = 50
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(result.prorationCharge()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Upgrade no primeiro dia do ciclo - cobra proporcional ao ciclo inteiro")
        void upgrade_firstDay_shouldChargeFullProration() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = start; // 30 dias restantes de 30

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            // delta = (200 * 30/30) - (100 * 30/30) = 200 - 100 = 100
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(result.prorationCharge()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Upgrade no ultimo dia do ciclo - delta zero (0 dias restantes)")
        void upgrade_lastDay_shouldReturnZeroDelta() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = end; // 0 dias restantes

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.delta()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.prorationCharge()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.prorationCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Upgrade com valores grandes")
        void upgrade_largeValues_shouldCalculateCorrectly() {
            BigDecimal current = new BigDecimal("9999.99");
            BigDecimal newVal = new BigDecimal("19999.99");
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 2, 1); // 31 dias
            LocalDate change = LocalDate.of(2026, 1, 16); // 16 dias restantes

            ProrationResult result = ProrationCalculator.calculate(current, newVal, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            assertThat(result.delta()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.prorationCharge()).isEqualTo(result.delta());
        }

        @Test
        @DisplayName("Upgrade de plano gratuito para pago")
        void upgrade_freeToPayd_shouldChargeFullNewPlanProration() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_0, PLAN_100, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            // delta = (100 * 15/30) - (0 * 15/30) = 50
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("50.00"));
        }
    }

    // ==================== DOWNGRADE ====================

    @Nested
    @DisplayName("Downgrade (plano novo mais barato)")
    class DowngradeTests {

        @Test
        @DisplayName("Downgrade no meio do ciclo - delta negativo, gera credito")
        void downgrade_midCycle_shouldReturnNegativeDeltaAndCredit() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_200, PLAN_100, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.DOWNGRADE);
            assertThat(result.delta()).isLessThan(BigDecimal.ZERO);
            assertThat(result.prorationCredit()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.prorationCharge()).isEqualByComparingTo(BigDecimal.ZERO);
            // delta = (100 * 15/30) - (200 * 15/30) = 50 - 100 = -50
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("-50.00"));
            assertThat(result.prorationCredit()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Downgrade no primeiro dia - credito maximo")
        void downgrade_firstDay_shouldReturnMaxCredit() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = start;

            ProrationResult result = ProrationCalculator.calculate(PLAN_200, PLAN_100, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.DOWNGRADE);
            // delta = (100 * 30/30) - (200 * 30/30) = 100 - 200 = -100
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("-100.00"));
            assertThat(result.prorationCredit()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Downgrade no ultimo dia - delta zero")
        void downgrade_lastDay_shouldReturnZero() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = end;

            ProrationResult result = ProrationCalculator.calculate(PLAN_200, PLAN_100, start, end, change);

            assertThat(result.delta()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Downgrade de plano pago para gratuito")
        void downgrade_paidToFree_shouldCreditFullRemaining() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_0, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.DOWNGRADE);
            // delta = (0 * 15/30) - (100 * 15/30) = 0 - 50 = -50
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("-50.00"));
            assertThat(result.prorationCredit()).isEqualByComparingTo(new BigDecimal("50.00"));
        }
    }

    // ==================== SIDEGRADE ====================

    @Nested
    @DisplayName("Sidegrade (mesmo valor)")
    class SidegradeTests {

        @Test
        @DisplayName("Sidegrade - delta zero, sem cobranca nem credito")
        void sidegrade_shouldReturnZeroDelta() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_100, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.SIDEGRADE);
            assertThat(result.delta()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.prorationCharge()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.prorationCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Sidegrade com planos gratuitos")
        void sidegrade_freePlans_shouldReturnZero() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_0, PLAN_0, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.SIDEGRADE);
            assertThat(result.delta()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== PERIODOS VARIADOS ====================

    @Nested
    @DisplayName("Periodos de ciclo variados")
    class VariousPeriodTests {

        @Test
        @DisplayName("Ciclo semanal (7 dias)")
        void weeklyCycle_shouldCalculateCorrectly() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 8);   // 7 dias
            LocalDate change = LocalDate.of(2026, 4, 4); // 4 dias restantes

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            // delta = (200 * 4/7) - (100 * 4/7) = 114.285... - 57.142... = 57.14
            BigDecimal expected = new BigDecimal("200").multiply(new BigDecimal("4"))
                    .divide(new BigDecimal("7"), 6, RoundingMode.HALF_EVEN)
                    .subtract(new BigDecimal("100").multiply(new BigDecimal("4"))
                            .divide(new BigDecimal("7"), 6, RoundingMode.HALF_EVEN))
                    .setScale(2, RoundingMode.HALF_EVEN);
            assertThat(result.delta()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Ciclo anual (365 dias)")
        void annualCycle_shouldCalculateCorrectly() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2027, 1, 1);     // 365 dias
            LocalDate change = LocalDate.of(2026, 7, 1);  // ~184 dias restantes

            BigDecimal annualCurrent = new BigDecimal("1200.00");
            BigDecimal annualNew = new BigDecimal("2400.00");

            ProrationResult result = ProrationCalculator.calculate(annualCurrent, annualNew, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            assertThat(result.delta()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Ciclo de 1 dia (totalDays = 1)")
        void singleDayCycle_shouldCalculateCorrectly() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 2); // 1 dia
            LocalDate change = start; // 1 dia restante

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            // delta = (200 * 1/1) - (100 * 1/1) = 100
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Fevereiro (28 dias)")
        void februaryCycle_shouldCalculateCorrectly() {
            LocalDate start = LocalDate.of(2026, 2, 1);
            LocalDate end = LocalDate.of(2026, 3, 1); // 28 dias
            LocalDate change = LocalDate.of(2026, 2, 15); // 14 dias restantes

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            // delta = (200 * 14/28) - (100 * 14/28) = 100 - 50 = 50
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Mes de 31 dias")
        void thirtyOneDayMonth_shouldCalculateCorrectly() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 2, 1); // 31 dias
            LocalDate change = LocalDate.of(2026, 1, 1);

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.changeType()).isEqualTo(PlanChangeType.UPGRADE);
            // delta = (200 * 31/31) - (100 * 31/31) = 100
            assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }

    // ==================== ESCALA E ARREDONDAMENTO ====================

    @Nested
    @DisplayName("Escala e arredondamento (HALF_EVEN, 2 casas)")
    class RoundingTests {

        @Test
        @DisplayName("Divisao com resultado periodico - arredonda para 2 casas")
        void periodicDivision_shouldRoundTo2Decimals() {
            BigDecimal current = new BigDecimal("100.00");
            BigDecimal newVal = new BigDecimal("133.33");
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 10); // 21 dias restantes de 30

            ProrationResult result = ProrationCalculator.calculate(current, newVal, start, end, change);

            assertThat(result.delta().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Delta resultado com centavos fracionarios - escala 2")
        void fractionalCents_shouldHaveScale2() {
            BigDecimal current = new BigDecimal("99.99");
            BigDecimal newVal = new BigDecimal("149.99");
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 11);

            ProrationResult result = ProrationCalculator.calculate(current, newVal, start, end, change);

            assertThat(result.delta().scale()).isEqualTo(2);
            assertThat(result.prorationCharge().scale()).isLessThanOrEqualTo(2);
        }
    }

    // ==================== VALIDACOES ====================

    @Nested
    @DisplayName("Validacoes de entrada")
    class ValidationTests {

        @Test
        @DisplayName("periodStart nulo lanca IllegalArgumentException")
        void nullPeriodStart_shouldThrow() {
            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, null, LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("periodEnd nulo lanca IllegalArgumentException")
        void nullPeriodEnd_shouldThrow() {
            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, LocalDate.now(), null, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("changeDate nulo lanca IllegalArgumentException")
        void nullChangeDate_shouldThrow() {
            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, LocalDate.now(), LocalDate.now().plusDays(30), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("periodEnd antes de periodStart lanca IllegalArgumentException")
        void endBeforeStart_shouldThrow() {
            LocalDate start = LocalDate.of(2026, 5, 1);
            LocalDate end = LocalDate.of(2026, 4, 1);
            LocalDate change = LocalDate.of(2026, 4, 15);

            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("after");
        }

        @Test
        @DisplayName("periodEnd igual a periodStart lanca IllegalArgumentException")
        void endEqualsStart_shouldThrow() {
            LocalDate date = LocalDate.of(2026, 4, 1);

            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, date, date, date))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("changeDate antes do periodStart lanca IllegalArgumentException")
        void changeDateBeforeStart_shouldThrow() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 3, 15);

            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("within");
        }

        @Test
        @DisplayName("changeDate depois do periodEnd lanca IllegalArgumentException")
        void changeDateAfterEnd_shouldThrow() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 5, 15);

            assertThatThrownBy(() ->
                    ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("within");
        }
    }

    // ==================== CONSISTENCIA ====================

    @Nested
    @DisplayName("Propriedades de consistencia")
    class ConsistencyTests {

        @Test
        @DisplayName("prorationCredit e prorationCharge nunca sao ambos positivos")
        void creditAndCharge_neverBothPositive() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            for (BigDecimal[] pair : new BigDecimal[][] {
                    {PLAN_100, PLAN_200}, {PLAN_200, PLAN_100}, {PLAN_100, PLAN_100},
                    {PLAN_0, PLAN_100}, {PLAN_100, PLAN_0}, {PLAN_0, PLAN_0}
            }) {
                ProrationResult result = ProrationCalculator.calculate(pair[0], pair[1], start, end, change);

                boolean bothPositive = result.prorationCredit().compareTo(BigDecimal.ZERO) > 0
                        && result.prorationCharge().compareTo(BigDecimal.ZERO) > 0;
                assertThat(bothPositive)
                        .as("credit=%s, charge=%s para %s->%s", result.prorationCredit(), result.prorationCharge(), pair[0], pair[1])
                        .isFalse();
            }
        }

        @Test
        @DisplayName("prorationCharge == delta quando delta > 0")
        void positiveDeleta_chargeEqualsDelta() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_100, PLAN_200, start, end, change);

            assertThat(result.delta()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.prorationCharge()).isEqualByComparingTo(result.delta());
        }

        @Test
        @DisplayName("prorationCredit == abs(delta) quando delta < 0")
        void negativeDelta_creditEqualsAbsDelta() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);
            LocalDate change = LocalDate.of(2026, 4, 16);

            ProrationResult result = ProrationCalculator.calculate(PLAN_200, PLAN_100, start, end, change);

            assertThat(result.delta()).isLessThan(BigDecimal.ZERO);
            assertThat(result.prorationCredit()).isEqualByComparingTo(result.delta().abs());
        }
    }

    // ==================== PARAMETRIZED ====================

    static Stream<Arguments> upgradeDowngradeCombinations() {
        return Stream.of(
                // currentValue, newValue, remainingDays, totalDays, expectedType
                Arguments.of("100.00", "200.00", 15, 30, PlanChangeType.UPGRADE),
                Arguments.of("200.00", "100.00", 15, 30, PlanChangeType.DOWNGRADE),
                Arguments.of("100.00", "100.00", 15, 30, PlanChangeType.SIDEGRADE),
                Arguments.of("50.00", "150.00", 10, 30, PlanChangeType.UPGRADE),
                Arguments.of("300.00", "100.00", 20, 30, PlanChangeType.DOWNGRADE),
                Arguments.of("0.00", "100.00", 15, 30, PlanChangeType.UPGRADE),
                Arguments.of("100.00", "0.00", 15, 30, PlanChangeType.DOWNGRADE),
                Arguments.of("0.00", "0.00", 15, 30, PlanChangeType.SIDEGRADE)
        );
    }

    @ParameterizedTest(name = "{0} -> {1}, {2}/{3} dias restantes = {4}")
    @MethodSource("upgradeDowngradeCombinations")
    @DisplayName("Tipo de mudanca detectado corretamente")
    void changeType_shouldBeDetectedCorrectly(String currentVal, String newVal,
                                               int remainingDays, int totalDays,
                                               PlanChangeType expectedType) {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = start.plusDays(totalDays);
        LocalDate change = end.minusDays(remainingDays);

        ProrationResult result = ProrationCalculator.calculate(
                new BigDecimal(currentVal), new BigDecimal(newVal), start, end, change);

        assertThat(result.changeType()).isEqualTo(expectedType);
    }
}
