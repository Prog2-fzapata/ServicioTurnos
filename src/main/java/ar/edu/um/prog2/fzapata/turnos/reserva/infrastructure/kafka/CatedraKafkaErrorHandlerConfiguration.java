package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import ar.edu.um.prog2.fzapata.turnos.shared.config.CatedraKafkaProperties;

@Configuration
public class CatedraKafkaErrorHandlerConfiguration {

    private static final double BACKOFF_MULTIPLIER = 2.0;

    // Reintentos acotados con espera creciente. Agotados, el mensaje se descarta con un log de error y se sigue
    // (sin cola de eventos muertos, D70): una reserva que se quede sin su evento la recupera la reconciliación con la
    // cátedra. Un mensaje inválido se descarta sin reintentar
    @Bean
    DefaultErrorHandler catedraKafkaErrorHandler(CatedraKafkaProperties properties) {
        ExponentialBackOff backOff = new ExponentialBackOff(properties.initialBackoff().toMillis(), BACKOFF_MULTIPLIER);
        backOff.setMaxAttempts(properties.maxRetries());
        DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
        handler.addNotRetryableExceptions(InvalidCatedraEventException.class);
        return handler;
    }
}
