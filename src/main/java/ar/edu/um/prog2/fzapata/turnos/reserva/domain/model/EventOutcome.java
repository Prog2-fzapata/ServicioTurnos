package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// PROCESSED: el evento llegó a un proceso (el resultado de su transición está en EventResult). DUPLICATE: el eventId
// ya se había procesado. UNKNOWN_PROCESS: ningún proceso local tiene ese reservationProcessId. UNKNOWN_TYPE: el
// eventType no es uno de los que se tratan. Los tres últimos no cambian nada, pero el eventId queda registrado
public enum EventOutcome {
    PROCESSED,
    DUPLICATE,
    UNKNOWN_PROCESS,
    UNKNOWN_TYPE
}
