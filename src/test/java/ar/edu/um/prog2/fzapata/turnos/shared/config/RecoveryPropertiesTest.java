package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryPolicy;

// Carga el application.properties real: así se prueban el valor por defecto y que el entorno lo pise
class RecoveryPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RecoveryConfiguration.class);

    @Test
    void theAdjustmentParameterHasADefaultValue() {
        runner.run(context -> {
            assertThat(context.getBean(RecoveryProperties.class).batchSize()).isEqualTo(100);
            assertThat(context.getBean(RecoveryPolicy.class)).isEqualTo(new RecoveryPolicy(100));
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefaultAndReachesThePolicy() {
        runner.withSystemProperties("RECOVERY_BATCH_SIZE=7").run(context -> {
            assertThat(context.getBean(RecoveryProperties.class).batchSize()).isEqualTo(7);
            assertThat(context.getBean(RecoveryPolicy.class)).isEqualTo(new RecoveryPolicy(7));
        });
    }

    @Test
    void atLeastOneProcessPerKindIsRequired() {
        runner.withPropertyValues("recovery.batch-size=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("batchSize");
        });
    }
}
