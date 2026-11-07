package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.Optional;

import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): quien los dispara registra el resultado, y lo hace siempre igual.
// Nunca incluye datos del paciente ni del profesional, solo ids y estados
@Slf4j
@Component
public class TransitionOutcomeLogger {

    private static LoggingEventBuilder described(LoggingEventBuilder event, String eventType, String eventId,
            Long reservationId, String userId) {
        return LogFields.reservation(LogFields.origin(event, eventType, eventId), reservationId, userId);
    }

    // eventType y eventId dicen qué evento disparó el cambio; eventId puede ser null
    public void log(String eventType, String eventId, Long reservationId, Optional<TransitionResult> result) {
        if (result.isEmpty()) {
            LogFields.reservation(LogFields.origin(log.atWarn().setMessage("Reserva inexistente"), eventType, eventId),
                    reservationId, null).log();
            return;
        }
        TransitionResult transition = result.get();
        String userId = transition.process().getUserId();
        switch (transition.outcome()) {
            case APPLIED -> described(log.atInfo().setMessage("Transición aplicada"), eventType, eventId,
                    reservationId, userId).addKeyValue("from", transition.from().name())
                    .addKeyValue("to", transition.target().name()).log();
            case DATA_COMPLETED -> described(log.atInfo().setMessage("Datos registrados, el estado no cambia"),
                    eventType, eventId, reservationId, userId).addKeyValue("status", transition.target().name())
                    .log();
            case ALREADY_IN_STATE -> described(log.atInfo().setMessage("Sin cambios, ya estaba en ese estado"),
                    eventType, eventId, reservationId, userId).addKeyValue("status", transition.target().name())
                    .log();
            case REJECTED -> described(log.atWarn().setMessage("Transición ignorada, el diagrama no la permite"),
                    eventType, eventId, reservationId, userId).addKeyValue("from", transition.from().name())
                    .addKeyValue("to", transition.target().name()).log();
        }
    }
}
