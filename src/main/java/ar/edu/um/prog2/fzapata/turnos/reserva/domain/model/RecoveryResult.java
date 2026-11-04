package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// El resultado para un proceso. Solo lleva ids y estados: nada del paciente
public record RecoveryResult(Long processId, ReservationStatus from, RecoveryOutcome outcome) {
}
