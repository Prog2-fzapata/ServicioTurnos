package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// transition solo está presente con PROCESSED
public record EventResult(EventOutcome outcome, TransitionResult transition) {

    public static EventResult processed(TransitionResult transition) {
        return new EventResult(EventOutcome.PROCESSED, transition);
    }

    public static EventResult without(EventOutcome outcome) {
        return new EventResult(outcome, null);
    }
}
