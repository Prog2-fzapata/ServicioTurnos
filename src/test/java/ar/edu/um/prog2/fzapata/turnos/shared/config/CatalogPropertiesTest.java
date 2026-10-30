package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

// Carga el application.properties real: así se prueban los valores por defecto y que el entorno los pise
class CatalogPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CatalogProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    void theAdjustmentParametersHaveADefaultValue() {
        runner.run(context -> {
            CatalogProperties properties = context.getBean(CatalogProperties.class);

            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.maxAttempts()).isEqualTo(3);
            assertThat(properties.initialBackoff()).isEqualTo(Duration.ofMillis(200));
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("CATALOGO_READ_TIMEOUT=7s", "CATALOGO_MAX_ATTEMPTS=5",
                "CATALOGO_CONNECT_TIMEOUT=1s", "CATALOGO_INITIAL_BACKOFF=1s").run(context -> {
                    CatalogProperties properties = context.getBean(CatalogProperties.class);

                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.maxAttempts()).isEqualTo(5);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    void theUrlIsRequired() {
        runner.withPropertyValues("catalogo.base-url=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("baseUrl");
        });
    }

    @Test
    void atLeastOneAttemptIsRequired() {
        runner.withPropertyValues("catalogo.max-attempts=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("maxAttempts");
        });
    }
}
