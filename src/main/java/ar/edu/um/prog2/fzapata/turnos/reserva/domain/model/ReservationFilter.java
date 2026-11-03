package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.Objects;

// Las reservas de un usuario. status null no filtra por estado. hideDiscarded deja afuera las EXPIRED y FAILED
// (ReservationStatus.DISCARDED); si se pide un status, ese estado se devuelve tal cual
public record ReservationFilter(String userId, ReservationStatus status, boolean hideDiscarded) {

    public ReservationFilter {
        Objects.requireNonNull(userId, "El usuario es obligatorio");
    }
}
