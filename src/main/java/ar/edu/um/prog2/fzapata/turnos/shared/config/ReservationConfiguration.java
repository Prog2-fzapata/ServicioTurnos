package ar.edu.um.prog2.fzapata.turnos.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldRetryPolicy;

// Lleva los parámetros hasta los casos de uso sin que la aplicación dependa de la configuración
@Configuration
@EnableConfigurationProperties(ReservationProperties.class)
public class ReservationConfiguration {

    @Bean
    ConflictRetryPolicy conflictRetryPolicy(ReservationProperties properties) {
        return new ConflictRetryPolicy(properties.conflictMaxAttempts());
    }

    @Bean
    HoldRetryPolicy holdRetryPolicy(ReservationProperties properties) {
        return new HoldRetryPolicy(properties.holdMaxAttempts());
    }
}
