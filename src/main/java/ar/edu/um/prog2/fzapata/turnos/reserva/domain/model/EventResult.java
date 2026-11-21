package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.Objects;

// Resultado de un evento con Outcome y TransitionResult
public record EventResult(EventOutcome outcome, TransitionResult transition) {

    // La transición está si y solo si el evento se procesó: cualquier otra combinación no tiene sentido
    public EventResult {
        Objects.requireNonNull(outcome, "outcome");
        if ((outcome == EventOutcome.PROCESSED) != (transition != null)) {
            throw new IllegalArgumentException(
                    "Un evento " + outcome + (transition == null ? " necesita su transición" : " no lleva transición"));
        }
    }

    // EventOutcome es un enum PROCESSED, DUPLICATE, UNKNOWN_PROCESS, UNKNOWN_TYPE
    // Si el evento se procesó, PROCESSED + transitionResult
    public static EventResult processed(TransitionResult transition) {
        return new EventResult(EventOutcome.PROCESSED, transition);
    }
    // No se procesó, solo outcome
    public static EventResult ignored(EventOutcome outcome) {
        return new EventResult(outcome, null);
    }
}
