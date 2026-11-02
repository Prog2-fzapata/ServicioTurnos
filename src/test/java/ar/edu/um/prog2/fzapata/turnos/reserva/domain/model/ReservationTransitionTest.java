package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReservationTransitionTest {

    @Test
    void theTargetIsRequired() {
        assertThatThrownBy(() -> ReservationTransition.builder().holdId("hold-1").build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toBuildsATransitionWithoutData() {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.EXPIRED);

        assertThat(transition.target()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(transition.holdId()).isNull();
        assertThat(transition.failureReason()).isNull();
        assertThat(transition.reservationId()).isNull();
    }
}
