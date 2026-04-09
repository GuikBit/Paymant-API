package br.com.holding.payments.phase7;

import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionStatus;
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

@DisplayName("Fase 7 - Transicoes de estado de SubscriptionStatus")
class SubscriptionStatusTransitionTest {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> EXPECTED_TRANSITIONS = Map.of(
            SubscriptionStatus.ACTIVE, Set.of(SubscriptionStatus.PAUSED, SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELED, SubscriptionStatus.EXPIRED),
            SubscriptionStatus.PAUSED, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELED),
            SubscriptionStatus.SUSPENDED, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELED),
            SubscriptionStatus.CANCELED, Set.of(),
            SubscriptionStatus.EXPIRED, Set.of()
    );

    static Stream<Arguments> validTransitions() {
        return EXPECTED_TRANSITIONS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(target -> Arguments.of(entry.getKey(), target)));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(SubscriptionStatus.values())
                .flatMap(from -> Stream.of(SubscriptionStatus.values())
                        .filter(to -> !EXPECTED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
                        .filter(to -> from != to)
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser permitida")
    @MethodSource("validTransitions")
    @DisplayName("Transicoes validas")
    void validTransition_shouldBeAllowed(SubscriptionStatus from, SubscriptionStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} deve ser rejeitada")
    @MethodSource("invalidTransitions")
    @DisplayName("Transicoes invalidas")
    void invalidTransition_shouldBeRejected(SubscriptionStatus from, SubscriptionStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(SubscriptionStatus.class)
    @DisplayName("Nenhum status pode transitar para si mesmo")
    void selfTransition_shouldBeInvalid(SubscriptionStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Nested
    @DisplayName("Subscription.transitionTo()")
    class SubscriptionTransition {

        @Test
        @DisplayName("ACTIVE -> PAUSED funciona")
        void activeToPaused_shouldWork() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.ACTIVE).build();
            sub.transitionTo(SubscriptionStatus.PAUSED);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        }

        @Test
        @DisplayName("PAUSED -> ACTIVE funciona (resume)")
        void pausedToActive_shouldWork() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.PAUSED).build();
            sub.transitionTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE -> SUSPENDED funciona (inadimplencia)")
        void activeToSuspended_shouldWork() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.ACTIVE).build();
            sub.transitionTo(SubscriptionStatus.SUSPENDED);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        }

        @Test
        @DisplayName("SUSPENDED -> ACTIVE funciona (regularizacao)")
        void suspendedToActive_shouldWork() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.SUSPENDED).build();
            sub.transitionTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("CANCELED eh terminal - transicao lanca excecao")
        void canceled_shouldBeTerminal() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.CANCELED).build();

            for (SubscriptionStatus target : SubscriptionStatus.values()) {
                if (target == SubscriptionStatus.CANCELED) continue;
                assertThatThrownBy(() -> sub.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("EXPIRED eh terminal - transicao lanca excecao")
        void expired_shouldBeTerminal() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.EXPIRED).build();

            for (SubscriptionStatus target : SubscriptionStatus.values()) {
                if (target == SubscriptionStatus.EXPIRED) continue;
                assertThatThrownBy(() -> sub.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("PAUSED -> SUSPENDED eh invalido")
        void pausedToSuspended_shouldFail() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.PAUSED).build();

            assertThatThrownBy(() -> sub.transitionTo(SubscriptionStatus.SUSPENDED))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("Transicao invalida nao altera status")
        void invalidTransition_shouldNotChangeStatus() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.CANCELED).build();

            try {
                sub.transitionTo(SubscriptionStatus.ACTIVE);
            } catch (IllegalStateTransitionException e) {
                // expected
            }

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        }
    }
}
