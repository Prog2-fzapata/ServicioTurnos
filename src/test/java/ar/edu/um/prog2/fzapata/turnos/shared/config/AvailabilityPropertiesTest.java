package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Carga el application.properties real: así se prueban los valores por defecto y que el entorno los pise
class AvailabilityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(AvailabilityConfiguration.class);

    @Test
    void theAdjustmentParametersHaveADefaultValue() {
        runner.run(context -> {
            AvailabilityProperties properties = context.getBean(AvailabilityProperties.class);

            assertThat(properties.zone()).isEqualTo(ZoneId.of("America/Argentina/Mendoza"));
            assertThat(properties.maxRangeDays()).isEqualTo(31);
        });
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("AVAILABILITY_ZONE=UTC", "AVAILABILITY_MAX_RANGE_DAYS=10").run(context -> {
            AvailabilityProperties properties = context.getBean(AvailabilityProperties.class);

            assertThat(properties.zone()).isEqualTo(ZoneId.of("UTC"));
            assertThat(properties.maxRangeDays()).isEqualTo(10);
        });
    }

    @Test
    void theClockUsesTheConfiguredZone() {
        runner.withSystemProperties("AVAILABILITY_ZONE=Asia/Tokyo").run(context ->
                assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Tokyo")));
    }

    @Test
    void anUnknownZoneStopsTheStartup() {
        runner.withPropertyValues("availability.zone=No/Existe").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("availability.zone");
        });
    }

    @Test
    void atLeastOneDayOfRangeIsRequired() {
        runner.withPropertyValues("availability.max-range-days=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("maxRangeDays");
        });
    }
}
