package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

// Solo se cancela una reserva confirmada (o ya cancelada, que es idempotente)
public class ReservationNotCancellableException extends RuntimeException {

    public ReservationNotCancellableException(Long processId, ReservationStatus status) {
        super("La reserva " + processId + " está en " + status + " y no se puede cancelar");
    }

    public ReservationNotCancellableException(Long processId) {
        super("La cátedra no permite cancelar la reserva " + processId);
    }
}
