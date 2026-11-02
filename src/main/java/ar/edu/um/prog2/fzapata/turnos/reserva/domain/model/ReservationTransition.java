package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

// Un cambio de estado pedido y los datos que lo acompañan. Solo los datos presentes se copian al proceso: un null
// nunca borra lo que ya había
@Builder
public record ReservationTransition(ReservationStatus target, String holdId, String reservationProcessId,
        Instant expiresAt, String patientPhone, String requestEventId, Long reservationId, String failureReason,
        String cancellationReason) {

    public ReservationTransition {
        Objects.requireNonNull(target, "El estado destino es obligatorio");
    }

    public static ReservationTransition to(ReservationStatus target) {
        return ReservationTransition.builder().target(target).build();
    }
}
