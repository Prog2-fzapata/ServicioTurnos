package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.respond;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
// se comporta como la real: GET /api/appointments devuelve solo los turnos cuya fecha cae entre from y to (inclusivos)
// y informa el total de los que cumplen el filtro en X-Total-Count. El vencimiento y la recuperación piden el día del
// turno del proceso, así que la respuesta es corta aunque el paciente tenga muchos turnos confirmados en otras fechas
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class CatedraReconciliationOverHttpIntegrationTest {

    private static final FakeHttpServer CATEDRA = start();
    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);

    private final JdbcClient jdbc;
    private final ReservaService reservaService;
    private final ReservationProcessRepository repository;

    private static String confirmedAppointment(String processId, long reservationId, LocalDate date) {
        return """
                {"reservationId": %d, "reservationProcessId": "%s", "externalPatientId": "42",
                 "appointmentDate": "%s", "startTime": "09:30:00", "endTime": "10:00:00", "professionalId": 15,
                 "professionalFirstName": "Ana", "professionalLastName": "Gomez", "patientFirstName": "Juan",
                 "patientLastName": "Perez", "patientPhone": "+5492615551234", "status": "CONFIRMED",
                 "createdAt": "2026-11-04T13:00:00Z", "confirmedAt": "2026-11-04T13:01:00Z", "cancelledAt": null,
                 "cancellationReason": null}""".formatted(reservationId, processId, date);
    }

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

    // Un turno de la cátedra: su fecha y su JSON
    private record SimulatedAppointment(LocalDate date, String json) {
    }

    private static SimulatedAppointment appointmentOn(LocalDate date, String processId, long reservationId) {
        return new SimulatedAppointment(date, confirmedAppointment(processId, reservationId, date));
    }

    // Responde como la cátedra real: de los turnos confirmados del paciente devuelve solo los que caen entre from y to
    // (si vienen), de a una página, y en X-Total-Count el total de los que cumplen el filtro
    private Behavior likeTheRealCatedra(List<SimulatedAppointment> confirmed) {
        return exchange -> {
            Map<String, String> params = queryParams(exchange.getRequestURI().getRawQuery());
            List<SimulatedAppointment> matching = confirmed.stream()
                    .filter(appointment -> !params.containsKey("from")
                            || !appointment.date().isBefore(LocalDate.parse(params.get("from"))))
                    .filter(appointment -> !params.containsKey("to")
                            || !appointment.date().isAfter(LocalDate.parse(params.get("to"))))
                    .toList();
            int size = Integer.parseInt(params.getOrDefault("size", "20"));
            String page = matching.stream().limit(size).map(SimulatedAppointment::json)
                    .collect(Collectors.joining(",", "[", "]"));
            respond(exchange, 200, page, Map.of("X-Total-Count", String.valueOf(matching.size())));
        };
    }

    private static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                String[] keyAndValue = pair.split("=", 2);
                params.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return params;
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
    void aProcessTheCatedraHasConfirmedIsRepairedInsteadOfExpiredAskingForTheDayOfTheAppointment() {
        ReservationProcess submitted = overdue("process-1", ReservationStatus.PHONE_SUBMITTED);
        CATEDRA.thenRespond(likeTheRealCatedra(List.of(appointmentOn(DATE, "process-1", 8251))));

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
        assertThat(CATEDRA.requests()).singleElement().satisfies(request -> {
            assertThat(request.rawPath()).isEqualTo("/api/appointments");
            assertThat(request.rawQuery())
                    .isEqualTo("status=CONFIRMED&externalPatientId=42&from=2026-11-09&to=2026-11-09&page=0&size=100");
        });
    }

    @Test
    void aProcessThatIsNotInTheAnswerOfTheCatedraIsExpired() {
        ReservationProcess queued = overdue("process-1", ReservationStatus.PHONE_QUEUED);
        CATEDRA.thenRespond(likeTheRealCatedra(List.of()));

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.EXPIRED);
        assertThat(row(queued.getId())).containsEntry("status", "EXPIRED").containsEntry("failure_reason", "HOLD_EXPIRED");
    }

    @Test
    void theRecoveryAtStartupRepairsAProcessTheCatedraHasConfirmed() {
        ReservationProcess queued = overdue("process-1", ReservationStatus.PHONE_QUEUED);
        CATEDRA.thenRespond(likeTheRealCatedra(List.of(appointmentOn(DATE, "process-1", 8251))));

        List<RecoveryResult> results = reservaService.recoverReservations(Instant.now());

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(queued.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
        assertThat(CATEDRA.requests()).singleElement()
                .satisfies(request -> assertThat(request.rawQuery()).contains("from=2026-11-09&to=2026-11-09"));
    }

    // Con la cota por paciente, estos 120 turnos de otras fechas llenarían la página y el proceso (el último) no
    // aparecería: la respuesta sería incompleta y no se actuaría. Pidiendo solo su día, llega uno
    @Test
    void aPatientWithMoreConfirmedAppointmentsThanAPageIsStillResolvedBecauseOnlyTheDayOfTheProcessIsAsked() {
        ReservationProcess submitted = overdue("process-1", ReservationStatus.PHONE_SUBMITTED);
        List<SimulatedAppointment> confirmed = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            confirmed.add(appointmentOn(DATE.plusDays(1 + i), "process-other-" + i, 9000L + i));
        }
        confirmed.add(appointmentOn(DATE, "process-1", 8251));
        CATEDRA.thenRespond(likeTheRealCatedra(confirmed));

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome)
                .containsExactly(ExpirationOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
    }
}
