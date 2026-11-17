package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.shared.config.OutboxProperties;

class KafkaPhonePublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final KafkaPhonePublisher publisher = new KafkaPhonePublisher(kafkaTemplate,
            new OutboxProperties(Duration.ofSeconds(1), 50, Duration.ofMillis(200)));

    @AfterEach
    void clearTheInterruption() {
        Thread.interrupted();
    }

    @Test
    void sendsWithTheKeyAndWaitsForTheBrokerAck() {
        when(kafkaTemplate.send("topic", "process-1", "{json}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish("topic", "process-1", "{json}");

        verify(kafkaTemplate).send("topic", "process-1", "{json}");
    }

    @Test
    void ifTheBrokerRejectsTheMessageItIsAPublishFailure() {
        when(kafkaTemplate.send("topic", "process-1", "{json}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caído")));

        assertThatThrownBy(() -> publisher.publish("topic", "process-1", "{json}"))
                .isInstanceOf(PhonePublishException.class).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void ifTheAckDoesNotArriveInTimeItIsAPublishFailure() {
        when(kafkaTemplate.send("topic", "process-1", "{json}")).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> publisher.publish("topic", "process-1", "{json}"))
                .isInstanceOf(PhonePublishException.class);
    }

    @Test
    void anInterruptionIsAFailureAndKeepsTheInterruptedFlag() {
        when(kafkaTemplate.send("topic", "process-1", "{json}")).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> publisher.publish("topic", "process-1", "{json}"))
                .isInstanceOf(PhonePublishException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    // Sin metadatos del broker send() ni siquiera devuelve un futuro: lanza. Tiene que contar como fallo de publicación
    @Test
    void ifSendItselfFailsItIsAPublishFailure() {
        when(kafkaTemplate.send("topic", "process-1", "{json}"))
                .thenThrow(new org.springframework.kafka.KafkaException("Failed to send"));

        assertThatThrownBy(() -> publisher.publish("topic", "process-1", "{json}"))
                .isInstanceOf(PhonePublishException.class)
                .hasCauseInstanceOf(org.springframework.kafka.KafkaException.class);
    }

    @Test
    void aKafkaClientErrorOnSendIsAPublishFailureToo() {
        when(kafkaTemplate.send("topic", "process-1", "{json}"))
                .thenThrow(new org.apache.kafka.common.KafkaException("productor cerrado"));

        assertThatThrownBy(() -> publisher.publish("topic", "process-1", "{json}"))
                .isInstanceOf(PhonePublishException.class);
    }
}
