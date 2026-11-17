package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// process es el proceso tal como quedó: guardado si se aplicó, sin tocar en los otros casos
public record TransitionResult(TransitionOutcome outcome, ReservationStatus from, ReservationStatus target,
        ReservationProcess process) {
}
