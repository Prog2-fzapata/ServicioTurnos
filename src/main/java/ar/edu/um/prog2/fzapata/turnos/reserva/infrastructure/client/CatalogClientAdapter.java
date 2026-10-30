package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.util.Optional;

import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatalogClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogProfessionalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogClientAdapter implements CatalogClient {

    private final RestClient catalogRestClient;
    private final RetryTemplate catalogRetryTemplate;
    private final CatalogProfessionalMapper catalogProfessionalMapper;

    // Se decide solo por el status; el mensaje y el log nunca incluyen el cuerpo de la respuesta ni el JWT
    private RuntimeException translate(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        if (status.is5xxServerError()) {
            log.warn("Catálogo respondió {}", status.value());
            return new CatalogUnavailableException("Catálogo no está disponible (" + status.value() + ")", e);
        }
        log.error("Catálogo respondió {} inesperado: revisar que ambos servicios compartan el JWT_SECRET",
                status.value());
        return new CatalogResponseException("Catálogo respondió " + status.value(), e);
    }

    // Un intento. Solo CatalogUnavailableException se reintenta: lo demás no mejora repitiendo el pedido
    private Optional<CatalogProfessional> fetch(Long id) {
        try {
            CatalogProfessionalResponse response = catalogRestClient.get()
                    .uri("/api/professionals/{id}", id)
                    .retrieve()
                    .body(CatalogProfessionalResponse.class);
            if (response == null) {
                throw new CatalogResponseException("Catálogo respondió sin cuerpo", null);
            }
            return Optional.of(catalogProfessionalMapper.toDomainModel(response));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            // Sin conexión o timeout: no hay respuesta que interpretar
            log.warn("No se pudo conectar con catálogo: {}", e.getClass().getSimpleName());
            throw new CatalogUnavailableException("No se pudo conectar con catálogo", e);
        } catch (RestClientException e) {
            // Llegó una respuesta, pero su cuerpo no se pudo leer como JSON del contrato
            throw new CatalogResponseException("La respuesta de catálogo no se pudo leer", e);
        }
    }

    @Override
    public Optional<CatalogProfessional> findProfessional(Long id) {
        return catalogRetryTemplate.invoke(() -> fetch(id));
    }
}
