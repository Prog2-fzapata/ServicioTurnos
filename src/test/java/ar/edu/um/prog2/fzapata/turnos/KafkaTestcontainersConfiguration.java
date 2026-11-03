package ar.edu.um.prog2.fzapata.turnos;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

// Aparte de TestcontainersConfiguration: solo el test del listener necesita un Kafka real
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
    }

    // El mismo topic que lee el listener, con una sola partición: el offset confirmado cuenta los mensajes
    @Bean
    NewTopic appointmentActionsTopic(@Value("${catedra.kafka.appointment-actions-topic}") String topic) {
        return new NewTopic(topic, 1, (short) 1);
    }
}
