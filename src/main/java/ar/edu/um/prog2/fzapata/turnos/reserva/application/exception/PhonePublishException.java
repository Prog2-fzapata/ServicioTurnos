package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// El broker no confirmó el mensaje (no respondió a tiempo o lo rechazó). El mensaje sigue pendiente
public class PhonePublishException extends RuntimeException {

    public PhonePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
