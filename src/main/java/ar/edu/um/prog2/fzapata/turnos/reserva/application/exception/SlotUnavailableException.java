package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// El horario está ocupado: la cátedra respondió SLOT_ALREADY_HELD o SLOT_ALREADY_RESERVED, o las ocupaciones lo muestran.
// Nunca se interpreta como «es mi hold»: no hay forma de verificarlo (D80)
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException() {
        super("El horario ya no está disponible");
    }
}
