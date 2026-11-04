package ar.edu.um.prog2.fzapata.turnos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// El planificador de Spring Boot tiene un hilo por defecto y lo compartirían el relay, el latido del stream y el
// vencimiento: una vuelta lenta de una retrasaría a las otras. Carga el application.properties real
class SchedulingPoolTest {

    @Configuration
    @EnableScheduling
    static class Scheduling {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(Scheduling.class);

    private static int poolSize(ThreadPoolTaskScheduler scheduler) {
        return scheduler.getScheduledThreadPoolExecutor().getCorePoolSize();
    }

    @Test
    void thereIsOneThreadPerScheduledTask() {
        runner.run(context -> assertThat(poolSize(context.getBean(ThreadPoolTaskScheduler.class))).isEqualTo(3));
    }

    @Test
    void anEnvironmentVariableWinsOverTheDefault() {
        runner.withSystemProperties("SCHEDULING_POOL_SIZE=5")
                .run(context -> assertThat(poolSize(context.getBean(ThreadPoolTaskScheduler.class))).isEqualTo(5));
    }
}
