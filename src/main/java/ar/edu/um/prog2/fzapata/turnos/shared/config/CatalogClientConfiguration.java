package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtPropagationInterceptor;

// Cliente HTTP hacia catálogo: timeouts, reintentos y reenvío del JWT del usuario
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogClientConfiguration {

    @Bean
    RestClient catalogRestClient(CatalogProperties properties, JwtPropagationInterceptor jwtPropagationInterceptor) {
        // HTTP/1.1 explícito: evita el intento de subir a HTTP/2 que hace el cliente de Java sobre http://
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(jwtPropagationInterceptor)
                .requestFactory(requestFactory)
                .build();
    }

    // Solo se reintenta cuando catálogo no está disponible; la consulta es un GET, así que repetirla es seguro
    @Bean
    RetryTemplate catalogRetryTemplate(CatalogProperties properties) {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(properties.maxAttempts() - 1L)
                .delay(properties.initialBackoff())
                .multiplier(2)
                .includes(CatalogUnavailableException.class)
                .build();
        return new RetryTemplate(policy);
    }
}
