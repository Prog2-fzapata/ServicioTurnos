package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.List;

// Una página de reservas y el total que cumple el criterio, no solo las de esta página
public record AppointmentPage(List<CatedraAppointment> content, long totalElements) {
}
