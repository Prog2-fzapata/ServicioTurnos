package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;

// Todos los criterios son opcionales y combinables; las fechas son inclusivas
public record AppointmentQuery(String externalPatientId, AppointmentStatus status, LocalDate from, LocalDate to) {
}
