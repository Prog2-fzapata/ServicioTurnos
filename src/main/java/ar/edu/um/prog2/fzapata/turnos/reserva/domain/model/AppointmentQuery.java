package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Los criterios son opcionales y combinables. Sin filtro por fecha: la cátedra responde 500 a from y a to (A34), y quien
// necesita un proceso lo busca por su reservationProcessId entre los resultados
public record AppointmentQuery(String externalPatientId, AppointmentStatus status) {
}
