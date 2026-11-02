package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;

// Las fechas son inclusivas
public record AvailabilityQuery(Long professionalId, LocalDate from, LocalDate to) {
}
