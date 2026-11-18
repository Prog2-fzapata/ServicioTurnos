package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Cancellation;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatedraApiClientAdapter implements CatedraApiClient {

    private static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final RestClient catedraRestClient;
    // Reintenta ante cualquier "no disponible": solo para operaciones que se pueden repetir sin riesgo
    private final RetryTemplate catedraRetryTemplate;
    // Reintenta solo si el pedido nunca llegó a la cátedra: la creación de un hold no es idempotente
    private final RetryTemplate catedraHoldRetryTemplate;
    private final CatedraMapper catedraMapper;

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

    //  Se solicita a la catedra via REST que cree el hold
    @Override
    public Hold createHold(Long professionalId, LocalDate date, LocalTime startTime, String externalPatientId) {
        HoldRequest request = new HoldRequest(professionalId, date, startTime, externalPatientId);

        HoldResponse response = execute(catedraHoldRetryTemplate, () -> catedraRestClient.post()
                .uri("/api/appointment-holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HoldResponse.class));

        Hold hold = catedraMapper.toDomainModel(required(response));
        // Logueamos la creación del hold
        log.atInfo().setMessage("La cátedra creó el hold").addKeyValue("holdId", hold.getHoldId())
                .addKeyValue("reservationProcessId", hold.getReservationProcessId()).log();
        return hold;
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
        HoldConfirmation confirmation = catedraMapper.toDomainModel(required(response));
        log.atInfo().setMessage("La cátedra aceptó confirmar el hold").addKeyValue("holdId", holdId)
                .addKeyValue("reservationProcessId", confirmation.getReservationProcessId()).log();
        return confirmation;
    }

    @Override
    public AppointmentPage findAppointments(AppointmentQuery query, int page, int size) {
        ResponseEntity<List<AppointmentResponse>> response = execute(catedraRetryTemplate,
                () -> catedraRestClient.get()
                        .uri(uri -> uri.path("/api/appointments")
                                .queryParamIfPresent("status", Optional.ofNullable(query.status()))
                                .queryParamIfPresent("externalPatientId",
                                        Optional.ofNullable(query.externalPatientId()))
                                .queryParamIfPresent("from", Optional.ofNullable(query.from()))
                                .queryParamIfPresent("to", Optional.ofNullable(query.to()))
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
        Cancellation cancellation = catedraMapper.toDomainModel(required(response));
        log.atInfo().setMessage("La cátedra canceló la reserva")
                .addKeyValue("reservationProcessId", cancellation.getReservationProcessId()).log();
        return cancellation;
    }

    // Le pide al template que ejecute attempt(request) según su política
    private <T> T execute(RetryTemplate retryTemplate, Supplier<T> request) {
        return retryTemplate.invoke(() -> attempt(request));
    }

    // Se hace un intento y traduce las fallas del cliente HTTP a las excepciones de la aplicación
    private <T> T attempt(Supplier<T> request) {
        try {
            return request.get(); // Aquí recién ejecuta la petición HTTP
        } catch (RestClientException e) {
            throw CatedraErrorTranslator.translate(e);
        }
    }

    // Si response es null CatedraResponseException
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
}
