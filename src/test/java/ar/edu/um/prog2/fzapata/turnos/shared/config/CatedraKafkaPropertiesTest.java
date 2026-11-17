package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

// Carga el application.properties real: así se prueban los valores por defecto, el techo y que el entorno los pise
class CatedraKafkaPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CatedraKafkaProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    void theRetryParametersHaveADefaultValue() {
        runner.run(context -> {
            CatedraKafkaProperties properties = context.getBean(CatedraKafkaProperties.class);

            assertThat(properties.maxRetries()).isEqualTo(3);
            assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("CATEDRA_KAFKA_MAX_RETRIES=5", "CATEDRA_KAFKA_INITIAL_BACKOFF=2s")
                .run(context -> {
            CatedraKafkaProperties properties = context.getBean(CatedraKafkaProperties.class);

            assertThat(properties.maxRetries()).isEqualTo(5);
            assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(2));
        });
    }

    // Cero desactiva los reintentos
    @Test
    void noRetriesIsAllowed() {
        runner.withPropertyValues("catedra.kafka.max-retries=0").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CatedraKafkaProperties.class).maxRetries()).isZero();
        });
    }

    @Test
    void theRetriesCannotBeNegative() {
        runner.withPropertyValues("catedra.kafka.max-retries=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("maxRetries");
        });
    }

    @Test
    void theRetriesHaveACeiling() {
        runner.withPropertyValues("catedra.kafka.max-retries=11").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("maxRetries");
        });
    }

    @Test
    void theCeilingItselfIsAllowed() {
        runner.withPropertyValues("catedra.kafka.max-retries=10").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CatedraKafkaProperties.class).maxRetries()).isEqualTo(10);
        });
    }
}
