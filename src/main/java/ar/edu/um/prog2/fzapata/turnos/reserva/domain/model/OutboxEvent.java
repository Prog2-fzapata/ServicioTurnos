package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un mensaje que espera su publicación en Kafka. Se guarda en la misma transacción que el cambio de estado que lo
// provoca. id es null hasta guardarlo; attempts cuenta las publicaciones fallidas
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    private Long id;
    private String eventId;
    private String reservationProcessId;
    private String topic;
    private String messageKey;
    private String payload;
    private OutboxStatus status;
    private int attempts;
    private Instant createdAt;
    private Instant sentAt;
}
