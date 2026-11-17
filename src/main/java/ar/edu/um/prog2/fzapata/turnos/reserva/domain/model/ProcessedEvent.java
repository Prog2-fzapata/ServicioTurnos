package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Evento de Kafka ya aplicado: su eventId evita repetir el efecto si la cátedra lo entrega otra vez
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    private String eventId;
    private String eventType;
    private Instant processedAt;
}
