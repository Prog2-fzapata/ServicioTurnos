package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryPolicy;
import ar.edu.um.prog2.fzapata.turnos.shared.config.RecoveryProperties;

// Lleva los parámetros hasta los casos de uso sin que la aplicación dependa de la configuración
@Configuration
@EnableConfigurationProperties(RecoveryProperties.class)
public class RecoveryConfiguration {

    @Bean
    RecoveryPolicy recoveryPolicy(RecoveryProperties properties) {
        return new RecoveryPolicy(properties.batchSize());
    }
}
