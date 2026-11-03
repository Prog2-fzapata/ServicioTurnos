package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

// Mensaje que no se puede interpretar: reintentarlo no lo arregla, así que se descarta sin reintentos
public class InvalidCatedraEventException extends RuntimeException {

    public InvalidCatedraEventException(String message) {
        super(message);
    }

    public InvalidCatedraEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
