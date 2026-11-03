package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatedraEventTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-10T14:44:50Z");

    private static CatedraEvent event(String eventType) {
        return new CatedraEvent("evento-1", eventType, "process-a92b47d0", 8251L, "motivo", EXPIRES_AT);
    }

    private static ReservationTransition transitionOf(String eventType) {
        return event(eventType).toTransition().orElseThrow();
    }

    // El eventId del pedido es el requestEventId que se devuelve con el teléfono
    @Test
    void thePhoneRequestWaitsForThePhoneAndKeepsItsOwnEventIdAsTheRequestEventId() {
        ReservationTransition transition = transitionOf("AdditionalInformationRequested");

        assertThat(transition.target()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(transition.requestEventId()).isEqualTo("evento-1");
        assertThat(transition.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(transition.reservationId()).isNull();
    }

    @Test
    void aRejectedPhoneGoesBackToWaitingForThePhoneWithoutTouchingTheRequestEventId() {
        ReservationTransition transition = transitionOf("AdditionalInformationRejected");

        assertThat(transition.target()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(transition.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(transition.requestEventId()).isNull();
    }

    @Test
    void aConfirmationKeepsTheReservationIdOfTheCatedra() {
        ReservationTransition transition = transitionOf("AppointmentConfirmed");

        assertThat(transition.target()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(transition.reservationId()).isEqualTo(8251L);
        assertThat(transition.requestEventId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"AppointmentProcessExpired, EXPIRED", "AppointmentProcessInvalid, FAILED"})
    void anExpiredOrInvalidProcessEndsWithTheReasonAsFailureReason(String eventType, ReservationStatus target) {
        ReservationTransition transition = transitionOf(eventType);

        assertThat(transition.target()).isEqualTo(target);
        assertThat(transition.failureReason()).isEqualTo("motivo");
        assertThat(transition.cancellationReason()).isNull();
    }

    @Test
    void aCancellationKeepsTheReasonAndTheReservationId() {
        ReservationTransition transition = transitionOf("AppointmentCancelled");

        assertThat(transition.target()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(transition.cancellationReason()).isEqualTo("motivo");
        assertThat(transition.reservationId()).isEqualTo(8251L);
        assertThat(transition.failureReason()).isNull();
    }

    @Test
    void anUnknownTypeHasNoTransition() {
        assertThat(event("AlgoNuevoDeLaCatedra").toTransition()).isEqualTo(Optional.empty());
    }

    // El nombre del tipo es exacto: distingue mayúsculas
    @Test
    void theTypeNameIsCaseSensitive() {
        assertThat(event("appointmentconfirmed").toTransition()).isEmpty();
    }

    @Test
    void theIdentifiersAreRequired() {
        assertThatThrownBy(() -> new CatedraEvent(null, "AppointmentConfirmed", "p", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CatedraEvent("e", null, "p", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CatedraEvent("e", "AppointmentConfirmed", null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
