package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;

// Los criterios son opcionales y combinables. from y to (inclusivos) acotan por la fecha del turno: la reconciliación
// pide solo el día del turno de un proceso, y lo busca por su reservationProcessId entre los resultados
public record AppointmentQuery(String externalPatientId, AppointmentStatus status, LocalDate from, LocalDate to) {
}
