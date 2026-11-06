package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.respond;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.Behavior;
import lombok.RequiredArgsConstructor;

// La reconciliación con la cátedra por HTTP de verdad (el adaptador real, no el puerto simulado), contra una cátedra que
// se comporta como la real: responde 500 a GET /api/appointments si le llegan los filtros from o to (A34, hallado en la
// prueba real del 2026-11-06). Si alguien los vuelve a mandar, el vencimiento y la recuperación quedan sin comprobar
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class CatedraReconciliationOverHttpIntegrationTest {

    private static final FakeHttpServer CATEDRA = start();
    private static final Pattern DATE_FILTER = Pattern.compile("(^|&)(from|to)=");
    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    private static final String CONFIRMED_APPOINTMENT = """
            {"reservationId": 8251, "reservationProcessId": "process-1", "externalPatientId": "42",
             "appointmentDate": "2026-11-09", "startTime": "09:30:00", "endTime": "10:00:00", "professionalId": 15,
             "professionalFirstName": "Ana", "professionalLastName": "Gomez", "patientFirstName": "Juan",
             "patientLastName": "Perez", "patientPhone": "+5492615551234", "status": "CONFIRMED",
             "createdAt": "2026-11-04T13:00:00Z", "confirmedAt": "2026-11-04T13:01:00Z", "cancelledAt": null,
             "cancellationReason": null}""";

    private final JdbcClient jdbc;
    private final ReservaService reservaService;
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
        // Un solo intento: un 500 no debe alargar el test con reintentos
        registry.add("catedra.max-attempts", () -> "1");
    }

    @AfterAll
    static void stopTheFakeServer() {
        CATEDRA.close();
    }

    @BeforeEach
    void cleanEverything() {
        CATEDRA.reset();
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    // Responde como la cátedra real: 500 con from o to, y con el resto de los filtros la lista de turnos confirmados
    private Behavior likeTheRealCatedra(String appointmentsJson, int total) {
        return exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && DATE_FILTER.matcher(query).find()) {
                respond(exchange, 500, "{\"detail\":\"Failure during data access\",\"status\":500}", Map.of());
            } else {
                respond(exchange, 200, appointmentsJson, Map.of("X-Total-Count", String.valueOf(total)));
            }
        };
    }

    private ReservationProcess overdue(String processId, ReservationStatus status) {
        return repository.save(ReservationProcess.builder().userId("42").reservationProcessId(processId)
                .holdId("hold-1").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(DATE).startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .expiresAt(Instant.now().minus(Duration.ofHours(1))).patientFirstName("Juan")
                .patientLastName("Perez").createdAt(T0).updatedAt(T0).build());
    }

    private Map<String, Object> row(Long id) {
        return jdbc.sql("SELECT status, failure_reason, reservation_id FROM reservation_processes WHERE id = :id")
                .param("id", id).query().singleRow();
    }

    @Test
    void aProcessTheCatedraHasConfirmedIsRepairedInsteadOfExpiredWithoutAskingByDate() {
        ReservationProcess submitted = overdue("process-1", ReservationStatus.PHONE_SUBMITTED);
        CATEDRA.thenRespond(likeTheRealCatedra("[" + CONFIRMED_APPOINTMENT + "]", 1));

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
        assertThat(CATEDRA.requests()).singleElement().satisfies(request -> {
            assertThat(request.rawPath()).isEqualTo("/api/appointments");
            assertThat(request.rawQuery()).isEqualTo("status=CONFIRMED&externalPatientId=42&page=0&size=100");
        });
    }

    @Test
    void aProcessThatIsNotInTheAnswerOfTheCatedraIsExpired() {
        ReservationProcess queued = overdue("process-1", ReservationStatus.PHONE_QUEUED);
        CATEDRA.thenRespond(likeTheRealCatedra("[]", 0));

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.EXPIRED);
        assertThat(row(queued.getId())).containsEntry("status", "EXPIRED").containsEntry("failure_reason", "HOLD_EXPIRED");
    }

    @Test
    void theRecoveryAtStartupRepairsAProcessTheCatedraHasConfirmed() {
        ReservationProcess queued = overdue("process-1", ReservationStatus.PHONE_QUEUED);
        CATEDRA.thenRespond(likeTheRealCatedra("[" + CONFIRMED_APPOINTMENT + "]", 1));

        List<RecoveryResult> results = reservaService.recoverReservations(Instant.now());

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(queued.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
        assertThat(CATEDRA.requests()).singleElement()
                .satisfies(request -> assertThat(request.rawQuery()).doesNotContain("from=").doesNotContain("to="));
    }
}
