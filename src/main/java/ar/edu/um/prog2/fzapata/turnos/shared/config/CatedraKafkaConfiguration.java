package ar.edu.um.prog2.fzapata.turnos.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatedraKafkaProperties.class)
public class CatedraKafkaConfiguration {
}
