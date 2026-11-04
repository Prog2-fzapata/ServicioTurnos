package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationPolicy;

// Carga el application.properties real: así se prueban el valor por defecto y que el entorno lo pise
class ExpirationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ExpirationConfiguration.class);

    @Test
    void theAdjustmentParametersHaveADefaultValue() {
        runner.run(context -> {
            ExpirationProperties properties = context.getBean(ExpirationProperties.class);
            assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.grace()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.batchSize()).isEqualTo(100);
            assertThat(context.getBean(ExpirationPolicy.class))
                    .isEqualTo(new ExpirationPolicy(Duration.ofSeconds(60), 100));
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefaultAndReachesThePolicy() {
        runner.withSystemProperties("RESERVATION_EXPIRATION_INTERVAL=5s", "RESERVATION_EXPIRATION_GRACE=2m",
                "RESERVATION_EXPIRATION_BATCH_SIZE=7").run(context -> {
            ExpirationProperties properties = context.getBean(ExpirationProperties.class);
            assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.grace()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.batchSize()).isEqualTo(7);
            assertThat(context.getBean(ExpirationPolicy.class))
                    .isEqualTo(new ExpirationPolicy(Duration.ofMinutes(2), 7));
        });
    }

    // Cero es válido: expira en cuanto vence, sin dejar llegar el evento que viene en camino
    @Test
    void aZeroGraceIsAllowed() {
        runner.withPropertyValues("expiration.grace=0s").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExpirationProperties.class).grace()).isZero();
        });
    }

    @Test
    void aNegativeGraceIsRejected() {
        runner.withPropertyValues("expiration.grace=-1s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("grace");
        });
    }

    @Test
    void theIntervalMustBePositive() {
        runner.withPropertyValues("expiration.interval=0s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("interval");
        });
    }

    @Test
    void atLeastOneProcessPerRoundIsRequired() {
        runner.withPropertyValues("expiration.batch-size=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("batchSize");
        });
    }
}
