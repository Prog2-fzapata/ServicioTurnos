package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.cutConnection;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import lombok.RequiredArgsConstructor;

// El flujo completo (seguridad, controller, caso de uso, máquina de estados, base de datos y clientes HTTP) contra un
// catálogo y una cátedra simulados que son servidores HTTP reales. "Hoy" es el lunes 12/10/2026 a las 10:10 en Mendoza
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, StartReservationIntegrationTest.FixedClock.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class StartReservationIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-12T13:10:00Z"), ZoneId.of("America/Argentina/Mendoza"));
        }
    }

    private static final FakeHttpServer CATALOG = start();
    private static final FakeHttpServer CATEDRA = start();

    private static final String PROFESSIONAL = """
            {"id": 15, "categoryId": 1, "categoryName": "Clinica medica", "categoryEnabled": true,
             "firstName": "Ana", "lastName": "Gomez", "enabled": true,
             "weeklySchedules": [
               {"id": 1, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
                "slotDurationMinutes": 30, "enabled": true}]}""";
    private static final String ACCOUNT = "{\"id\": 7, \"login\": \"juan\", \"firstName\": \"Juan\", \"lastName\": \"Perez\"}";
    private static final String HOLD_1 = """
            {"holdId": "hold-1", "reservationProcessId": "process-1", "status": "HELD",
             "expiresAt": "2026-10-12T13:20:00Z"}""";
    private static final String HOLD_2 = """
            {"holdId": "hold-2", "reservationProcessId": "process-2", "status": "HELD",
             "expiresAt": "2026-10-12T13:21:00Z"}""";
    private static final String CONFIRM_1 = """
            {"reservationProcessId": "process-1", "status": "WAITING_FOR_PHONE", "expiresAt": "2026-10-12T13:20:01Z"}""";
    private static final String CONFIRM_2 = """
            {"reservationProcessId": "process-2", "status": "WAITING_FOR_PHONE", "expiresAt": "2026-10-12T13:21:01Z"}""";
    private static final String NO_OCCUPANCIES = """
            {"professionalId": 15, "from": "2026-10-19", "to": "2026-10-19", "occupancies": []}""";
    private static final String BODY = "{\"professionalId\": 15, \"date\": \"2026-10-19\", \"startTime\": \"09:30\"}";

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;

    private static FakeHttpServer start() {
        try {
            return new FakeHttpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void pointTheClientsToTheFakeServers(DynamicPropertyRegistry registry) {
        registry.add("catalogo.base-url", CATALOG::baseUrl);
        registry.add("catedra.base-url", CATEDRA::baseUrl);
        // Un solo intento HTTP: los tests de caída no esperan reintentos
        registry.add("catalogo.max-attempts", () -> "1");
        registry.add("catedra.max-attempts", () -> "1");
    }

    @AfterAll
    static void stopTheFakeServers() {
        CATALOG.close();
        CATEDRA.close();
    }

    @BeforeEach
    void resetEverything() {
        CATALOG.reset();
        CATEDRA.reset();
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private ResultActions start(String body) throws Exception {
        return mockMvc.perform(post("/api/reservations").with(jwt().jwt(token -> token.claim("uid", "42")))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void catalogKnowsTheProfessionalAndTheAccount() {
        CATALOG.thenRespond(status(200, PROFESSIONAL), status(200, ACCOUNT));
    }

    private List<Map<String, Object>> processes() {
        return jdbc.sql("""
                SELECT status, failure_reason, hold_id, reservation_process_id, user_id, version,
                       to_char(expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS expires_at,
                       patient_first_name, patient_last_name, professional_first_name, professional_last_name,
                       appointment_date::text AS appointment_date, start_time::text AS start_time,
                       end_time::text AS end_time
                FROM reservation_processes ORDER BY id""").query().listOfRows();
    }

    private List<String> catedraCalls() {
        return CATEDRA.requests().stream().map(request -> request.method() + " " + request.rawPath()).toList();
    }

    @Test
    void startsTheReservationEndToEndAndLeavesItWaitingForThePhone() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(201, HOLD_1), status(202, CONFIRM_1));

        start(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.reservationProcessId").value("process-1"))
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.professionalFirstName").value("Ana"))
                .andExpect(jsonPath("$.appointmentDate").value("2026-10-19"))
                .andExpect(jsonPath("$.startTime").value("09:30:00"))
                .andExpect(jsonPath("$.endTime").value("10:00:00"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-12T13:20:01Z"))
                .andExpect(jsonPath("$.userId").doesNotExist());

        assertThat(processes()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("status", "WAITING_PHONE").containsEntry("user_id", "42")
                    .containsEntry("hold_id", "hold-1").containsEntry("reservation_process_id", "process-1")
                    .containsEntry("expires_at", "2026-10-12T13:20:01Z").containsEntry("patient_first_name", "Juan")
                    .containsEntry("patient_last_name", "Perez").containsEntry("professional_last_name", "Gomez")
                    .containsEntry("appointment_date", "2026-10-19").containsEntry("start_time", "09:30:00")
                    .containsEntry("end_time", "10:00:00").containsEntry("version", 2L);
            assertThat(row.get("failure_reason")).isNull();
        });
        assertThat(CATALOG.paths()).containsExactly("/api/professionals/15", "/api/account");
        assertThat(catedraCalls()).containsExactly("POST /api/appointment-holds",
                "POST /api/appointment-holds/hold-1/confirm");
    }

    // Catálogo recibe el JWT del usuario y la cátedra el técnico: mezclarlos filtraría uno hacia el otro servicio
    @Test
    void theUserJwtGoesToCatalogAndTheTechnicalOneToTheCatedra() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(201, HOLD_1), status(202, CONFIRM_1));

        start(BODY).andExpect(status().isCreated());

        assertThat(CATALOG.authorizations()).containsExactly("Bearer token", "Bearer token");
        assertThat(CATEDRA.authorizations()).containsOnly("Bearer jwt-tecnico-solo-para-tests");
    }

    @Test
    void theCatedraReceivesTheUserFromTheTokenAndTheNamesOfTheAccount() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(201, HOLD_1), status(202, CONFIRM_1));

        start("{\"professionalId\": 15, \"date\": \"2026-10-19\", \"startTime\": \"09:30\", \"userId\": \"99\"}")
                .andExpect(status().isCreated());

        assertThat(CATEDRA.requests().get(0).body()).contains("\"externalPatientId\":\"42\"")
                .contains("\"professionalId\":15").contains("\"date\":\"2026-10-19\"")
                .contains("\"startTime\":\"09:30\"").doesNotContain("99");
        assertThat(CATEDRA.requests().get(1).body()).contains("\"externalPatientId\":\"42\"")
                .contains("\"patientFirstName\":\"Juan\"").contains("\"patientLastName\":\"Perez\"");
        assertThat(processes()).singleElement().satisfies(row -> assertThat(row).containsEntry("user_id", "42"));
    }

    @Test
    void anAccountWithoutNamesIsRejectedAndNothingIsCreated() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL), status(200, "{\"id\": 7, \"login\": \"juan\"}"));

        start(BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PATIENT_NAME_REQUIRED"));

        assertThat(processes()).isEmpty();
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aSlotOutsideTheAgendaIsRejectedBeforeAskingForAnythingElse() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL));

        start("{\"professionalId\": 15, \"date\": \"2026-10-19\", \"startTime\": \"09:15\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));

        assertThat(processes()).isEmpty();
        assertThat(CATALOG.paths()).containsExactly("/api/professionals/15");
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aSlotThatAlreadyPassedIsRejected() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL));

        start("{\"professionalId\": 15, \"date\": \"2026-10-12\", \"startTime\": \"09:30\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));

        assertThat(processes()).isEmpty();
    }

    @Test
    void aMissingProfessionalIsA404AndNothingIsCreated() throws Exception {
        CATALOG.thenRespond(status(404, "{\"code\":\"PROFESSIONAL_NOT_FOUND\"}"));

        start(BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));

        assertThat(processes()).isEmpty();
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aProfessionalOfADisabledCategoryIsRejectedAndNothingIsCreated() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL.replace("\"categoryEnabled\": true", "\"categoryEnabled\": false")));

        start(BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));

        assertThat(processes()).isEmpty();
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aSlotTakenByOthersIsAConflictAndTheProcessFails() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(409, "{\"code\":\"SLOT_ALREADY_HELD\"}"));

        start(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        assertThat(processes()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("status", "FAILED").containsEntry("failure_reason", "SLOT_ALREADY_HELD")
                .containsEntry("hold_id", null));
        // Un 409 nunca se toma como «es mi hold»: no se confirma nada
        assertThat(catedraCalls()).containsExactly("POST /api/appointment-holds");
    }

    // D80: no se repite el POST ni se asume nada; se mira qué hay ocupado y, si está libre, se pide otro hold
    @Test
    void aLostHoldAnswerWithTheSlotFreeAsksForANewHold() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(cutConnection(), status(200, NO_OCCUPANCIES), status(201, HOLD_2), status(202, CONFIRM_2));

        start(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.reservationProcessId").value("process-2"));

        assertThat(processes()).hasSize(2);
        assertThat(processes().get(0)).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "HOLD_RESULT_UNKNOWN").containsEntry("hold_id", null);
        assertThat(processes().get(1)).containsEntry("status", "WAITING_PHONE").containsEntry("hold_id", "hold-2");
        assertThat(catedraCalls()).containsExactly("POST /api/appointment-holds",
                "GET /api/appointment-occupancies", "POST /api/appointment-holds",
                "POST /api/appointment-holds/hold-2/confirm");
        assertThat(CATEDRA.requests().get(1).rawQuery()).contains("professionalId=15").contains("from=2026-10-19")
                .contains("to=2026-10-19");
    }

    @Test
    void aLostHoldAnswerWithTheSlotShownAsHeldIsUnavailableAndNotRepeated() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(cutConnection(), status(200, """
                {"professionalId": 15, "from": "2026-10-19", "to": "2026-10-19", "occupancies": [
                  {"date": "2026-10-19", "startTime": "09:30:00", "status": "HELD",
                   "expiresAt": "2026-10-12T13:20:00Z"}]}"""));

        start(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        assertThat(processes()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("status", "FAILED").containsEntry("failure_reason", "HOLD_RESULT_UNKNOWN"));
        assertThat(catedraCalls()).containsExactly("POST /api/appointment-holds",
                "GET /api/appointment-occupancies");
    }

    // El hold existe y la cátedra pudo haberlo confirmado: queda en HELD, con sus ids, para resolverlo después
    @Test
    void aLostConfirmationLeavesTheProcessHeldWithItsIdsAndAnswers503() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(201, HOLD_1), cutConnection());

        start(BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));

        assertThat(processes()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("status", "HELD").containsEntry("hold_id", "hold-1")
                .containsEntry("reservation_process_id", "process-1").containsEntry("expires_at", "2026-10-12T13:20:00Z"));
        assertThat(catedraCalls()).containsExactly("POST /api/appointment-holds",
                "POST /api/appointment-holds/hold-1/confirm");
    }

    @Test
    void anExpiredHoldAtConfirmationIsRetriedWithANewHold() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(201, HOLD_1), status(404, "{\"code\":\"HOLD_EXPIRED\"}"), status(201, HOLD_2),
                status(202, CONFIRM_2));

        start(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationProcessId").value("process-2"));

        assertThat(processes()).hasSize(2);
        assertThat(processes().get(0)).containsEntry("status", "EXPIRED").containsEntry("failure_reason", "HOLD_EXPIRED")
                .containsEntry("hold_id", "hold-1");
        assertThat(processes().get(1)).containsEntry("status", "WAITING_PHONE");
    }

    @Test
    void ifTheHoldKeepsExpiringTheAnswerIsAConflictAndEveryAttemptIsLeftClosed() throws Exception {
        catalogKnowsTheProfessionalAndTheAccount();
        CATEDRA.thenRespond(status(201, HOLD_1), status(404, "{\"code\":\"HOLD_EXPIRED\"}"), status(201, HOLD_2),
                status(404, "{\"code\":\"HOLD_EXPIRED\"}"));

        start(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));

        assertThat(processes()).extracting(row -> row.get("status")).containsExactly("EXPIRED", "EXPIRED");
    }

    @Test
    void aCatalogThatDoesNotAnswerIsA503AndNothingIsCreated() throws Exception {
        CATALOG.thenRespond(cutConnection());

        start(BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));

        assertThat(processes()).isEmpty();
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        assertThat(CATALOG.requestCount()).isZero();
        assertThat(processes()).isEmpty();
    }
}
