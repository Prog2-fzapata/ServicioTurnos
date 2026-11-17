package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// La cátedra respondió algo que no se esperaba (un 4xx sin significado funcional, o un cuerpo ilegible): es un
// desajuste con el contrato y reintentar no lo arregla
public class CatedraResponseException extends RuntimeException {

    public CatedraResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
