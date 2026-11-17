package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Carga el application.properties real: así se prueban el valor por defecto y que la variable de entorno lo pise
class DatabaseTimeoutPropertyTest {

    private static final String PROPERTY = "spring.datasource.hikari.connection-timeout";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    // Hikari lee milisegundos
    @Test
    void theDefaultIsFiveSecondsInsteadOfTheThirtyOfThePool() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty(PROPERTY, Long.class))
                .isEqualTo(5_000L));
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("DB_CONNECTION_TIMEOUT=1500").run(context ->
                assertThat(context.getEnvironment().getProperty(PROPERTY, Long.class)).isEqualTo(1_500L));
    }
}
