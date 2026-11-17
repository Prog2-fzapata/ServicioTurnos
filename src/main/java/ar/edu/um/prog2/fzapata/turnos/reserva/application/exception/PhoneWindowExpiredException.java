package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// Pasó el vencimiento de la reserva: ya no se acepta el teléfono
public class PhoneWindowExpiredException extends RuntimeException {

    public PhoneWindowExpiredException(Long processId) {
        super("Venció el tiempo para enviar el teléfono de la reserva " + processId);
    }
}
