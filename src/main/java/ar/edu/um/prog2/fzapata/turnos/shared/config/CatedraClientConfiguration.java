package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;

// Cliente HTTP hacia la cátedra: JWT técnico fijo (no el del usuario), timeouts y reintentos
@Configuration
@EnableConfigurationProperties(CatedraProperties.class)
public class CatedraClientConfiguration {

    @Bean
    RestClient catedraRestClient(CatedraProperties properties) {
        // HTTP/1.1 explícito: evita el intento de subir a HTTP/2 que hace el cliente de Java sobre http://
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.jwt())
                .requestFactory(requestFactory)
                .build();
    }

    private RetryTemplate retryTemplate(CatedraProperties properties, Class<? extends Throwable> retryable) {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(properties.maxAttempts() - 1L)
                .delay(properties.initialBackoff())
                .multiplier(2)
                .includes(retryable)
                .build();
        return new RetryTemplate(policy);
    }

    // Lecturas, confirmar y cancelar: se pueden repetir sin riesgo ante cualquier "no disponible"
    @Bean
    RetryTemplate catedraRetryTemplate(CatedraProperties properties) {
        return retryTemplate(properties, CatedraUnavailableException.class);
    }

    // Crear un hold no es idempotente y la cátedra no permite recuperar sus ids: un timeout o un 5xx pudo haber
    // creado el hold, así que solo se repite cuando la conexión ni siquiera se estableció
    @Bean
    RetryTemplate catedraHoldRetryTemplate(CatedraProperties properties) {
        return retryTemplate(properties, CatedraNotReachedException.class);
    }
}
