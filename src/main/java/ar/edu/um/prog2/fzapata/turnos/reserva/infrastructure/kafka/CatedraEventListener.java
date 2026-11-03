package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.EventResultLogger;
import lombok.RequiredArgsConstructor;

// Adaptador de entrada: convierte el mensaje y llama al servicio; no decide nada del negocio
@Component
@RequiredArgsConstructor
public class CatedraEventListener {

    private final ReservaService reservaService;
    private final CatedraEventMapper catedraEventMapper;
    private final EventResultLogger eventResultLogger;

    // El offset se confirma recién cuando el caso de uso terminó y su transacción hizo COMMIT. Si lanza, no se
    // confirma y el manejador de errores reintenta; Kafka reentrega y el eventId evita repetir el efecto
    @KafkaListener(topics = "${catedra.kafka.appointment-actions-topic}")
    public void onEvent(String payload, Acknowledgment acknowledgment) {
        CatedraEvent event = catedraEventMapper.toDomainModel(payload);
        eventResultLogger.log(event, reservaService.processEvent(event));
        acknowledgment.acknowledge();
    }
}
