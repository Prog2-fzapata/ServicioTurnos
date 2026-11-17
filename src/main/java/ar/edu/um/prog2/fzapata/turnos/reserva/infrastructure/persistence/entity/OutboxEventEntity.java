package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity;

import java.time.Instant;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;

    private String reservationProcessId;

    private String topic;

    private String messageKey;

    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    private int attempts;

    private Instant createdAt;

    private Instant sentAt;
}
