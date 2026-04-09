package br.com.holding.payments.phase8;

import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.planchange.PlanChangeStatus;
import br.com.holding.payments.planchange.SubscriptionPlanChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fase 8 - Transicoes de estado de PlanChangeStatus")
class PlanChangeStatusTransitionTest {

    private static final Map<PlanChangeStatus, Set<PlanChangeStatus>> EXPECTED_TRANSITIONS = Map.of(
            PlanChangeStatus.PENDING, Set.of(PlanChangeStatus.AWAITING_PAYMENT, PlanChangeStatus.EFFECTIVE, PlanChangeStatus.SCHEDULED, PlanChangeStatus.FAILED, PlanChangeStatus.CANCELED),
            PlanChangeStatus.AWAITING_PAYMENT, Set.of(PlanChangeStatus.EFFECTIVE, PlanChangeStatus.FAILED, PlanChangeStatus.CANCELED),
            PlanChangeStatus.SCHEDULED, Set.of(PlanChangeStatus.EFFECTIVE, PlanChangeStatus.FAILED, PlanChangeStatus.CANCELED),
            PlanChangeStatus.EFFECTIVE, Set.of(),
            PlanChangeStatus.FAILED, Set.of(),
            PlanChangeStatus.CANCELED, Set.of()
    );

    static Stream<Arguments> validTransitions() {
        return EXPECTED_TRANSITIONS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(target -> Arguments.of(entry.getKey(), target)));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(PlanChangeStatus.values())
                .flatMap(from -> Stream.of(PlanChangeStatus.values())
                        .filter(to -> !EXPECTED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
                        .filter(to -> from != to)
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser permitida")
    @MethodSource("validTransitions")
    @DisplayName("Transicoes validas")
    void validTransition_shouldBeAllowed(PlanChangeStatus from, PlanChangeStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser rejeitada")
    @MethodSource("invalidTransitions")
    @DisplayName("Transicoes invalidas")
    void invalidTransition_shouldBeRejected(PlanChangeStatus from, PlanChangeStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PlanChangeStatus.class)
    @DisplayName("Nenhum status pode transitar para si mesmo")
    void selfTransition_shouldBeInvalid(PlanChangeStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Nested
    @DisplayName("SubscriptionPlanChange.transitionTo()")
    class EntityTransition {

        @Test
        @DisplayName("PENDING -> AWAITING_PAYMENT funciona")
        void pendingToAwaitingPayment_shouldWork() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.PENDING).build();

            pc.transitionTo(PlanChangeStatus.AWAITING_PAYMENT);

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.AWAITING_PAYMENT);
        }

        @Test
        @DisplayName("PENDING -> EFFECTIVE funciona")
        void pendingToEffective_shouldWork() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.PENDING).build();

            pc.transitionTo(PlanChangeStatus.EFFECTIVE);

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
        }

        @Test
        @DisplayName("PENDING -> SCHEDULED funciona")
        void pendingToScheduled_shouldWork() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.PENDING).build();

            pc.transitionTo(PlanChangeStatus.SCHEDULED);

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.SCHEDULED);
        }

        @Test
        @DisplayName("AWAITING_PAYMENT -> EFFECTIVE funciona (pagamento confirmado)")
        void awaitingPaymentToEffective_shouldWork() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.AWAITING_PAYMENT).build();

            pc.transitionTo(PlanChangeStatus.EFFECTIVE);

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
        }

        @Test
        @DisplayName("SCHEDULED -> EFFECTIVE funciona (fim do ciclo)")
        void scheduledToEffective_shouldWork() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.SCHEDULED).build();

            pc.transitionTo(PlanChangeStatus.EFFECTIVE);

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
        }

        @Test
        @DisplayName("EFFECTIVE eh terminal - nenhuma transicao possivel")
        void effective_shouldBeTerminal() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.EFFECTIVE).build();

            for (PlanChangeStatus target : PlanChangeStatus.values()) {
                if (target == PlanChangeStatus.EFFECTIVE) continue;
                assertThatThrownBy(() -> pc.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("FAILED eh terminal - nenhuma transicao possivel")
        void failed_shouldBeTerminal() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.FAILED).build();

            for (PlanChangeStatus target : PlanChangeStatus.values()) {
                if (target == PlanChangeStatus.FAILED) continue;
                assertThatThrownBy(() -> pc.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("CANCELED eh terminal - nenhuma transicao possivel")
        void canceled_shouldBeTerminal() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.CANCELED).build();

            for (PlanChangeStatus target : PlanChangeStatus.values()) {
                if (target == PlanChangeStatus.CANCELED) continue;
                assertThatThrownBy(() -> pc.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("Transicao invalida nao altera o status")
        void invalidTransition_shouldNotChangeStatus() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.EFFECTIVE).build();

            try {
                pc.transitionTo(PlanChangeStatus.PENDING);
            } catch (IllegalStateTransitionException e) {
                // expected
            }

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
        }
    }

    @Nested
    @DisplayName("markEffective() e markFailed()")
    class ConvenienceMethods {

        @Test
        @DisplayName("markEffective() transita para EFFECTIVE e seta effectiveAt")
        void markEffective_shouldSetStatusAndTimestamp() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.PENDING).build();

            pc.markEffective();

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.EFFECTIVE);
            assertThat(pc.getEffectiveAt()).isNotNull();
        }

        @Test
        @DisplayName("markFailed() transita para FAILED e seta failureReason")
        void markFailed_shouldSetStatusAndReason() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.PENDING).build();

            pc.markFailed("Erro de processamento");

            assertThat(pc.getStatus()).isEqualTo(PlanChangeStatus.FAILED);
            assertThat(pc.getFailureReason()).isEqualTo("Erro de processamento");
        }

        @Test
        @DisplayName("markEffective() de EFFECTIVE lanca excecao")
        void markEffective_fromTerminal_shouldThrow() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.EFFECTIVE).build();

            assertThatThrownBy(pc::markEffective)
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("markFailed() de FAILED lanca excecao")
        void markFailed_fromTerminal_shouldThrow() {
            SubscriptionPlanChange pc = SubscriptionPlanChange.builder()
                    .status(PlanChangeStatus.FAILED).build();

            assertThatThrownBy(() -> pc.markFailed("outro erro"))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }
}
