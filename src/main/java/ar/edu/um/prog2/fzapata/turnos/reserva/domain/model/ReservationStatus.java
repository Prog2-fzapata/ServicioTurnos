package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.List;

// Estados de un proceso de reserva (diagrama 04). EXPIRED, FAILED y CANCELLED son finales: nada los reabre.
// CONFIRMED solo puede pasar a CANCELLED
public enum ReservationStatus {
    HOLD_REQUESTED,
    HELD,
    WAITING_PHONE,
    PHONE_QUEUED,
    PHONE_SUBMITTED,
    CONFIRMED,
    EXPIRED,
    FAILED,
    CANCELLED;

    // Intentos que terminaron sin llegar a una reserva: «mis reservas» los oculta salvo que se pida su estado
    public static final List<ReservationStatus> DISCARDED = List.of(EXPIRED, FAILED);

    public boolean isFinal() {
        return this == EXPIRED || this == FAILED || this == CANCELLED;
    }

    // Una respuesta de la cátedra puede llegar antes de que el relay anote que el teléfono se envió: por eso desde
    // PHONE_QUEUED también se admiten CONFIRMED y WAITING_PHONE (gana el estado más avanzado, D84)
    public boolean canTransitionTo(ReservationStatus target) {
        return switch (this) {
            case HOLD_REQUESTED -> target == HELD || target == FAILED;
            case HELD -> target == WAITING_PHONE || target == EXPIRED || target == FAILED;
            case WAITING_PHONE -> target == PHONE_QUEUED || target == EXPIRED || target == FAILED;
            case PHONE_QUEUED -> target == PHONE_SUBMITTED || target == CONFIRMED || target == WAITING_PHONE
                    || target == EXPIRED || target == FAILED;
            case PHONE_SUBMITTED -> target == CONFIRMED || target == WAITING_PHONE || target == EXPIRED
                    || target == FAILED;
            case CONFIRMED -> target == CANCELLED;
            case EXPIRED, FAILED, CANCELLED -> false;
        };
    }
}
