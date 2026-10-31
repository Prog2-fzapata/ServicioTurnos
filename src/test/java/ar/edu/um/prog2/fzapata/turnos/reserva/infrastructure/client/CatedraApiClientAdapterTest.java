package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.cutConnection;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.slow;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.ResourceAccessException;

import com.jayway.jsonpath.JsonPath;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Cancellation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Hold;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldConfirmation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OccupancyStatus;
import ar.edu.um.prog2.fzapata.turnos.shared.config.CatedraClientConfiguration;

// El adapter con su configuración real (propiedades, JWT técnico, timeouts y reintentos) contra una cátedra simulada
class CatedraApiClientAdapterTest {

    private static final String TECHNICAL_JWT = "jwt-tecnico-de-la-catedra";
    private static final String USER_TOKEN = "jwt-del-usuario-que-no-debe-viajar";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 14);
    private static final String HOLD_JSON = """
            {"holdId": "hold-6c0c2f41", "reservationProcessId": "process-a92b47d0", "status": "HELD",
             "expiresAt": "2026-09-10T14:44:50Z"}""";
    private static final String APPOINTMENT_JSON = """
            {"reservationId": 8251, "reservationProcessId": "process-a92b47d0", "externalPatientId": "user-42",
             "appointmentDate": "2026-09-14", "startTime": "09:30:00", "endTime": "10:00:00", "professionalId": 15,
             "professionalFirstName": "Ana", "professionalLastName": "Gomez", "patientFirstName": "Juan",
             "patientLastName": "Perez", "patientPhone": "+5492615551234", "status": "CONFIRMED",
             "createdAt": "2026-09-10T14:35:00Z", "confirmedAt": "2026-09-10T14:36:00Z", "cancelledAt": null,
             "cancellationReason": null, "campoNuevo": 1}""";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CatedraClientConfiguration.class, CatedraApiClientAdapter.class,
                    CatedraMapper.class);

    private FakeHttpServer server;

    @BeforeEach
    void startFakeCatedraAndLogInAsAUser() throws Exception {
        server = new FakeHttpServer();
        // El usuario autenticado existe, pero la cátedra solo debe ver el JWT técnico
        Jwt jwt = Jwt.withTokenValue(USER_TOKEN).header("alg", "HS512").claim("uid", "42").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void stopFakeCatedraAndLogOut() {
        server.close();
        SecurityContextHolder.clearContext();
    }

    // Tiempos cortos para que los tests no esperen: 3 intentos, 50 ms antes del primer reintento (luego 100 ms)
    private void withAdapter(String baseUrl, String maxAttempts, Consumer<CatedraApiClientAdapter> test) {
        runner.withPropertyValues("catedra.base-url=" + baseUrl, "catedra.jwt=" + TECHNICAL_JWT,
                "catedra.connect-timeout=500ms", "catedra.read-timeout=300ms", "catedra.max-attempts=" + maxAttempts,
                "catedra.initial-backoff=50ms")
                .run(context -> test.accept(context.getBean(CatedraApiClientAdapter.class)));
    }

    private void withAdapter(String maxAttempts, Consumer<CatedraApiClientAdapter> test) {
        withAdapter(server.baseUrl(), maxAttempts, test);
    }

    private void withAdapter(Consumer<CatedraApiClientAdapter> test) {
        withAdapter("3", test);
    }

    private Hold createHold(CatedraApiClientAdapter adapter) {
        return adapter.createHold(15L, DAY, LocalTime.of(9, 30), "user-42");
    }

    private String problem(String code) {
        return "{\"title\":\"x\",\"detail\":\"texto-que-no-se-usa\",\"code\":\"" + code + "\"}";
    }

    // ---------- Ocupaciones

    @Test
    void asksForTheOccupanciesOfTheProfessionalBetweenTwoDates() {
        server.thenRespond(status(200, "{\"occupancies\": []}"));

        withAdapter(adapter -> {
            adapter.findOccupancies(15L, DAY, DAY.plusDays(2));

            FakeHttpServer.Request request = server.firstRequest();
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.rawPath()).isEqualTo("/api/appointment-occupancies");
            assertThat(request.rawQuery()).contains("professionalId=15").contains("from=2026-09-14")
                    .contains("to=2026-09-16");
        });
    }

    @Test
    void mapsConfirmedAndHeldOccupanciesAcceptingBothTimeFormats() {
        server.thenRespond(status(200, """
                {"professionalId": 15, "from": "2026-09-14", "to": "2026-09-14", "occupancies": [
                  {"date": "2026-09-14", "startTime": "09:00:00", "status": "CONFIRMED", "expiresAt": null},
                  {"date": "2026-09-14", "startTime": "09:30", "status": "HELD",
                   "expiresAt": "2026-09-10T14:44:50Z", "campoNuevo": true}]}"""));

        withAdapter(adapter -> {
            List<Occupancy> occupancies = adapter.findOccupancies(15L, DAY, DAY);

            assertThat(occupancies).hasSize(2);
            assertThat(occupancies.get(0).getDate()).isEqualTo(DAY);
            assertThat(occupancies.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(occupancies.get(0).getStatus()).isEqualTo(OccupancyStatus.CONFIRMED);
            assertThat(occupancies.get(0).getExpiresAt()).isNull();
            assertThat(occupancies.get(1).getStartTime()).isEqualTo(LocalTime.of(9, 30));
            assertThat(occupancies.get(1).getStatus()).isEqualTo(OccupancyStatus.HELD);
            assertThat(occupancies.get(1).getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
        });
    }

    @Test
    void noOccupanciesIsAValidAnswer() {
        server.thenRespond(status(200, "{\"professionalId\": 15, \"occupancies\": []}"));

        withAdapter(adapter -> assertThat(adapter.findOccupancies(15L, DAY, DAY)).isEmpty());
    }

    @Test
    void anOccupancyStatusThatTheContractDoesNotDefineIsAResponseError() {
        server.thenRespond(status(200, """
                {"occupancies": [{"date": "2026-09-14", "startTime": "09:00:00", "status": "BLOCKED"}]}"""));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraResponseException.class));
    }

    // ---------- Hold

    @Test
    void sendsTheHoldRequestInTheDocumentedFormat() {
        server.thenRespond(status(201, HOLD_JSON));

        withAdapter(adapter -> {
            createHold(adapter);

            FakeHttpServer.Request request = server.firstRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.rawPath()).isEqualTo("/api/appointment-holds");
            assertThat(request.contentType()).startsWith("application/json");
            assertThat(JsonPath.<Integer>read(request.body(), "$.professionalId")).isEqualTo(15);
            assertThat(JsonPath.<String>read(request.body(), "$.date")).isEqualTo("2026-09-14");
            assertThat(JsonPath.<String>read(request.body(), "$.startTime")).isEqualTo("09:30");
            assertThat(JsonPath.<String>read(request.body(), "$.externalPatientId")).isEqualTo("user-42");
        });
    }

    @Test
    void mapsTheHold() {
        server.thenRespond(status(201, HOLD_JSON));

        withAdapter(adapter -> {
            Hold hold = createHold(adapter);

            assertThat(hold.getHoldId()).isEqualTo("hold-6c0c2f41");
            assertThat(hold.getReservationProcessId()).isEqualTo("process-a92b47d0");
            assertThat(hold.getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
        });
    }

    // La cátedra no tiene idempotencia ni permite recuperar los ids de un hold: repetir el POST tras un resultado
    // desconocido podría chocar con nuestro propio hold sin poder reconocerlo
    @Test
    void aHoldIsNotRepeatedAfterAServerError() {
        server.thenRespond(status(503, ""));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> createHold(adapter)).isInstanceOf(CatedraUnavailableException.class)
                    .isNotInstanceOf(CatedraNotReachedException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void aHoldIsNotRepeatedAfterATimeout() {
        server.thenRespond(slow(1_000));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> createHold(adapter)).isInstanceOf(CatedraUnavailableException.class)
                    .isNotInstanceOf(CatedraNotReachedException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void aHoldIsNotRepeatedAfterACutConnection() {
        server.thenRespond(cutConnection());

        withAdapter(adapter -> {
            assertThatThrownBy(() -> createHold(adapter)).isInstanceOf(CatedraUnavailableException.class)
                    .isNotInstanceOf(CatedraNotReachedException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    // Si la conexión nunca se estableció no se envió nada: se sabe que no hay hold y repetir es seguro
    @Test
    void aHoldIsRepeatedWhenTheConnectionCouldNotBeEstablished() {
        String baseUrl = server.baseUrl();
        server.close();

        withAdapter(baseUrl, "3", adapter -> {
            long start = System.nanoTime();
            assertThatThrownBy(() -> createHold(adapter)).isInstanceOf(CatedraNotReachedException.class);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            // Esperas de 50 ms y 100 ms entre los tres intentos
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(145);
        });
    }

    @Test
    void aSuccessfulHoldMakesASingleRequest() {
        server.thenRespond(status(201, HOLD_JSON));

        withAdapter(adapter -> {
            createHold(adapter);

            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    // ---------- Confirmar

    @Test
    void confirmsTheHoldWithThePatient() {
        server.thenRespond(status(202, """
                {"reservationProcessId": "process-a92b47d0", "status": "WAITING_FOR_PHONE",
                 "expiresAt": "2026-09-10T14:44:50Z"}"""));

        withAdapter(adapter -> {
            HoldConfirmation confirmation = adapter.confirmHold("hold-6c0c2f41", "user-42", "Juan", "Perez");

            FakeHttpServer.Request request = server.firstRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.rawPath()).isEqualTo("/api/appointment-holds/hold-6c0c2f41/confirm");
            assertThat(JsonPath.<String>read(request.body(), "$.externalPatientId")).isEqualTo("user-42");
            assertThat(JsonPath.<String>read(request.body(), "$.patientFirstName")).isEqualTo("Juan");
            assertThat(JsonPath.<String>read(request.body(), "$.patientLastName")).isEqualTo("Perez");
            assertThat(confirmation.getReservationProcessId()).isEqualTo("process-a92b47d0");
            assertThat(confirmation.getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
        });
    }

    // El id del hold viene de afuera: no debe poder cambiar la ruta del pedido
    @Test
    void aHoldIdIsEncodedInThePath() {
        server.thenRespond(status(202, "{\"reservationProcessId\": \"p\"}"));

        withAdapter(adapter -> {
            adapter.confirmHold("a b/../c", "user-42", "Juan", "Perez");

            assertThat(server.firstRequest().rawPath()).isEqualTo("/api/appointment-holds/a%20b%2F..%2Fc/confirm");
        });
    }

    // Confirmar es seguro de repetir: la cátedra responde RESERVATION_PROCESS_ALREADY_CONFIRMED si ya estaba
    @Test
    void aConfirmationIsRetriedAfterAServerError() {
        server.thenRespond(status(503, ""), status(202, "{\"reservationProcessId\": \"process-a92b47d0\"}"));

        withAdapter(adapter -> {
            assertThat(adapter.confirmHold("hold-6c0c2f41", "user-42", "Juan", "Perez").getReservationProcessId())
                    .isEqualTo("process-a92b47d0");
            assertThat(server.requestCount()).isEqualTo(2);
        });
    }

    @Test
    void anAlreadyConfirmedProcessIsARejectionThatTheUseCaseCanTreatAsSuccess() {
        server.thenRespond(status(409, problem("RESERVATION_PROCESS_ALREADY_CONFIRMED")));

        withAdapter(adapter -> assertThatThrownBy(
                () -> adapter.confirmHold("hold-6c0c2f41", "user-42", "Juan", "Perez"))
                .isInstanceOfSatisfying(CatedraRejectedException.class, ex -> assertThat(ex.getRejection())
                        .isEqualTo(CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED)));
    }

    // ---------- Listar

    @Test
    void asksForTheAppointmentsWithEveryFilterAndThePage() {
        server.thenRespond(status(200, "[]", Map.of("X-Total-Count", "0")));

        withAdapter(adapter -> {
            adapter.findAppointments(new AppointmentQuery("user-42", AppointmentStatus.CONFIRMED, DAY,
                    DAY.plusDays(30)), 2, 20);

            FakeHttpServer.Request request = server.firstRequest();
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.rawPath()).isEqualTo("/api/appointments");
            assertThat(request.rawQuery()).contains("status=CONFIRMED").contains("from=2026-09-14")
                    .contains("to=2026-10-14").contains("externalPatientId=user-42").contains("page=2")
                    .contains("size=20");
        });
    }

    @Test
    void leavesOutTheFiltersThatWereNotGiven() {
        server.thenRespond(status(200, "[]", Map.of("X-Total-Count", "0")));

        withAdapter(adapter -> {
            adapter.findAppointments(new AppointmentQuery(null, null, null, null), 0, 50);

            assertThat(server.firstRequest().rawQuery()).isEqualTo("page=0&size=50");
        });
    }

    @Test
    void mapsTheAppointmentsAndTheTotalOfTheHeader() {
        server.thenRespond(status(200, "[" + APPOINTMENT_JSON + "]", Map.of("X-Total-Count", "35")));

        withAdapter(adapter -> {
            AppointmentPage page = adapter.findAppointments(new AppointmentQuery("user-42", null, null, null), 0, 20);

            assertThat(page.totalElements()).isEqualTo(35);
            assertThat(page.content()).hasSize(1);
            CatedraAppointment appointment = page.content().get(0);
            assertThat(appointment.getReservationId()).isEqualTo(8251L);
            assertThat(appointment.getReservationProcessId()).isEqualTo("process-a92b47d0");
            assertThat(appointment.getExternalPatientId()).isEqualTo("user-42");
            assertThat(appointment.getAppointmentDate()).isEqualTo(DAY);
            assertThat(appointment.getStartTime()).isEqualTo(LocalTime.of(9, 30));
            assertThat(appointment.getEndTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(appointment.getProfessionalId()).isEqualTo(15L);
            assertThat(appointment.getProfessionalFirstName()).isEqualTo("Ana");
            assertThat(appointment.getProfessionalLastName()).isEqualTo("Gomez");
            assertThat(appointment.getPatientFirstName()).isEqualTo("Juan");
            assertThat(appointment.getPatientLastName()).isEqualTo("Perez");
            assertThat(appointment.getPatientPhone()).isEqualTo("+5492615551234");
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
            assertThat(appointment.getCreatedAt()).isEqualTo(Instant.parse("2026-09-10T14:35:00Z"));
            assertThat(appointment.getConfirmedAt()).isEqualTo(Instant.parse("2026-09-10T14:36:00Z"));
            assertThat(appointment.getCancelledAt()).isNull();
            assertThat(appointment.getCancellationReason()).isNull();
        });
    }

    // Un total inventado engañaría a quien pagina
    @Test
    void aListWithoutATotalIsAResponseError() {
        server.thenRespond(status(200, "[]"));

        withAdapter(adapter -> assertThatThrownBy(
                () -> adapter.findAppointments(new AppointmentQuery(null, null, null, null), 0, 20))
                .isInstanceOf(CatedraResponseException.class));
    }

    @Test
    void aListWithAnInvalidTotalIsAResponseError() {
        server.thenRespond(status(200, "[]", Map.of("X-Total-Count", "muchos")));

        withAdapter(adapter -> assertThatThrownBy(
                () -> adapter.findAppointments(new AppointmentQuery(null, null, null, null), 0, 20))
                .isInstanceOf(CatedraResponseException.class));
    }

    // ---------- Cancelar

    @Test
    void cancelsWithTheReasonWhenThereIsOne() {
        server.thenRespond(status(200, """
                {"reservationId": 8251, "reservationProcessId": "process-a92b47d0", "status": "CANCELLED",
                 "cancelledAt": "2026-09-11T15:10:00Z"}"""));

        withAdapter(adapter -> {
            Cancellation cancellation = adapter.cancelAppointment("process-a92b47d0", "Cambio solicitado");

            FakeHttpServer.Request request = server.firstRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.rawPath()).isEqualTo("/api/appointments/process-a92b47d0/cancel");
            assertThat(JsonPath.<String>read(request.body(), "$.reason")).isEqualTo("Cambio solicitado");
            assertThat(cancellation.getReservationId()).isEqualTo(8251L);
            assertThat(cancellation.getReservationProcessId()).isEqualTo("process-a92b47d0");
            assertThat(cancellation.getCancelledAt()).isEqualTo(Instant.parse("2026-09-11T15:10:00Z"));
        });
    }

    @Test
    void cancelsWithoutABodyWhenThereIsNoReason() {
        server.thenRespond(status(200, "{\"reservationId\": 8251}"));

        withAdapter(adapter -> {
            adapter.cancelAppointment("process-a92b47d0", null);

            assertThat(server.firstRequest().body()).isEmpty();
        });
    }

    @Test
    void aCancellationIsRetriedAfterAServerError() {
        server.thenRespond(status(500, ""), status(200, "{\"reservationId\": 8251}"));

        withAdapter(adapter -> {
            assertThat(adapter.cancelAppointment("process-a92b47d0", null).getReservationId()).isEqualTo(8251L);
            assertThat(server.requestCount()).isEqualTo(2);
        });
    }

    // ---------- Errores del §13: se decide por status y code

    @ParameterizedTest
    @CsvSource({
            "409, SLOT_ALREADY_HELD, SLOT_ALREADY_HELD",
            "409, SLOT_ALREADY_RESERVED, SLOT_ALREADY_RESERVED",
            "400, PROFESSIONAL_DISABLED, PROFESSIONAL_DISABLED",
            "404, PROFESSIONAL_NOT_FOUND, PROFESSIONAL_NOT_FOUND",
            "400, INVALID_SLOT, INVALID_SLOT",
            "404, HOLD_NOT_FOUND, HOLD_NOT_FOUND",
            "404, HOLD_EXPIRED, HOLD_EXPIRED",
            "409, RESERVATION_PROCESS_ALREADY_CONFIRMED, RESERVATION_PROCESS_ALREADY_CONFIRMED",
            "404, RESERVATION_NOT_FOUND, RESERVATION_NOT_FOUND",
            "409, RESERVATION_NOT_CANCELLABLE, RESERVATION_NOT_CANCELLABLE"})
    void aFunctionalErrorOfTheCatedraIsARejectionWithItsReason(int httpStatus, String code, CatedraRejection reason) {
        server.thenRespond(status(httpStatus, problem(code)));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> createHold(adapter)).isInstanceOfSatisfying(CatedraRejectedException.class,
                    ex -> assertThat(ex.getRejection()).isEqualTo(reason));
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    // Un 409 de un slot ocupado nunca se toma como "es mi hold": no hay forma de recuperar ni verificar los ids
    @Test
    void aSlotAlreadyHeldIsNeverTreatedAsOurOwnHold() {
        server.thenRespond(status(409, problem("SLOT_ALREADY_HELD")));

        withAdapter(adapter -> assertThatThrownBy(() -> createHold(adapter))
                .isInstanceOf(CatedraRejectedException.class));
    }

    @ParameterizedTest
    @CsvSource({"401, ''", "403, HOLD_OWNER_MISMATCH", "403, RESERVATION_OWNER_MISMATCH",
            "403, STUDENT_INTEGRATION_NOT_FOUND"})
    void aRejectedIdentityIsAnAuthenticationErrorAndIsNotRetried(int httpStatus, String code) {
        server.thenRespond(status(httpStatus, code.isEmpty() ? "" : problem(code)));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraAuthenticationException.class)
                    .hasMessageContaining(String.valueOf(httpStatus)).hasMessageNotContaining(TECHNICAL_JWT);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    // Un 4xx sin significado funcional delata un desajuste con el contrato: reintentar no lo arregla
    @ParameterizedTest
    @CsvSource({"400, VALIDATION_ERROR", "400, INVALID_DATE_RANGE", "400, EXTERNAL_PATIENT_MISMATCH",
            "404, CODIGO_NUEVO", "409, HOLD_EXPIRED", "404, ''", "422, VALIDATION_ERROR"})
    void anUnexpectedClientErrorIsAResponseErrorAndIsNotRetried(int httpStatus, String code) {
        server.thenRespond(status(httpStatus, code.isEmpty() ? "no es json" : problem(code)));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraResponseException.class).hasMessageContaining(String.valueOf(httpStatus));
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void anErrorWithoutABodyIsStillAnUnexpectedResponse() {
        server.thenRespond(status(400, ""));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraResponseException.class).hasMessage("La cátedra respondió 400"));
    }

    @Test
    void anErrorBodyWithoutACodeIsStillAnUnexpectedResponse() {
        server.thenRespond(status(400, "{\"title\":\"Bad Request\"}"));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraResponseException.class).hasMessage("La cátedra respondió 400"));
    }

    // El code viene de afuera: si no tiene la forma de un code, no se copia a un mensaje ni a un log
    @Test
    void aCodeThatDoesNotLookLikeACodeIsNotCopiedToTheMessage() {
        server.thenRespond(status(400, problem("inyectado ERROR falso")));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraResponseException.class).hasMessageNotContaining("inyectado")
                .hasMessageNotContaining("falso"));
    }

    // ---------- JWT técnico

    @Test
    void everyOperationSendsTheTechnicalJwtAndNeverTheOneOfTheUser() {
        server.thenRespond(status(200, "{}"), status(201, "{}"), status(202, "{}"),
                status(200, "[]", Map.of("X-Total-Count", "0")), status(200, "{}"));

        withAdapter(adapter -> {
            adapter.findOccupancies(15L, DAY, DAY);
            createHold(adapter);
            adapter.confirmHold("hold-1", "user-42", "Juan", "Perez");
            adapter.findAppointments(new AppointmentQuery(null, null, null, null), 0, 20);
            adapter.cancelAppointment("process-1", null);

            assertThat(server.requestCount()).isEqualTo(5);
            assertThat(server.authorizations()).containsOnly("Bearer " + TECHNICAL_JWT);
        });
    }

    @Test
    void theTechnicalJwtDoesNotNeedAnAuthenticatedUser() {
        SecurityContextHolder.clearContext();
        server.thenRespond(status(200, "{\"occupancies\": []}"));

        withAdapter(adapter -> {
            adapter.findOccupancies(15L, DAY, DAY);

            assertThat(server.authorizations()).containsExactly("Bearer " + TECHNICAL_JWT);
        });
    }

    // ---------- Reintentos y caídas

    @Test
    void aReadIsRetriedAfterAServerErrorUntilItAnswers() {
        server.thenRespond(status(503, ""), status(500, ""), status(200, "{\"occupancies\": []}"));

        withAdapter(adapter -> {
            assertThat(adapter.findOccupancies(15L, DAY, DAY)).isEmpty();
            assertThat(server.requestCount()).isEqualTo(3);
        });
    }

    // Un 503 de infraestructura no garantiza un code (§13)
    @Test
    void aServerErrorWithoutACodeIsUnavailable() {
        server.thenRespond(status(503, "no es json"));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraUnavailableException.class).hasMessageContaining("503"));
    }

    @Test
    void aReadGivesUpAfterExactlyTheConfiguredAttempts() {
        server.thenRespond(status(503, ""));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraUnavailableException.class).isNotInstanceOf(CatedraNotReachedException.class);
            assertThat(server.requestCount()).isEqualTo(3);
        });
    }

    @Test
    void aReadIsRetriedAfterATimeout() {
        server.thenRespond(slow(1_000));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraUnavailableException.class);
            assertThat(server.requestCount()).isEqualTo(3);
        });
    }

    @Test
    void aReadIsRetriedAfterACutConnection() {
        server.thenRespond(cutConnection(), status(200, "{\"occupancies\": []}"));

        withAdapter(adapter -> {
            assertThat(adapter.findOccupancies(15L, DAY, DAY)).isEmpty();
            assertThat(server.requestCount()).isEqualTo(2);
        });
    }

    // Esperas de 50 ms y luego 100 ms: si la espera no se duplicara, serían 100 ms en total
    @Test
    void waitsLongerBeforeEachRetry() {
        server.thenRespond(status(500, ""));

        withAdapter(adapter -> {
            long start = System.nanoTime();
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraUnavailableException.class);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertThat(elapsedMillis).isGreaterThanOrEqualTo(145);
        });
    }

    @Test
    void oneAttemptDisablesTheRetries() {
        server.thenRespond(status(500, ""));

        withAdapter("1", adapter -> {
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraUnavailableException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void anUnreadableBodyIsAResponseErrorAndIsNotRetried() {
        server.thenRespond(status(200, "esto no es json"));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                    .isInstanceOf(CatedraResponseException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void anEmptyBodyIsAResponseError() {
        server.thenRespond(status(200, ""));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraResponseException.class));
    }

    // ---------- Nada sensible en los mensajes

    @Test
    void theMessagesNeverCarryTheBodyTheJwtNorThePatient() {
        String secret = "cuerpo-secreto Juan Perez +5492615551234 " + TECHNICAL_JWT;
        server.thenRespond(status(500, "{\"detail\":\"" + secret + "\"}"));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.confirmHold("hold-1", "user-42", "Juan", "Perez"))
                .isInstanceOf(CatedraUnavailableException.class).hasMessageNotContaining("cuerpo-secreto")
                .hasMessageNotContaining("Juan").hasMessageNotContaining("+549").hasMessageNotContaining(TECHNICAL_JWT));
    }

    @Test
    void theMessageOfAResponseErrorCarriesNeitherTheBodyNorTheJwt() {
        server.thenRespond(status(400, "{\"code\":\"VALIDATION_ERROR\",\"detail\":\"cuerpo-secreto " + TECHNICAL_JWT
                + "\"}"));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findOccupancies(15L, DAY, DAY))
                .isInstanceOf(CatedraResponseException.class).hasMessageNotContaining("cuerpo-secreto")
                .hasMessageNotContaining(TECHNICAL_JWT));
    }

    // ---------- Cómo se distingue "nunca se conectó" de "el pedido pudo haber llegado"

    @Test
    void aRefusedOrTimedOutConnectionMeansThatNothingWasSent() {
        CatedraApiClientAdapter adapter = new CatedraApiClientAdapter(null, null, null, null);

        assertThat(adapter.neverConnected(new ResourceAccessException("x", new ConnectException("refused"))))
                .isTrue();
        assertThat(adapter.neverConnected(new ResourceAccessException("x", new HttpConnectTimeoutException("t"))))
                .isTrue();
        // La causa puede venir envuelta
        assertThat(adapter.neverConnected(new ResourceAccessException("x",
                new IOException("envoltorio", new ConnectException("refused"))))).isTrue();
    }

    // Un timeout de lectura o un corte después de enviar: el pedido pudo haber llegado
    @Test
    void anyOtherFailureMeansThatTheRequestMayHaveArrived() {
        CatedraApiClientAdapter adapter = new CatedraApiClientAdapter(null, null, null, null);

        assertThat(adapter.neverConnected(new ResourceAccessException("x", new HttpTimeoutException("read")))).isFalse();
        assertThat(adapter.neverConnected(new ResourceAccessException("x", new IOException("corte")))).isFalse();
        assertThat(adapter.neverConnected(new ResourceAccessException("x"))).isFalse();
    }
}
