package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// El reloj lleva la zona de atención; los tests lo reemplazan por uno fijo
@Configuration
@EnableConfigurationProperties(AvailabilityProperties.class)
public class AvailabilityConfiguration {

    @Bean
    Clock clock(AvailabilityProperties properties) {
        return Clock.system(properties.zone());
    }
}
