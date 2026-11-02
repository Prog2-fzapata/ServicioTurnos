package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;

// Carga el application.properties real: así se prueban el valor por defecto y que el entorno lo pise
class ReservationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ReservationConfiguration.class);

    @Test
    void theAdjustmentParameterHasADefaultValue() {
        runner.run(context -> {
            assertThat(context.getBean(ReservationProperties.class).conflictMaxAttempts()).isEqualTo(3);
            assertThat(context.getBean(ConflictRetryPolicy.class).maxAttempts()).isEqualTo(3);
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefaultAndReachesThePolicy() {
        runner.withSystemProperties("RESERVATION_CONFLICT_MAX_ATTEMPTS=7").run(context -> {
            assertThat(context.getBean(ReservationProperties.class).conflictMaxAttempts()).isEqualTo(7);
            assertThat(context.getBean(ConflictRetryPolicy.class).maxAttempts()).isEqualTo(7);
        });
    }

    @Test
    void atLeastOneAttemptIsRequired() {
        runner.withPropertyValues("reservation.conflict-max-attempts=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("conflictMaxAttempts");
        });
    }
}
