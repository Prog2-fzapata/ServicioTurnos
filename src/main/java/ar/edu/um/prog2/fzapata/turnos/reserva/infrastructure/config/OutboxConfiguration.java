package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneMessageSettings;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RelayBatchPolicy;
import ar.edu.um.prog2.fzapata.turnos.shared.config.CatedraKafkaProperties;
import ar.edu.um.prog2.fzapata.turnos.shared.config.OutboxProperties;

// Lleva los parámetros hasta los casos de uso sin que la aplicación dependa de la configuración
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    RelayBatchPolicy relayBatchPolicy(OutboxProperties properties) {
        return new RelayBatchPolicy(properties.relayBatchSize());
    }

    @Bean
    PhoneMessageSettings phoneMessageSettings(CatedraKafkaProperties properties) {
        return new PhoneMessageSettings(properties.appointmentPhoneTopic(), properties.accountGroupId());
    }
}
