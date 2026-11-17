package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// Catálogo respondió algo que no se esperaba (un 4xx distinto de 404, o un cuerpo ilegible): no es una caída,
// es un desajuste entre los servicios, y reintentar no lo arregla
public class CatalogResponseException extends RuntimeException {

    public CatalogResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
