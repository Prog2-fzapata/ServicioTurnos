package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// Catálogo no respondió a tiempo o falló con un 5xx, incluso después de los reintentos
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
