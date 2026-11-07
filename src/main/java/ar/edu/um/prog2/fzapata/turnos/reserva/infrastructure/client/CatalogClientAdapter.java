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
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogAccount;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatalogClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogAccountResponse;
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
    private final CatalogAccountMapper catalogAccountMapper;

    // Se decide solo por el status; el mensaje y el log nunca incluyen el cuerpo de la respuesta ni el JWT. Sin la
    // excepción original como causa: su mensaje trae el cuerpo y llegaría al log con la traza de un error inesperado
    private RuntimeException translate(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        if (status.is5xxServerError()) {
            log.atWarn().setMessage("Catálogo respondió con un error del servidor")
                    .addKeyValue("status", status.value()).log();
            return new CatalogUnavailableException("Catálogo no está disponible (" + status.value() + ")", null);
        }
        log.atError().setMessage("Catálogo respondió con un status inesperado, revisar que ambos servicios "
                + "compartan el JWT_SECRET").addKeyValue("status", status.value()).log();
        return new CatalogResponseException("Catálogo respondió " + status.value(), null);
    }

    // Sin conexión o timeout no hay respuesta que interpretar; si llegó pero no se pudo leer, es un desajuste
    private RuntimeException translateFailure(RestClientException e) {
        if (e instanceof RestClientResponseException responseException) {
            return translate(responseException);
        }
        if (e instanceof ResourceAccessException) {
            log.atWarn().setMessage("No se pudo conectar con catálogo")
                    .addKeyValue("exception", e.getClass().getSimpleName()).log();
            return new CatalogUnavailableException("No se pudo conectar con catálogo", e);
        }
        return new CatalogResponseException("La respuesta de catálogo no se pudo leer", e);
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
        } catch (RestClientException e) {
            throw translateFailure(e);
        }
    }

    private CatalogAccount fetchAccount() {
        try {
            CatalogAccountResponse response = catalogRestClient.get()
                    .uri("/api/account")
                    .retrieve()
                    .body(CatalogAccountResponse.class);
            if (response == null) {
                throw new CatalogResponseException("Catálogo respondió sin cuerpo", null);
            }
            return catalogAccountMapper.toDomainModel(response);
        } catch (RestClientException e) {
            throw translateFailure(e);
        }
    }

    @Override
    public Optional<CatalogProfessional> findProfessional(Long id) {
        return catalogRetryTemplate.invoke(() -> fetch(id));
    }

    @Override
    public CatalogAccount findAccount() {
        return catalogRetryTemplate.invoke(this::fetchAccount);
    }
}
