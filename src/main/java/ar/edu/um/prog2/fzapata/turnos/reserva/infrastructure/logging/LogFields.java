package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import org.slf4j.spi.LoggingEventBuilder;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RoundResult;

// Campos que comparten los loggers de una reserva.
// Son campos del JSON, así se filtra por reservationId o por userId sin depender de la redacción del mensaje.
// El usuario es su id interno: nunca su nombre ni su teléfono
final class LogFields {

    private LogFields() {
    }

    // El evento que cambió la reserva: su tipo y su id (id puede faltar)
    static LoggingEventBuilder origin(LoggingEventBuilder event, String eventType, String eventId) {
        return event.addKeyValue("eventType", eventType).addKeyValue("eventId", eventId);
    }

    // La reserva (id local, el mismo que devuelve la API), para cuando no se conoce a su usuario
    static LoggingEventBuilder reservation(LoggingEventBuilder event, Long reservationId) {
        return event.addKeyValue("reservationId", reservationId);
    }

    // La reserva y su usuario
    static LoggingEventBuilder reservation(LoggingEventBuilder event, Long reservationId, String userId) {
        return reservation(event, reservationId).addKeyValue("userId", userId);
    }

    // La reserva de un resultado de ronda y su estado: el que tenía cuando se decidió
    static LoggingEventBuilder round(LoggingEventBuilder event, RoundResult result) {
        return reservation(event, result.processId()).addKeyValue("status", result.from().name());
    }
}
