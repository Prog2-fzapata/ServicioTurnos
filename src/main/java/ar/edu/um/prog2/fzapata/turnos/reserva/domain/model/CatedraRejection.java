package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Motivos funcionales por los que la cátedra rechaza una operación (matriz de errores REST, §13 del contrato).
// Cada caso de uso decide qué hacer con cada uno
public enum CatedraRejection {
    // Crear hold
    SLOT_ALREADY_HELD,
    SLOT_ALREADY_RESERVED,
    PROFESSIONAL_DISABLED,
    // Crear o confirmar hold
    PROFESSIONAL_NOT_FOUND,
    INVALID_SLOT,
    // Confirmar hold
    HOLD_NOT_FOUND,
    HOLD_EXPIRED,
    RESERVATION_PROCESS_ALREADY_CONFIRMED,
    // Cancelar
    RESERVATION_NOT_FOUND,
    RESERVATION_NOT_CANCELLABLE
}
