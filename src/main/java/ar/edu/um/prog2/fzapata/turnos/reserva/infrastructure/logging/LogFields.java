package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import org.slf4j.spi.LoggingEventBuilder;

// Campos que comparten los loggers de una reserva. Salen como campos del JSON, así se filtra por reservationId o por
// userId sin depender de la redacción del mensaje. El usuario es su id interno: nunca su nombre ni su teléfono
final class LogFields {

    private LogFields() {
    }

    // El evento que cambió la reserva: su tipo y su id (el id puede faltar)
    static LoggingEventBuilder origin(LoggingEventBuilder event, String eventType, String eventId) {
        LoggingEventBuilder withType = event.addKeyValue("eventType", eventType);
        return eventId == null ? withType : withType.addKeyValue("eventId", eventId);
    }

    // La reserva (id local, el mismo que devuelve la API) y, si se sabe, su usuario
    static LoggingEventBuilder reservation(LoggingEventBuilder event, Long reservationId, String userId) {
        LoggingEventBuilder withReservation = event.addKeyValue("reservationId", reservationId);
        return userId == null ? withReservation : withReservation.addKeyValue("userId", userId);
    }
}
