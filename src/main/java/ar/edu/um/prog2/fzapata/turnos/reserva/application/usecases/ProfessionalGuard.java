package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;

// La regla de quién se puede reservar, compartida por la disponibilidad y el inicio de una reserva
@Component
public class ProfessionalGuard {

    // Una categoría apagada bloquea a sus profesionales: cada profesional tiene una sola
    public void requireEnabled(CatalogProfessional professional) {
        if (!professional.isEnabled()) {
            throw new ProfessionalDisabledException("El profesional " + professional.getId() + " está deshabilitado");
        }
        if (!professional.isCategoryEnabled()) {
            throw new ProfessionalDisabledException(
                    "La categoría del profesional " + professional.getId() + " está deshabilitada");
        }
    }
}
