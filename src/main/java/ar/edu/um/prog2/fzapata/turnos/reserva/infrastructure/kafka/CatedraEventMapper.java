package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka.dto.CatedraEventMessage;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class CatedraEventMapper {

    private final JsonMapper jsonMapper; // De Jackson

    // Lo mínimo para poder deduplicar, ubicar el proceso y registrar el tipo; lo demás depende de cada evento
    public CatedraEvent toDomainModel(String payload) {
        // Traducimos el string a un objeto
        CatedraEventMessage message = parse(payload);
        // Verificamos por seguridad pese a que nunca deberían faltar
        if (isBlank(message.eventId())) {
            throw new InvalidCatedraEventException("Falta el eventId en el mensaje del evento");
        }
        if (isBlank(message.eventType())) {
            throw new InvalidCatedraEventException("Falta el eventType en el evento " + message.eventId());
        }
        if (isBlank(message.reservationProcessId())) {
            throw new InvalidCatedraEventException(
                    "Falta el reservationProcessId en el evento " + message.eventId());
        }

        return new CatedraEvent(message.eventId(), message.eventType(), message.reservationProcessId(),
                message.reservationId(), message.reason(), message.expiresAt());
    }

    private CatedraEventMessage parse(String payload) {
        try {
            CatedraEventMessage message = jsonMapper.readValue(payload, CatedraEventMessage.class);
            if (message == null) {
                throw new InvalidCatedraEventException("El mensaje del evento está vacío");
            }
            return message;
        } catch (JacksonException e) {
            throw new InvalidCatedraEventException("El mensaje del evento no es un JSON válido", e);
        }
    }
    // True si es null o blank
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
