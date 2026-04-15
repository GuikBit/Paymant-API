package br.com.holding.payments.phase8;

import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanLimitCodec;
import br.com.holding.payments.planchange.PlanLimitsValidator;
import br.com.holding.payments.planchange.PlanLimitsValidator.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 8 - PlanLimitsValidator")
class PlanLimitsValidatorTest {

    private PlanLimitsValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        validator = new PlanLimitsValidator(mapper, new PlanLimitCodec(mapper));
    }

    private Plan planWithLimits(String limitsJson) {
        Plan plan = Plan.builder().id(1L).name("Target Plan").build();
        plan.setLimits(limitsJson);
        return plan;
    }

    // ==================== SEM VIOLACOES ====================

    @Nested
    @DisplayName("Sem violacoes de limite")
    class NoViolationsTests {

        @Test
        @DisplayName("Uso dentro dos limites - retorna OK")
        void usageWithinLimits_shouldReturnOk() {
            Plan plan = planWithLimits("{\"users\": 10, \"projects\": 5}");
            Map<String, Integer> usage = Map.of("users", 8, "projects", 3);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
            assertThat(result.shouldSchedule()).isFalse();
            assertThat(result.gracePeriod()).isFalse();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("Uso exatamente no limite - retorna OK")
        void usageAtLimit_shouldReturnOk() {
            Plan plan = planWithLimits("{\"users\": 10}");
            Map<String, Integer> usage = Map.of("users", 10);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("Plano sem limites definidos - retorna OK")
        void planWithNoLimits_shouldReturnOk() {
            Plan plan = planWithLimits(null);
            Map<String, Integer> usage = Map.of("users", 100);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("Plano com limites em branco - retorna OK")
        void planWithBlankLimits_shouldReturnOk() {
            Plan plan = planWithLimits("   ");
            Map<String, Integer> usage = Map.of("users", 100);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("Uso vazio - retorna OK")
        void emptyUsage_shouldReturnOk() {
            Plan plan = planWithLimits("{\"users\": 10}");
            Map<String, Integer> usage = Collections.emptyMap();

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("Chave de uso nao existe no plano - ignora")
        void usageKeyNotInPlan_shouldBeIgnored() {
            Plan plan = planWithLimits("{\"users\": 10}");
            Map<String, Integer> usage = Map.of("storage_gb", 500);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
        }
    }

    // ==================== ESTRATEGIA BLOCK ====================

    @Nested
    @DisplayName("Estrategia BLOCK")
    class BlockStrategyTests {

        @Test
        @DisplayName("Uso excede limite - bloqueia com violacoes")
        void usageExceedsLimit_shouldBlock() {
            Plan plan = planWithLimits("{\"users\": 5, \"projects\": 3}");
            Map<String, Integer> usage = Map.of("users", 10, "projects", 2);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isFalse();
            assertThat(result.shouldSchedule()).isFalse();
            assertThat(result.gracePeriod()).isFalse();
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().get(0)).contains("users");
        }

        @Test
        @DisplayName("Multiplos limites excedidos - lista todas as violacoes")
        void multipleViolations_shouldListAll() {
            Plan plan = planWithLimits("{\"users\": 5, \"projects\": 3, \"storage_gb\": 10}");
            Map<String, Integer> usage = Map.of("users", 10, "projects", 5, "storage_gb", 20);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isFalse();
            assertThat(result.violations()).hasSize(3);
        }
    }

    // ==================== ESTRATEGIA SCHEDULE ====================

    @Nested
    @DisplayName("Estrategia SCHEDULE")
    class ScheduleStrategyTests {

        @Test
        @DisplayName("Uso excede limite - sugere agendamento")
        void usageExceedsLimit_shouldSchedule() {
            Plan plan = planWithLimits("{\"users\": 5}");
            Map<String, Integer> usage = Map.of("users", 10);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.SCHEDULE);

            assertThat(result.allowed()).isFalse();
            assertThat(result.shouldSchedule()).isTrue();
            assertThat(result.gracePeriod()).isFalse();
            assertThat(result.violations()).isNotEmpty();
        }

        @Test
        @DisplayName("Sem violacoes - retorna OK mesmo com estrategia SCHEDULE")
        void noViolations_shouldReturnOk() {
            Plan plan = planWithLimits("{\"users\": 10}");
            Map<String, Integer> usage = Map.of("users", 5);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.SCHEDULE);

            assertThat(result.allowed()).isTrue();
            assertThat(result.shouldSchedule()).isFalse();
        }
    }

    // ==================== ESTRATEGIA GRACE_PERIOD ====================

    @Nested
    @DisplayName("Estrategia GRACE_PERIOD")
    class GracePeriodStrategyTests {

        @Test
        @DisplayName("Uso excede limite - permite com grace period")
        void usageExceedsLimit_shouldAllowWithGracePeriod() {
            Plan plan = planWithLimits("{\"users\": 5}");
            Map<String, Integer> usage = Map.of("users", 10);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.GRACE_PERIOD);

            assertThat(result.allowed()).isTrue();
            assertThat(result.shouldSchedule()).isFalse();
            assertThat(result.gracePeriod()).isTrue();
            assertThat(result.violations()).isNotEmpty();
        }

        @Test
        @DisplayName("Sem violacoes - retorna OK sem grace period")
        void noViolations_shouldReturnOkWithoutGracePeriod() {
            Plan plan = planWithLimits("{\"users\": 10}");
            Map<String, Integer> usage = Map.of("users", 5);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.GRACE_PERIOD);

            assertThat(result.allowed()).isTrue();
            assertThat(result.gracePeriod()).isFalse();
        }
    }

    // ==================== EDGE CASES ====================

    @Nested
    @DisplayName("Casos de borda")
    class EdgeCaseTests {

        @Test
        @DisplayName("JSON de limites invalido - retorna OK (falha silenciosa)")
        void invalidJson_shouldReturnOk() {
            Plan plan = planWithLimits("{invalid json}");
            Map<String, Integer> usage = Map.of("users", 100);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("Uso com valor zero - nunca viola limite positivo")
        void zeroUsage_shouldNotViolate() {
            Plan plan = planWithLimits("{\"users\": 1}");
            Map<String, Integer> usage = Map.of("users", 0);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("Limite zero e uso positivo - viola")
        void zeroLimitPositiveUsage_shouldViolate() {
            Plan plan = planWithLimits("{\"users\": 0}");
            Map<String, Integer> usage = Map.of("users", 1);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.allowed()).isFalse();
            assertThat(result.violations()).hasSize(1);
        }

        @Test
        @DisplayName("Mensagem de violacao contem chave, uso atual e limite")
        void violationMessage_shouldContainDetails() {
            Plan plan = planWithLimits("{\"users\": 5}");
            Map<String, Integer> usage = Map.of("users", 10);

            ValidationResult result = validator.validate(plan, usage, DowngradeValidationStrategy.BLOCK);

            assertThat(result.violations().get(0))
                    .contains("users")
                    .contains("10")
                    .contains("5");
        }
    }
}
