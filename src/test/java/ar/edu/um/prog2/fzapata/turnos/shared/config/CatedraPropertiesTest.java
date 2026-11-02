package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

// Carga el application.properties real: así se prueban los valores por defecto y que el entorno los pise
class CatedraPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CatedraProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    void theAdjustmentParametersHaveADefaultValue() {
        runner.run(context -> {
            CatedraProperties properties = context.getBean(CatedraProperties.class);

            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.maxAttempts()).isEqualTo(3);
            assertThat(properties.initialBackoff()).isEqualTo(Duration.ofMillis(200));
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("CATEDRA_READ_TIMEOUT=9s", "CATEDRA_MAX_ATTEMPTS=4",
                "CATEDRA_CONNECT_TIMEOUT=1s", "CATEDRA_INITIAL_BACKOFF=1s").run(context -> {
                    CatedraProperties properties = context.getBean(CatedraProperties.class);

                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(9));
                    assertThat(properties.maxAttempts()).isEqualTo(4);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    void theUrlIsRequired() {
        runner.withPropertyValues("catedra.base-url=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("baseUrl");
        });
    }

    @Test
    void theTechnicalJwtIsRequired() {
        runner.withPropertyValues("catedra.jwt=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("jwt");
        });
    }

    @Test
    void atLeastOneAttemptIsRequired() {
        runner.withPropertyValues("catedra.max-attempts=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("maxAttempts");
        });
    }

    // El toString de un record imprime todos los campos y puede acabar en un log
    @Test
    void theTechnicalJwtDoesNotAppearInTheText() {
        CatedraProperties properties = new CatedraProperties("http://catedra", "jwt-secreto", Duration.ofSeconds(2),
                Duration.ofSeconds(5), 3, Duration.ofMillis(200));

        assertThat(properties.toString()).doesNotContain("jwt-secreto").contains("http://catedra");
    }
}
