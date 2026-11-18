package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;

// Qué excepción de la aplicación corresponde a cada motivo por el que la cátedra rechaza crear un hold. Lo que no
// figura acá es un desajuste con el contrato (CatedraResponseException)
final class HoldRejectionTranslator {

    private HoldRejectionTranslator() {
    }

    static RuntimeException translate(CatedraRejection rejection, Long professionalId) {
        return switch (rejection) {
            case SLOT_ALREADY_HELD, SLOT_ALREADY_RESERVED -> new SlotUnavailableException();
            case PROFESSIONAL_DISABLED -> new ProfessionalDisabledException(
                    "La cátedra informa deshabilitado al profesional " + professionalId);
            case PROFESSIONAL_NOT_FOUND -> new ProfessionalNotFoundException(professionalId);
            case INVALID_SLOT -> new InvalidSlotException(
                    "La cátedra no reconoce el horario en la agenda del profesional " + professionalId);
            default -> new CatedraResponseException("La cátedra rechazó el hold con " + rejection, null);
        };
    }
}
