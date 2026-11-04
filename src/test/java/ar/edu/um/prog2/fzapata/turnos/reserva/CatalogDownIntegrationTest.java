package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import lombok.RequiredArgsConstructor;

// Solo el catálogo caído (la cátedra, simulada, responde): es el caso del criterio del issue. No se inician reservas ni
// se consulta disponibilidad, y ni siquiera se llega a la cátedra; en cambio cancelar, que no usa el catálogo, funciona
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class CatalogDownIntegrationTest {

    private static final FakeHttpServer CATEDRA = start();
    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");
    private static final String CANCELLED_BY_CATEDRA = """
            {"reservationId": 8251, "reservationProcessId": "p-1", "status": "CANCELLED",
             "cancelledAt": "2026-11-04T15:10:00Z"}""";

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

    // El catálogo queda en el puerto cerrado de la configuración de tests; solo la cátedra responde
    @DynamicPropertySource
    static void pointTheCatedraToTheFakeServer(DynamicPropertyRegistry registry) {
        registry.add("catedra.base-url", CATEDRA::baseUrl);
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
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private ReservationProcess confirmed() {
        return repository.save(ReservationProcess.builder().userId("42").reservationProcessId("p-1").holdId("hold-1")
                .professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 11, 9)).startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 0)).status(ReservationStatus.CONFIRMED).reservationId(8251L)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10))).patientFirstName("Juan")
                .patientLastName("Perez").createdAt(T0).updatedAt(T0).build());
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.claim("uid", "42"))));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void startingAReservationAnswers503WithoutCreatingAnythingNorTouchingTheCatedra() throws Exception {
        asUser(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"professionalId\": 15, \"date\": \"2026-11-30\", \"startTime\": \"09:30\"}"))
                .andExpect(MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));

        assertThat(count("reservation_processes")).isZero();
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void checkingTheAvailabilityAnswers503WithoutTouchingTheCatedra() throws Exception {
        asUser(get("/api/availability").param("professionalId", "15").param("from", "2026-11-30")
                .param("to", "2026-11-30"))
                .andExpect(MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));

        assertThat(CATEDRA.requestCount()).isZero();
    }

    // Cancelar solo usa la cátedra: con el catálogo caído se puede
    @Test
    void cancellingKeepsWorkingBecauseItDoesNotUseTheCatalog() throws Exception {
        ReservationProcess mine = confirmed();
        CATEDRA.thenRespond(FakeHttpServer.status(200, CANCELLED_BY_CATEDRA));

        asUser(post("/api/reservations/" + mine.getId() + "/cancel"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(CATEDRA.requestCount()).isEqualTo(1);
    }

    @Test
    void listingAndReadingMyReservationsKeepWorking() throws Exception {
        ReservationProcess mine = confirmed();

        asUser(get("/api/reservations"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$[0].id").value(mine.getId().intValue()));
        asUser(get("/api/reservations/" + mine.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(CATEDRA.requestCount()).isZero();
    }
}
