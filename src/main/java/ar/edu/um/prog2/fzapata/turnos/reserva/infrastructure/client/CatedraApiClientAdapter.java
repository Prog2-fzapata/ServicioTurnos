package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Cancellation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Hold;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldConfirmation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.AppointmentResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CancelRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CancelResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ConfirmRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ConfirmResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.HoldRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.HoldResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.OccupancyListResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ProblemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatedraApiClientAdapter implements CatedraApiClient {

    private static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    // Un code legítimo es corto y en mayúsculas; cualquier otra cosa no se copia a un mensaje ni a un log
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z_]{1,60}");

    // Matriz de errores del §13 del contrato: se decide por status y code, nunca por el texto del detalle.
    // Todo lo que no figure acá es un desajuste (CatedraResponseException)
    private static final Map<String, CatedraRejection> REJECTIONS = Map.ofEntries(
            Map.entry("409:SLOT_ALREADY_HELD", CatedraRejection.SLOT_ALREADY_HELD),
            Map.entry("409:SLOT_ALREADY_RESERVED", CatedraRejection.SLOT_ALREADY_RESERVED),
            Map.entry("400:PROFESSIONAL_DISABLED", CatedraRejection.PROFESSIONAL_DISABLED),
            Map.entry("404:PROFESSIONAL_NOT_FOUND", CatedraRejection.PROFESSIONAL_NOT_FOUND),
            Map.entry("400:INVALID_SLOT", CatedraRejection.INVALID_SLOT),
            Map.entry("404:HOLD_NOT_FOUND", CatedraRejection.HOLD_NOT_FOUND),
            Map.entry("404:HOLD_EXPIRED", CatedraRejection.HOLD_EXPIRED),
            Map.entry("409:RESERVATION_PROCESS_ALREADY_CONFIRMED",
                    CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED),
            Map.entry("404:RESERVATION_NOT_FOUND", CatedraRejection.RESERVATION_NOT_FOUND),
            Map.entry("409:RESERVATION_NOT_CANCELLABLE", CatedraRejection.RESERVATION_NOT_CANCELLABLE));

    private final RestClient catedraRestClient;
    // Reintenta ante cualquier "no disponible": solo para operaciones que se pueden repetir sin riesgo
    private final RetryTemplate catedraRetryTemplate;
    // Reintenta solo si el pedido nunca llegó a la cátedra: la creación de un hold no es idempotente
    private final RetryTemplate catedraHoldRetryTemplate;
    private final CatedraMapper catedraMapper;

    // El cuerpo de un error puede no ser JSON o no traer code
    private String codeOf(RestClientResponseException e) {
        try {
            ProblemResponse problem = e.getResponseBodyAs(ProblemResponse.class);
            String code = problem == null ? null : problem.getCode();
            return code != null && SAFE_CODE.matcher(code).matches() ? code : null;
        } catch (RestClientException | IllegalStateException unreadable) {
            return null;
        }
    }

    // Los mensajes y el log llevan solo el status y el code: nunca el cuerpo, el JWT ni datos del paciente
    private RuntimeException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (e.getStatusCode().is5xxServerError()) {
            log.warn("La cátedra respondió {}", status);
            return new CatedraUnavailableException("La cátedra no está disponible (" + status + ")", e);
        }
        if (status == 401 || status == 403) {
            log.error("La cátedra rechazó el JWT técnico ({}): revisar CATEDRA_JWT", status);
            return new CatedraAuthenticationException(
                    "La cátedra rechazó el JWT técnico (" + status + "): revisar CATEDRA_JWT", e);
        }
        String code = codeOf(e);
        CatedraRejection rejection = REJECTIONS.get(status + ":" + code);
        if (rejection != null) {
            return new CatedraRejectedException(rejection);
        }
        log.error("La cátedra respondió {} ({}) y turnos no lo esperaba", status, code);
        return new CatedraResponseException("La cátedra respondió " + status + (code == null ? "" : " " + code), e);
    }

    // ConnectException (rechazada) o HttpConnectTimeoutException: no se llegó a enviar nada
    boolean neverConnected(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    // Un intento: traduce las fallas del cliente HTTP a las excepciones de la aplicación
    private <T> T attempt(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            if (neverConnected(e)) {
                log.warn("No se pudo conectar con la cátedra");
                throw new CatedraNotReachedException("No se pudo conectar con la cátedra", e);
            }
            // Corte o timeout después de enviar: el pedido pudo haber llegado
            log.warn("La cátedra no respondió: {}", e.getClass().getSimpleName());
            throw new CatedraUnavailableException("La cátedra no respondió", e);
        } catch (RestClientException e) {
            // Llegó una respuesta, pero su cuerpo no se pudo leer como JSON del contrato
            throw new CatedraResponseException("La respuesta de la cátedra no se pudo leer", e);
        }
    }

    private <T> T execute(RetryTemplate retryTemplate, Supplier<T> request) {
        return retryTemplate.invoke(() -> attempt(request));
    }

    private <T> T required(T response) {
        if (response == null) {
            throw new CatedraResponseException("La cátedra respondió sin cuerpo", null);
        }
        return response;
    }

    private long totalOf(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(TOTAL_COUNT_HEADER);
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException e) {
            // También cubre la cabecera ausente: un total inventado engañaría a quien pagina
            throw new CatedraResponseException("La cátedra respondió sin un " + TOTAL_COUNT_HEADER + " válido", e);
        }
    }

    @Override
    public List<Occupancy> findOccupancies(Long professionalId, LocalDate from, LocalDate to) {
        OccupancyListResponse response = execute(catedraRetryTemplate, () -> catedraRestClient.get()
                .uri(uri -> uri.path("/api/appointment-occupancies")
                        .queryParam("professionalId", professionalId)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .retrieve()
                .body(OccupancyListResponse.class));
        return catedraMapper.toDomainModel(required(response));
    }

    @Override
    public Hold createHold(Long professionalId, LocalDate date, LocalTime startTime, String externalPatientId) {
        HoldRequest request = new HoldRequest(professionalId, date, startTime, externalPatientId);
        HoldResponse response = execute(catedraHoldRetryTemplate, () -> catedraRestClient.post()
                .uri("/api/appointment-holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HoldResponse.class));
        return catedraMapper.toDomainModel(required(response));
    }

    @Override
    public HoldConfirmation confirmHold(String holdId, String externalPatientId, String patientFirstName,
            String patientLastName) {
        ConfirmRequest request = new ConfirmRequest(externalPatientId, patientFirstName, patientLastName);
        ConfirmResponse response = execute(catedraRetryTemplate, () -> catedraRestClient.post()
                .uri("/api/appointment-holds/{holdId}/confirm", holdId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ConfirmResponse.class));
        return catedraMapper.toDomainModel(required(response));
    }

    @Override
    public AppointmentPage findAppointments(AppointmentQuery query, int page, int size) {
        ResponseEntity<List<AppointmentResponse>> response = execute(catedraRetryTemplate,
                () -> catedraRestClient.get()
                        .uri(uri -> uri.path("/api/appointments")
                                .queryParamIfPresent("status", Optional.ofNullable(query.status()))
                                .queryParamIfPresent("from", Optional.ofNullable(query.from()))
                                .queryParamIfPresent("to", Optional.ofNullable(query.to()))
                                .queryParamIfPresent("externalPatientId",
                                        Optional.ofNullable(query.externalPatientId()))
                                .queryParam("page", page)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<List<AppointmentResponse>>() {
                        }));
        List<AppointmentResponse> body = required(response.getBody());
        return new AppointmentPage(body.stream().map(catedraMapper::toDomainModel).toList(), totalOf(response));
    }

    @Override
    public Cancellation cancelAppointment(String reservationProcessId, String reason) {
        CancelResponse response = execute(catedraRetryTemplate, () -> {
            RestClient.RequestBodySpec request = catedraRestClient.post()
                    .uri("/api/appointments/{reservationProcessId}/cancel", reservationProcessId);
            // El body es opcional: sin motivo no se envía
            if (reason != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(new CancelRequest(reason));
            }
            return request.retrieve().body(CancelResponse.class);
        });
        return catedraMapper.toDomainModel(required(response));
    }
}
