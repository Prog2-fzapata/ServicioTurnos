package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogAccount;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;

public interface CatalogClient {

    // Pide la agenda vigente a catálogo en nombre del usuario autenticado. Vacío si el profesional no existe.
    // Si catálogo no responde o falla (5xx, timeout), el adapter lanza una excepción: no es lo mismo que "no existe"
    Optional<CatalogProfessional> findProfessional(Long id);

    // La cuenta del usuario autenticado (nombre y apellido para reservar), pedida con su propio JWT. Su ausencia no es
    // esperable con un token válido: se trata como un desajuste entre servicios
    CatalogAccount findAccount();
}
