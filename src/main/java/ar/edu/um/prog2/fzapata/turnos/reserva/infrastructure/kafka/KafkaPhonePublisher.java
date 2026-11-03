package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhonePublisher;
import ar.edu.um.prog2.fzapata.turnos.shared.config.OutboxProperties;
import lombok.RequiredArgsConstructor;

// El productor tiene acks=all e idempotencia (D12): el ack significa que el broker guardó el mensaje. Si la espera se
// agota el mensaje puede haber llegado igual; se reenvía con el mismo eventId y la cátedra descarta el duplicado
@Component
@RequiredArgsConstructor
public class KafkaPhonePublisher implements PhonePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.publishTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PhonePublishException("Se interrumpió la espera de la confirmación de Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new PhonePublishException("Kafka no confirmó el mensaje", e);
        }
    }
}
