package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// El vencimiento contra PostgreSQL real, con una cátedra simulada. La tarea automática está apagada (configuración de
// tests): cada test dispara una vuelta a mano. Los vencimientos son de hace una hora, de sobra más que el margen de 60 s
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationExpirationIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservaService reservaService;
    private final ReservationProcessRepository repository;

    @MockitoBean
    private CatedraApiClient catedra;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
        when(catedra.findAppointments(any(), anyInt(), anyInt())).thenReturn(new AppointmentPage(List.of(), 0));
    }

    private ReservationProcess seed(String processId, ReservationStatus status, Instant expiresAt) {
        return repository.save(ReservationProcess.builder().userId("42").reservationProcessId(processId)
                .holdId("hold-1").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(DATE).startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .expiresAt(expiresAt).patientFirstName("Juan").patientLastName("Perez").createdAt(T0).updatedAt(T0)
                .build());
    }

    private ReservationProcess overdue(String processId, ReservationStatus status) {
        return seed(processId, status, Instant.now().minus(Duration.ofHours(1)));
    }

    private Map<String, Object> row(Long id) {
        return jdbc.sql("SELECT status, failure_reason, reservation_id FROM reservation_processes WHERE id = :id")
                .param("id", id).query().singleRow();
    }

    private String status(Long id) {
        return (String) row(id).get("status");
    }

    private MvcResult openStream() throws Exception {
        return mockMvc.perform(get("/api/reservations/stream").accept(MediaType.TEXT_EVENT_STREAM)
                .with(jwt().jwt(token -> token.claim("uid", "42").expiresAt(Instant.now().plus(Duration.ofHours(1))))))
                .andExpect(request().asyncStarted()).andReturn();
    }

    @Test
    void theRoundExpiresTheOverdueProcessesAndLeavesTheRestAlone() {
        ReservationProcess held = overdue("process-1", ReservationStatus.HELD);
        ReservationProcess waiting = overdue("process-2", ReservationStatus.WAITING_PHONE);
        ReservationProcess queued = overdue("process-3", ReservationStatus.PHONE_QUEUED);
        ReservationProcess submitted = overdue("process-4", ReservationStatus.PHONE_SUBMITTED);
        // Dentro del margen, sin vencimiento, confirmadas o terminadas: no se tocan
        ReservationProcess withinGrace = seed("process-5", ReservationStatus.HELD, Instant.now().minusSeconds(10));
        ReservationProcess notExpiredYet = seed("process-6", ReservationStatus.HELD,
                Instant.now().plus(Duration.ofMinutes(5)));
        ReservationProcess requested = seed("process-7", ReservationStatus.HOLD_REQUESTED, null);
        ReservationProcess confirmed = overdue("process-8", ReservationStatus.CONFIRMED);
        ReservationProcess failed = overdue("process-9", ReservationStatus.FAILED);

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome).containsOnly(ExpirationOutcome.EXPIRED).hasSize(4);
        for (ReservationProcess expired : List.of(held, waiting, queued, submitted)) {
            assertThat(row(expired.getId())).containsEntry("status", "EXPIRED")
                    .containsEntry("failure_reason", "HOLD_EXPIRED");
        }
        assertThat(status(withinGrace.getId())).isEqualTo("HELD");
        assertThat(status(notExpiredYet.getId())).isEqualTo("HELD");
        assertThat(status(requested.getId())).isEqualTo("HOLD_REQUESTED");
        assertThat(status(confirmed.getId())).isEqualTo("CONFIRMED");
        assertThat(status(failed.getId())).isEqualTo("FAILED");
    }

    @Test
    void theNextRoundHasNothingLeftToDo() {
        overdue("process-1", ReservationStatus.HELD);

        reservaService.expireOverdueReservations();

        assertThat(reservaService.expireOverdueReservations()).isEmpty();
    }

    @Test
    void expiringShowsUpOnTheStreamOfItsOwner() throws Exception {
        ReservationProcess held = overdue("process-1", ReservationStatus.HELD);
        MvcResult stream = openStream();

        reservaService.expireOverdueReservations();

        assertThat(stream.getResponse().getContentAsString()).contains("event:reservation-updated",
                "\"id\":" + held.getId(), "\"status\":\"EXPIRED\"", "\"expiresAt\":null");
    }

    // La confirmación llegó a la cátedra y el evento todavía no a turnos: se repara en vez de expirar
    @Test
    void aProcessTheCatedraHasConfirmedIsRepairedAndNeverExpired() {
        ReservationProcess submitted = overdue("process-1", ReservationStatus.PHONE_SUBMITTED);
        when(catedra.findAppointments(any(), anyInt(), anyInt())).thenReturn(new AppointmentPage(List.of(
                CatedraAppointment.builder().reservationId(8251L).reservationProcessId("process-1")
                        .status(AppointmentStatus.CONFIRMED).build()), 1));

        List<ExpirationResult> results = reservaService.expireOverdueReservations();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
    }

    // Ante la duda no se expira: con la cátedra sin responder el proceso queda para la vuelta siguiente
    @Test
    void ifTheCatedraCannotBeAskedTheProcessIsKeptAndTheNextRoundTriesAgain() {
        ReservationProcess submitted = overdue("process-1", ReservationStatus.PHONE_SUBMITTED);
        ReservationProcess held = overdue("process-2", ReservationStatus.HELD);
        when(catedra.findAppointments(any(), anyInt(), anyInt()))
                .thenThrow(new CatedraNotReachedException("sin conexión", null));

        List<ExpirationResult> first = reservaService.expireOverdueReservations();

        assertThat(first).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.UNVERIFIED,
                ExpirationOutcome.EXPIRED);
        assertThat(status(submitted.getId())).isEqualTo("PHONE_SUBMITTED");
        assertThat(status(held.getId())).isEqualTo("EXPIRED");

        doReturn(new AppointmentPage(List.of(), 0)).when(catedra).findAppointments(any(), anyInt(), anyInt());
        reservaService.expireOverdueReservations();

        assertThat(status(submitted.getId())).isEqualTo("EXPIRED");
    }

    // El evento de la cátedra y la tarea llevan al mismo estado final y el segundo no cambia nada
    @Test
    void theExpiredEventAfterTheTaskChangesNothing() {
        ReservationProcess held = overdue("process-1", ReservationStatus.HELD);
        reservaService.expireOverdueReservations();

        var result = reservaService.processEvent(new CatedraEvent("process-event-1", "AppointmentProcessExpired",
                "process-1", null, "HOLD_EXPIRED", null));

        assertThat(result.outcome()).isEqualTo(EventOutcome.PROCESSED);
        assertThat(result.transition().outcome().name()).isEqualTo("ALREADY_IN_STATE");
        assertThat(status(held.getId())).isEqualTo("EXPIRED");
    }

    @Test
    void theTaskAfterTheExpiredEventHasNothingToDo() {
        ReservationProcess held = overdue("process-1", ReservationStatus.HELD);

        reservaService.processEvent(new CatedraEvent("process-event-1", "AppointmentProcessExpired", "process-1", null,
                "HOLD_EXPIRED", null));

        assertThat(reservaService.expireOverdueReservations()).isEmpty();
        assertThat(row(held.getId())).containsEntry("status", "EXPIRED").containsEntry("failure_reason", "HOLD_EXPIRED");
    }

    // Lo que el margen y la consulta previa buscan evitar: un EXPIRED es final y una confirmación tardía no lo reabre
    @Test
    void aLateConfirmationNeverReopensAnExpiredProcess() {
        ReservationProcess submitted = overdue("process-1", ReservationStatus.PHONE_SUBMITTED);
        reservaService.expireOverdueReservations();

        reservaService.processEvent(new CatedraEvent("confirmed-1", "AppointmentConfirmed", "process-1", 8251L, null,
                null));

        assertThat(row(submitted.getId())).containsEntry("status", "EXPIRED").containsEntry("reservation_id", null);
    }

    // La tarea y el evento de confirmación corren a la vez sobre el mismo proceso: gana el que guarda primero y el otro
    // relee y decide de nuevo. Nunca queda un estado mezclado
    @Test
    void theTaskAndAConfirmationRacingLeaveAConsistentProcess() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                ReservationProcess submitted = overdue("process-race-" + round, ReservationStatus.PHONE_SUBMITTED);
                CountDownLatch gate = new CountDownLatch(1);
                Future<?> expiration = pool.submit(() -> {
                    gate.await();
                    return reservaService.expireOverdueReservations();
                });
                String processId = submitted.getReservationProcessId();
                int number = round;
                Future<?> confirmation = pool.submit(() -> {
                    gate.await();
                    return reservaService.processEvent(new CatedraEvent("confirmed-race-" + number,
                            "AppointmentConfirmed", processId, 9000L + number, null, null));
                });
                gate.countDown();
                expiration.get(30, TimeUnit.SECONDS);
                confirmation.get(30, TimeUnit.SECONDS);

                Map<String, Object> result = row(submitted.getId());
                if ("CONFIRMED".equals(result.get("status"))) {
                    assertThat(result).containsEntry("reservation_id", 9000L + round).containsEntry("failure_reason", null);
                } else {
                    assertThat(result).containsEntry("status", "EXPIRED").containsEntry("failure_reason", "HOLD_EXPIRED")
                            .containsEntry("reservation_id", null);
                }
                jdbc.sql("DELETE FROM reservation_processes").update();
                jdbc.sql("DELETE FROM processed_events").update();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
