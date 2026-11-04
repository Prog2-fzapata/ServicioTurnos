package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Degradación con catálogo y cátedra caídos a la vez, contra PostgreSQL real. La configuración de tests apunta a los dos
// a un puerto cerrado, así que toda llamada falla enseguida. Lo propio sigue funcionando; lo que necesita a otro
// servicio responde 503 y no deja nada a medias
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ServicesDownIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservationProcessRepository repository;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private ReservationProcess seed(String processId, ReservationStatus status, String requestEventId) {
        return repository.save(ReservationProcess.builder().userId("42").reservationProcessId(processId)
                .holdId("hold-" + processId).professionalId(15L).professionalFirstName("Ana")
                .professionalLastName("Gomez").appointmentDate(LocalDate.of(2026, 11, 9))
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .requestEventId(requestEventId).expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .patientFirstName("Juan").patientLastName("Perez").createdAt(T0).updatedAt(T0).build());
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.claim("uid", "42")
                .expiresAt(Instant.now().plus(Duration.ofHours(1))))));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private Map<String, Object> row(Long id) {
        return jdbc.sql("SELECT status, version FROM reservation_processes WHERE id = :id").param("id", id).query()
                .singleRow();
    }

    // ---------- Lo propio sigue funcionando

    @Test
    void listingMyReservationsKeepsWorking() throws Exception {
        seed("p-1", ReservationStatus.CONFIRMED, null);
        seed("p-2", ReservationStatus.WAITING_PHONE, "request-1");

        asUser(get("/api/reservations")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void readingOneOfMyReservationsKeepsWorking() throws Exception {
        ReservationProcess mine = seed("p-1", ReservationStatus.CONFIRMED, null);

        asUser(get("/api/reservations/" + mine.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.getId().intValue()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // El teléfono va al outbox y lo publica el relay cuando Kafka responda: no necesita ni catálogo ni cátedra
    @Test
    void sendingThePhoneKeepsWorkingAndLeavesTheMessageInTheOutbox() throws Exception {
        ReservationProcess waiting = seed("p-1", ReservationStatus.WAITING_PHONE, "request-event-8a31");

        asUser(post("/api/reservations/" + waiting.getId() + "/phone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"+5492615551234\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PHONE_QUEUED"));

        assertThat(row(waiting.getId())).containsEntry("status", "PHONE_QUEUED");
        assertThat(count("outbox_events")).isEqualTo(1);
    }

    @Test
    void theChangeStreamKeepsWorking() throws Exception {
        asUser(get("/api/reservations/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    // Que catálogo o la cátedra no respondan no hace que el servicio deje de estar listo
    @Test
    void theServiceStillReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    // ---------- Lo que necesita a otro servicio responde 503 y no deja nada a medias

    @Test
    void startingAReservationAnswers503AndCreatesNothing() throws Exception {
        asUser(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"professionalId\": 15, \"date\": \"2026-11-30\", \"startTime\": \"09:30\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));

        assertThat(count("reservation_processes")).isZero();
    }

    @Test
    void checkingTheAvailabilityAnswers503() throws Exception {
        asUser(get("/api/availability").param("professionalId", "15").param("from", "2026-11-30")
                .param("to", "2026-11-30")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    // La cancelación va primero a la cátedra: si no responde no cambia nada y se puede repetir
    @Test
    void cancellingAnswers503AndLeavesTheReservationConfirmed() throws Exception {
        ReservationProcess confirmed = seed("p-1", ReservationStatus.CONFIRMED, null);
        Object versionBefore = row(confirmed.getId()).get("version");

        asUser(post("/api/reservations/" + confirmed.getId() + "/cancel"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));

        assertThat(row(confirmed.getId())).containsEntry("status", "CONFIRMED").containsEntry("version", versionBefore);
    }
}
