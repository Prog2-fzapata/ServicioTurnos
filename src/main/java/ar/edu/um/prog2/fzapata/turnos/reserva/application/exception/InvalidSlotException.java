package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// El horario no existe en la agenda del profesional, o ya pasó
public class InvalidSlotException extends RuntimeException {

    public InvalidSlotException(String message) {
        super(message);
    }
}
