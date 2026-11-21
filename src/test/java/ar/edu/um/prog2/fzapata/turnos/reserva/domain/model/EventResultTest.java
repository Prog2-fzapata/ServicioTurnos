package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EventResultTest {

    private static TransitionResult transition() {
        return new TransitionResult(TransitionOutcome.APPLIED, ReservationStatus.HELD, ReservationStatus.CONFIRMED,
                ReservationProcess.builder().build());
    }

    @Test
    void aProcessedEventCarriesItsTransition() {
        TransitionResult transition = transition();

        EventResult result = EventResult.processed(transition);

        assertThat(result.outcome()).isEqualTo(EventOutcome.PROCESSED);
        assertThat(result.transition()).isSameAs(transition);
    }

    @ParameterizedTest
    @EnumSource(value = EventOutcome.class, names = "PROCESSED", mode = EnumSource.Mode.EXCLUDE)
    void anIgnoredEventHasNoTransition(EventOutcome outcome) {
        EventResult result = EventResult.ignored(outcome);

        assertThat(result.outcome()).isEqualTo(outcome);
        assertThat(result.transition()).isNull();
    }

    @Test
    void anIgnoredEventCannotBeProcessed() {
        assertThatThrownBy(() -> EventResult.ignored(EventOutcome.PROCESSED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PROCESSED");
    }

    @Test
    void aProcessedEventNeedsItsTransition() {
        assertThatThrownBy(() -> EventResult.processed(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EventOutcome.class, names = "PROCESSED", mode = EnumSource.Mode.EXCLUDE)
    void anEventThatWasNotProcessedCannotCarryATransition(EventOutcome outcome) {
        assertThatThrownBy(() -> new EventResult(outcome, transition()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(outcome.name());
    }

    @Test
    void theOutcomeIsRequired() {
        assertThatThrownBy(() -> new EventResult(null, null)).isInstanceOf(NullPointerException.class);
    }
}
