package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.shared.config.OutboxProperties;

// Un broker que de verdad no responde (un puerto cerrado), con el productor real. Con el valor por defecto de Kafka
// (max.block.ms = 60 s) cada publicación se bloqueaba un minuto antes de fallar, y fallaba con una excepción que el
// relay no trataba como fallo de publicación
class KafkaPhonePublisherUnreachableBrokerTest {

    @Test
    void withoutABrokerPublishingFailsWithinTheBlockTimeAsAPublishFailure() {
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true));
        KafkaPhonePublisher publisher = new KafkaPhonePublisher(new KafkaTemplate<>(factory),
                new OutboxProperties(Duration.ofSeconds(1), 50, Duration.ofSeconds(10)));
        long start = System.nanoTime();

        assertThatThrownBy(() -> publisher.publish("topic", "process-1", "{json}"))
                .isInstanceOf(PhonePublishException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        factory.destroy();
    }
}
