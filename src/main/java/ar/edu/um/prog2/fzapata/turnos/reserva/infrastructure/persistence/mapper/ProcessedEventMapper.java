package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ProcessedEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.ProcessedEventEntity;

// Solo de dominio a entity: el puerto únicamente consulta si existe el id y guarda, nunca devuelve el evento
@Component
public class ProcessedEventMapper {

    public ProcessedEventEntity toEntity(ProcessedEvent event) {
        if (event == null) return null;
        return ProcessedEventEntity.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .processedAt(event.getProcessedAt())
                .build();
    }
}
