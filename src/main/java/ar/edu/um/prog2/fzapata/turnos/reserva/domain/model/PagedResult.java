package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.List;

// Una página de resultados y el total de elementos que cumplen el criterio, no solo los de esta página
public record PagedResult<T>(List<T> content, long totalElements, PageQuery pageQuery) {
}
