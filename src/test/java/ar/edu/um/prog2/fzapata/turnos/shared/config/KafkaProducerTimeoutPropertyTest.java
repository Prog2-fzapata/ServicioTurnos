package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Carga el application.properties real: así se prueban el valor por defecto y que la variable de entorno lo pise
class KafkaProducerTimeoutPropertyTest {

    private static final String PROPERTY = "spring.kafka.producer.properties.max.block.ms";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    // Kafka lee milisegundos; su valor por defecto, 60 s, se sumaba por cada mensaje de la vuelta del relay
    @Test
    void theDefaultIsFiveSecondsInsteadOfTheSixtyOfTheProducer() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty(PROPERTY, Long.class))
                .isEqualTo(5_000L));
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("OUTBOX_PUBLISH_BLOCK_TIMEOUT=2000").run(context ->
                assertThat(context.getEnvironment().getProperty(PROPERTY, Long.class)).isEqualTo(2_000L));
    }
}
