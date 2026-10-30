package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;

public interface CatalogClient {

    // Pide la agenda vigente a catálogo en nombre del usuario autenticado. Vacío si el profesional no existe.
    // Si catálogo no responde o falla (5xx, timeout), el adapter lanza una excepción: no es lo mismo que "no existe"
    Optional<CatalogProfessional> findProfessional(Long id);
}
