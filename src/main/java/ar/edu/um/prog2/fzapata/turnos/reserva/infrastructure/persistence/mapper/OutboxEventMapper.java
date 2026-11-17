package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.OutboxEventEntity;

@Component
public class OutboxEventMapper {

    public OutboxEventEntity toEntity(OutboxEvent event) {
        if (event == null) return null;
        return OutboxEventEntity.builder()
                .id(event.getId())
                .eventId(event.getEventId())
                .reservationProcessId(event.getReservationProcessId())
                .topic(event.getTopic())
                .messageKey(event.getMessageKey())
                .payload(event.getPayload())
                .status(event.getStatus())
                .attempts(event.getAttempts())
                .createdAt(event.getCreatedAt())
                .sentAt(event.getSentAt())
                .build();
    }

    public OutboxEvent toDomainModel(OutboxEventEntity entity) {
        if (entity == null) return null;
        return OutboxEvent.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .reservationProcessId(entity.getReservationProcessId())
                .topic(entity.getTopic())
                .messageKey(entity.getMessageKey())
                .payload(entity.getPayload())
                .status(entity.getStatus())
                .attempts(entity.getAttempts())
                .createdAt(entity.getCreatedAt())
                .sentAt(entity.getSentAt())
                .build();
    }
}
