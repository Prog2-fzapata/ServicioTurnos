package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// La cátedra no respondió a tiempo o falló con un 5xx, incluso después de los reintentos. En una operación con
// efectos el pedido pudo haber llegado: el resultado es desconocido
public class CatedraUnavailableException extends RuntimeException {

    public CatedraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
