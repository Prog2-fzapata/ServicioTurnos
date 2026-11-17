package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

public class ReservationProcessNotFoundException extends RuntimeException {

    public ReservationProcessNotFoundException(Long processId) {
        super("No existe el proceso de reserva " + processId);
    }
}
