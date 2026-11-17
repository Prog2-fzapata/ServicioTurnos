package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.cutConnection;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import lombok.RequiredArgsConstructor;

// Consultar y cancelar contra PostgreSQL real y una cátedra simulada. Los dos usuarios del test son "42" (el que
// consulta) y "99" (el ajeno): lo que es de 99 nunca debe verse ni tocarse desde el token de 42
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationsQueryIntegrationTest {

    private static final FakeHttpServer CATEDRA = start();
    private static final Instant T0 = Instant.parse("2026-11-02T13:00:00Z");
    private static final String CANCELLED_BY_CATEDRA = """
            {"reservationId": 8251, "reservationProcessId": "process-1", "status": "CANCELLED",
             "cancelledAt": "2026-11-03T15:10:00Z"}""";

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservationProcessRepository repository;

    private static FakeHttpServer start() {
        try {
            return new FakeHttpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void pointTheClientToTheFakeServer(DynamicPropertyRegistry registry) {
        registry.add("catalogo.base-url", () -> "http://localhost:1");
        registry.add("catedra.base-url", CATEDRA::baseUrl);
        // Un solo intento: los tests de caída no esperan reintentos
        registry.add("catedra.max-attempts", () -> "1");
    }

    @AfterAll
    static void stopTheFakeServer() {
        CATEDRA.close();
    }

    @BeforeEach
    void resetEverything() {
        CATEDRA.reset();
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    // processId null es un intento que nunca consiguió un hold
    private ReservationProcess seed(String userId, String processId, ReservationStatus status, String date,
            String startTime) {
        LocalTime start = LocalTime.parse(startTime);
        return repository.save(ReservationProcess.builder().userId(userId).reservationProcessId(processId)
                .professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.parse(date)).startTime(start).endTime(start.plusMinutes(30)).status(status)
                .patientFirstName("Juan").patientLastName("Perez").createdAt(T0).updatedAt(T0).build());
    }

    private ResultActions asUser(String userId, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.claim("uid", userId))));
    }

    private ResultActions list(String query) throws Exception {
        return asUser("42", get("/api/reservations" + query));
    }

    private ResultActions cancel(Long id, String userId, String body) throws Exception {
        var request = post("/api/reservations/" + id + "/cancel");
        return asUser(userId, body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> row(Long id) {
        return jdbc.sql("SELECT status, cancellation_reason, cancelled_at, version FROM reservation_processes WHERE id = :id")
                .param("id", id).query().singleRow();
    }

    private List<String> catedraCalls() {
        return CATEDRA.requests().stream().map(request -> request.method() + " " + request.rawPath()).toList();
    }

    // ---------- Listar

    @Test
    void listsOnlyTheReservationsOfTheUserNewestFirst() throws Exception {
        ReservationProcess oldest = seed("42", "p-1", ReservationStatus.CANCELLED, "2026-11-08", "11:00");
        ReservationProcess sameDayEarly = seed("42", "p-2", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        ReservationProcess sameDayLate = seed("42", "p-3", ReservationStatus.WAITING_PHONE, "2026-11-09", "10:00");
        ReservationProcess newest = seed("42", "p-4", ReservationStatus.CONFIRMED, "2026-11-10", "09:00");
        seed("99", "p-5", ReservationStatus.CONFIRMED, "2026-11-20", "09:00");

        list("")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "4"))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].id", contains(newest.getId().intValue(),
                        sameDayLate.getId().intValue(), sameDayEarly.getId().intValue(), oldest.getId().intValue())))
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andExpect(jsonPath("$[0].holdId").doesNotExist());
    }

    @Test
    void anUserWithoutReservationsGetsAnEmptyList() throws Exception {
        seed("99", "p-1", ReservationStatus.CONFIRMED, "2026-11-20", "09:00");

        list("").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)))
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void filtersByStatus() throws Exception {
        seed("42", "p-1", ReservationStatus.CANCELLED, "2026-11-08", "11:00");
        ReservationProcess confirmed = seed("42", "p-2", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        seed("42", "p-3", ReservationStatus.WAITING_PHONE, "2026-11-09", "10:00");
        seed("99", "p-4", ReservationStatus.CONFIRMED, "2026-11-20", "09:00");

        list("?status=CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(confirmed.getId().intValue()));
    }

    // Decisión del usuario: sin status se ocultan las EXPIRED y FAILED; con status se devuelve exactamente ese estado
    @Test
    void theDiscardedAttemptsAreHiddenUnlessTheirStatusIsAsked() throws Exception {
        ReservationProcess confirmed = seed("42", "p-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:00");
        ReservationProcess expired = seed("42", "p-2", ReservationStatus.EXPIRED, "2026-11-09", "09:30");
        ReservationProcess failedWithHold = seed("42", "p-3", ReservationStatus.FAILED, "2026-11-09", "10:00");
        ReservationProcess failedWithoutHold = seed("42", null, ReservationStatus.FAILED, "2026-11-09", "10:30");
        ReservationProcess cancelled = seed("42", "p-5", ReservationStatus.CANCELLED, "2026-11-09", "11:00");
        seed("99", "p-6", ReservationStatus.FAILED, "2026-11-09", "11:30");

        list("")
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].id", contains(cancelled.getId().intValue(), confirmed.getId().intValue())));
        list("?status=FAILED")
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].id", contains(failedWithoutHold.getId().intValue(),
                        failedWithHold.getId().intValue())));
        list("?status=EXPIRED")
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(expired.getId().intValue()));
    }

    @Test
    void paginatesAndTheLinksKeepTheFilter() throws Exception {
        List<ReservationProcess> all = List.of(
                seed("42", "p-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:00"),
                seed("42", "p-2", ReservationStatus.CONFIRMED, "2026-11-09", "09:30"),
                seed("42", "p-3", ReservationStatus.CONFIRMED, "2026-11-09", "10:00"),
                seed("42", "p-4", ReservationStatus.CONFIRMED, "2026-11-09", "10:30"),
                seed("42", "p-5", ReservationStatus.CONFIRMED, "2026-11-09", "11:00"));

        // Más reciente primero: 11:00, 10:30 | 10:00, 09:30 | 09:00
        list("?status=CONFIRMED&page=1&size=2")
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("Link", containsString("status=CONFIRMED")))
                .andExpect(header().string("Link", containsString("page=2&size=2>; rel=\"next\"")))
                .andExpect(header().string("Link", containsString("page=0&size=2>; rel=\"prev\"")))
                .andExpect(jsonPath("$[*].id", contains(all.get(2).getId().intValue(),
                        all.get(1).getId().intValue())));
        list("?status=CONFIRMED&page=2&size=2")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(all.get(0).getId().intValue()));
        list("?page=9").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)))
                .andExpect(header().string("X-Total-Count", "5"));
    }

    @Test
    void aSizeAboveTheLimitIsReducedToIt() throws Exception {
        seed("42", "p-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:00");

        list("?size=100000").andExpect(header().string("Link", containsString("size=100>")));
    }

    // ---------- Consultar una

    @Test
    void getsOneOfItsOwnReservations() throws Exception {
        ReservationProcess mine = seed("42", "p-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");

        asUser("42", get("/api/reservations/" + mine.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.getId().intValue()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointmentDate").value("2026-11-09"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void aForeignReservationAnswersExactlyLikeAMissingOne() throws Exception {
        ReservationProcess foreign = seed("99", "p-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");

        String foreignBody = asUser("42", get("/api/reservations/" + foreign.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String missingBody = asUser("42", get("/api/reservations/" + (foreign.getId() + 1000)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Solo cambia la ruta pedida: el contenido no delata si existe
        assertThat(foreignBody.replace(String.valueOf(foreign.getId()), "ID"))
                .isEqualTo(missingBody.replace(String.valueOf(foreign.getId() + 1000), "ID"));
    }

    // ---------- Cancelar

    @Test
    void cancelsAConfirmedReservationInTheCatedraAndThenLocally() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(status(200, CANCELLED_BY_CATEDRA));

        cancel(mine.getId(), "42", "{\"reason\": \"no puedo ir\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("no puedo ir"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        assertThat(catedraCalls()).containsExactly("POST /api/appointments/process-1/cancel");
        assertThat(CATEDRA.firstRequest().body()).contains("\"reason\":\"no puedo ir\"");
        assertThat(row(mine.getId())).containsEntry("status", "CANCELLED")
                .containsEntry("cancellation_reason", "no puedo ir");
        assertThat(row(mine.getId()).get("cancelled_at")).isNotNull();
    }

    @Test
    void cancelsWithoutABodyAndWithoutAReason() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(status(200, CANCELLED_BY_CATEDRA));

        cancel(mine.getId(), "42", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(CATEDRA.firstRequest().body()).isEmpty();
        assertThat(row(mine.getId())).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", null);
    }

    // Idempotencia: la segunda vez devuelve el estado actual sin tocar la cátedra ni la base
    @Test
    void cancellingTwiceAnswersTheSameAndCallsTheCatedraOnlyOnce() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(status(200, CANCELLED_BY_CATEDRA));
        cancel(mine.getId(), "42", "{\"reason\": \"uno\"}").andExpect(status().isOk());
        Object versionAfterFirst = row(mine.getId()).get("version");

        cancel(mine.getId(), "42", "{\"reason\": \"dos\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("uno"));

        assertThat(CATEDRA.requestCount()).isEqualTo(1);
        assertThat(row(mine.getId())).containsEntry("version", versionAfterFirst);
    }

    @Test
    void cancellingAForeignReservationIsA404AndNothingChanges() throws Exception {
        ReservationProcess foreign = seed("99", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");

        cancel(foreign.getId(), "42", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

        assertThat(CATEDRA.requestCount()).isZero();
        assertThat(row(foreign.getId())).containsEntry("status", "CONFIRMED");
    }

    @Test
    void cancellingAMissingReservationIsA404() throws Exception {
        cancel(987654L, "42", null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aReservationThatIsNotConfirmedYetCannotBeCancelledAndTheCatedraIsNotCalled() throws Exception {
        ReservationProcess waiting = seed("42", "process-1", ReservationStatus.WAITING_PHONE, "2026-11-09", "09:30");

        cancel(waiting.getId(), "42", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"));

        assertThat(CATEDRA.requestCount()).isZero();
        assertThat(row(waiting.getId())).containsEntry("status", "WAITING_PHONE");
    }

    @Test
    void ifTheCatedraRefusesTheCancellationTheReservationStaysConfirmed() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(status(409, "{\"code\": \"RESERVATION_NOT_CANCELLABLE\", \"detail\": \"ya pasó\"}"));

        cancel(mine.getId(), "42", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"));

        assertThat(row(mine.getId())).containsEntry("status", "CONFIRMED");
    }

    @Test
    void ifTheCatedraDoesNotKnowTheReservationItIsAnInternalErrorAndNothingChanges() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(status(404, "{\"code\": \"RESERVATION_NOT_FOUND\", \"detail\": \"no existe\"}"));

        cancel(mine.getId(), "42", null)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(row(mine.getId())).containsEntry("status", "CONFIRMED");
    }

    @Test
    void ifTheCatedraIsDownTheAnswerIs503AndTheReservationStaysConfirmed() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(cutConnection());

        cancel(mine.getId(), "42", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));

        assertThat(row(mine.getId())).containsEntry("status", "CONFIRMED");
    }

    // Si la cátedra canceló pero la respuesta se perdió, el usuario reintenta y la cátedra (idempotente) contesta bien
    @Test
    void retryingAfterALostAnswerCancelsLocallyThistime() throws Exception {
        ReservationProcess mine = seed("42", "process-1", ReservationStatus.CONFIRMED, "2026-11-09", "09:30");
        CATEDRA.thenRespond(cutConnection(), status(200, CANCELLED_BY_CATEDRA));

        cancel(mine.getId(), "42", null).andExpect(status().isServiceUnavailable());
        cancel(mine.getId(), "42", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(row(mine.getId())).containsEntry("status", "CANCELLED");
    }

    @Test
    void theCancellationRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/reservations/1/cancel")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/1")).andExpect(status().isUnauthorized());
    }
}
