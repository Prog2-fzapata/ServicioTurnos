package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

// Un evento de reserva de la cátedra, con los campos que el servicio usa. reason y expiresAt dependen del tipo y
// pueden ser null. eventType es el texto tal cual llegó, para registrarlo aunque el tipo no se conozca
public record CatedraEvent(String eventId, String eventType, String reservationProcessId, Long reservationId,
        String reason, Instant expiresAt) {

    public CatedraEvent {
        Objects.requireNonNull(eventId, "El eventId es obligatorio");
        Objects.requireNonNull(eventType, "El eventType es obligatorio");
        Objects.requireNonNull(reservationProcessId, "El reservationProcessId es obligatorio");
    }

    // El cambio de estado que provoca. Vacío si el tipo no se conoce. El eventId del pedido de teléfono es el
    // requestEventId que hay que devolver con el teléfono, por eso se guarda en el proceso
    public Optional<ReservationTransition> toTransition() {
        return CatedraEventType.fromWireName(eventType).map(type -> switch (type) {
            case ADDITIONAL_INFORMATION_REQUESTED -> ReservationTransition.builder()
                    .target(ReservationStatus.WAITING_PHONE).requestEventId(eventId).expiresAt(expiresAt).build();
            case ADDITIONAL_INFORMATION_REJECTED -> ReservationTransition.builder()
                    .target(ReservationStatus.WAITING_PHONE).expiresAt(expiresAt).build();
            case APPOINTMENT_CONFIRMED -> ReservationTransition.builder().target(ReservationStatus.CONFIRMED)
                    .reservationId(reservationId).build();
            case APPOINTMENT_PROCESS_EXPIRED -> ReservationTransition.builder().target(ReservationStatus.EXPIRED)
                    .failureReason(reason).build();
            case APPOINTMENT_PROCESS_INVALID -> ReservationTransition.builder().target(ReservationStatus.FAILED)
                    .failureReason(reason).build();
            case APPOINTMENT_CANCELLED -> ReservationTransition.builder().target(ReservationStatus.CANCELLED)
                    .reservationId(reservationId).cancellationReason(reason).build();
        });
    }
}
