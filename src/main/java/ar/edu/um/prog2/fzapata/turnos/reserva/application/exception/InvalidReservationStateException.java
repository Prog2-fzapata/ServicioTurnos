package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

// La reserva no está en condiciones de recibir el teléfono: no espera uno o la cátedra todavía no lo pidió
public class InvalidReservationStateException extends RuntimeException {

    public InvalidReservationStateException(Long processId, ReservationStatus status) {
        super("La reserva " + processId + " está en " + status + " y no puede recibir el teléfono");
    }
}
