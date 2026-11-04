package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import java.time.Instant;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

// El data del evento reservation-updated: un resumen; el detalle completo se consulta aparte
public record ReservationUpdate(Long id, ReservationStatus status, Instant expiresAt, boolean canSubmitPhone,
        Instant updatedAt) {
}
