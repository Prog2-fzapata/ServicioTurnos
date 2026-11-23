package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Cuándo se puede enviar el teléfono: la reserva espera el teléfono y la cátedra ya lo pidió. El vencimiento se
// comprueba aparte, con la hora actual
public final class PhoneSubmissionRules {

    private PhoneSubmissionRules() {
    }
    // Solo True si estado es WAITING_PHONE y ya tiene requestEventId
    public static boolean canSubmit(ReservationProcess process) {
        return process.getStatus() == ReservationStatus.WAITING_PHONE && process.getRequestEventId() != null;
    }
}
