package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// Otra actualización modificó el proceso entre que se leyó y se guardó (bloqueo optimista)
public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException(Long processId, Throwable cause) {
        super("El proceso de reserva " + processId + " fue modificado por otra operación", cause);
    }
}
