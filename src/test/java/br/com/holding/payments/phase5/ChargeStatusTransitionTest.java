package br.com.holding.payments.phase5;

import br.com.holding.payments.charge.Charge;
import br.com.holding.payments.charge.ChargeStatus;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
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

@DisplayName("Fase 5 - Transicoes de estado de ChargeStatus")
class ChargeStatusTransitionTest {

    // ==================== TABELA EXAUSTIVA DE TRANSICOES VALIDAS ====================

    private static final Map<ChargeStatus, Set<ChargeStatus>> EXPECTED_TRANSITIONS = Map.of(
            ChargeStatus.PENDING, Set.of(ChargeStatus.CONFIRMED, ChargeStatus.RECEIVED, ChargeStatus.OVERDUE, ChargeStatus.CANCELED),
            ChargeStatus.CONFIRMED, Set.of(ChargeStatus.RECEIVED, ChargeStatus.REFUNDED, ChargeStatus.CHARGEBACK, ChargeStatus.CANCELED),
            ChargeStatus.RECEIVED, Set.of(ChargeStatus.REFUNDED, ChargeStatus.CHARGEBACK),
            ChargeStatus.OVERDUE, Set.of(ChargeStatus.RECEIVED, ChargeStatus.CONFIRMED, ChargeStatus.CANCELED),
            ChargeStatus.REFUNDED, Set.of(),
            ChargeStatus.CHARGEBACK, Set.of(ChargeStatus.REFUNDED),
            ChargeStatus.CANCELED, Set.of()
    );

    static Stream<Arguments> validTransitions() {
        return EXPECTED_TRANSITIONS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(target -> Arguments.of(entry.getKey(), target)));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(ChargeStatus.values())
                .flatMap(from -> Stream.of(ChargeStatus.values())
                        .filter(to -> !EXPECTED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
                        .filter(to -> from != to) // self-transition is implicitly invalid
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser permitida")
    @MethodSource("validTransitions")
    @DisplayName("Transicoes validas")
    void validTransition_shouldBeAllowed(ChargeStatus from, ChargeStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("Transicao %s -> %s deve ser valida", from, to)
                .isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser rejeitada")
    @MethodSource("invalidTransitions")
    @DisplayName("Transicoes invalidas")
    void invalidTransition_shouldBeRejected(ChargeStatus from, ChargeStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("Transicao %s -> %s deve ser invalida", from, to)
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(ChargeStatus.class)
    @DisplayName("Nenhum status pode transitar para si mesmo")
    void selfTransition_shouldBeInvalid(ChargeStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Nested
    @DisplayName("Charge.transitionTo()")
    class ChargeTransitionTest {

        @Test
        @DisplayName("Transicao valida atualiza o status da charge")
        void validTransition_shouldUpdateStatus() {
            Charge charge = Charge.builder().status(ChargeStatus.PENDING).build();

            charge.transitionTo(ChargeStatus.CONFIRMED);

            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Transicao invalida lanca IllegalStateTransitionException sem alterar status")
        void invalidTransition_shouldThrowAndNotChange() {
            Charge charge = Charge.builder().status(ChargeStatus.CANCELED).build();
            ChargeStatus originalStatus = charge.getStatus();

            assertThatThrownBy(() -> charge.transitionTo(ChargeStatus.RECEIVED))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining("CANCELED")
                    .hasMessageContaining("RECEIVED");

            assertThat(charge.getStatus()).isEqualTo(originalStatus);
        }

        @Test
        @DisplayName("PENDING -> CONFIRMED -> RECEIVED -> REFUNDED (fluxo completo PIX)")
        void fullPixFlow_shouldTransitionCorrectly() {
            Charge charge = Charge.builder().status(ChargeStatus.PENDING).build();

            charge.transitionTo(ChargeStatus.CONFIRMED);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);

            charge.transitionTo(ChargeStatus.RECEIVED);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.RECEIVED);

            charge.transitionTo(ChargeStatus.REFUNDED);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REFUNDED);
        }

        @Test
        @DisplayName("PENDING -> OVERDUE -> RECEIVED (fluxo boleto atrasado pago)")
        void overdueToReceived_shouldWork() {
            Charge charge = Charge.builder().status(ChargeStatus.PENDING).build();

            charge.transitionTo(ChargeStatus.OVERDUE);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.OVERDUE);

            charge.transitionTo(ChargeStatus.RECEIVED);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.RECEIVED);
        }

        @Test
        @DisplayName("CONFIRMED -> CHARGEBACK -> REFUNDED (fluxo chargeback)")
        void chargebackFlow_shouldWork() {
            Charge charge = Charge.builder().status(ChargeStatus.CONFIRMED).build();

            charge.transitionTo(ChargeStatus.CHARGEBACK);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CHARGEBACK);

            charge.transitionTo(ChargeStatus.REFUNDED);
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REFUNDED);
        }

        @Test
        @DisplayName("REFUNDED eh estado terminal - nenhuma transicao possivel")
        void refunded_shouldBeTerminal() {
            Charge charge = Charge.builder().status(ChargeStatus.REFUNDED).build();

            for (ChargeStatus target : ChargeStatus.values()) {
                if (target == ChargeStatus.REFUNDED) continue;
                assertThatThrownBy(() -> charge.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("CANCELED eh estado terminal - nenhuma transicao possivel")
        void canceled_shouldBeTerminal() {
            Charge charge = Charge.builder().status(ChargeStatus.CANCELED).build();

            for (ChargeStatus target : ChargeStatus.values()) {
                if (target == ChargeStatus.CANCELED) continue;
                assertThatThrownBy(() -> charge.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }
    }
}
