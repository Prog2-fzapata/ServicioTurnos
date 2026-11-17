package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// La cátedra rechazó la identidad: el JWT técnico está vencido, es de otra cuenta o la cuenta no tiene integración.
// Es un problema de configuración, no del usuario
public class CatedraAuthenticationException extends RuntimeException {

    public CatedraAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
