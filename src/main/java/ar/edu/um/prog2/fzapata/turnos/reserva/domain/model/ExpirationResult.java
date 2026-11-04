package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// El resultado para un proceso. Solo lleva ids y estados: nada del paciente
public record ExpirationResult(Long processId, ReservationStatus from, ExpirationOutcome outcome) {
}
