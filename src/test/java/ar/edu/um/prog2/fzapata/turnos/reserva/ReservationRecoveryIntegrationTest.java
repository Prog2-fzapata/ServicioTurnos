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
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// La recuperación al arrancar contra PostgreSQL real, con una cátedra simulada. El arranque automático está apagado
// (configuración de tests): cada test dispara la recuperación a mano con el momento en que "arrancó" la instancia
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationRecoveryIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-11-04T14:00:00Z");
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

    private ReservationProcess seed(String processId, String holdId, ReservationStatus status, Instant createdAt) {
        return repository.save(ReservationProcess.builder().userId("42").reservationProcessId(processId)
                .holdId(holdId).professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(DATE).startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10))).patientFirstName("Juan")
                .patientLastName("Perez").createdAt(createdAt).updatedAt(createdAt).build());
    }

    private ReservationProcess orphan() {
        return seed(null, null, ReservationStatus.HOLD_REQUESTED, STARTED_AT.minus(Duration.ofMinutes(30)));
    }

    private ReservationProcess withPhone(String processId, ReservationStatus status) {
        return seed(processId, "hold-" + processId, status, T0);
    }

    private void catedraConfirms(String processId, long reservationId) {
        when(catedra.findAppointments(any(), anyInt(), anyInt())).thenReturn(new AppointmentPage(List.of(
                CatedraAppointment.builder().reservationId(reservationId).reservationProcessId(processId)
                        .status(AppointmentStatus.CONFIRMED).build()), 1));
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
    void anOrphanHoldRequestFailsWithTheLostResponseReason() {
        ReservationProcess orphan = orphan();

        List<RecoveryResult> results = reservaService.recoverReservations(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.ORPHAN_FAILED);
        assertThat(row(orphan.getId())).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "HOLD_RESULT_UNKNOWN");
    }

    // Tomcat atiende antes de que se dispare el evento de arranque: un pedido en curso crea su proceso después de que
    // arrancó el contexto, y fallarlo sería matar un pedido que está andando
    @Test
    void aHoldRequestCreatedAfterTheInstanceStartedIsAnInFlightRequestAndIsLeftAlone() {
        ReservationProcess inFlight = seed(null, null, ReservationStatus.HOLD_REQUESTED, STARTED_AT.plusSeconds(2));

        reservaService.recoverReservations(STARTED_AT);

        assertThat(status(inFlight.getId())).isEqualTo("HOLD_REQUESTED");
    }

    @Test
    void theProcessesThatAreNotDoubtfulAreNeverTouched() {
        ReservationProcess held = seed("process-1", "hold-1", ReservationStatus.HELD, T0);
        ReservationProcess waiting = seed("process-2", "hold-2", ReservationStatus.WAITING_PHONE, T0);
        ReservationProcess confirmed = seed("process-3", "hold-3", ReservationStatus.CONFIRMED, T0);
        ReservationProcess failed = seed("process-4", "hold-4", ReservationStatus.FAILED, T0);
        ReservationProcess expired = seed("process-5", "hold-5", ReservationStatus.EXPIRED, T0);
        ReservationProcess withHold = seed(null, "hold-6", ReservationStatus.HOLD_REQUESTED, T0);

        assertThat(reservaService.recoverReservations(STARTED_AT)).isEmpty();

        assertThat(status(held.getId())).isEqualTo("HELD");
        assertThat(status(waiting.getId())).isEqualTo("WAITING_PHONE");
        assertThat(status(confirmed.getId())).isEqualTo("CONFIRMED");
        assertThat(status(failed.getId())).isEqualTo("FAILED");
        assertThat(status(expired.getId())).isEqualTo("EXPIRED");
        assertThat(status(withHold.getId())).isEqualTo("HOLD_REQUESTED");
    }

    // La confirmación llegó a la cátedra y su evento nunca llegó a turnos: se repara apenas arranca el servicio
    @Test
    void aProcessTheCatedraHasConfirmedIsRepairedWithItsReservationId() {
        ReservationProcess submitted = withPhone("process-1", ReservationStatus.PHONE_SUBMITTED);
        catedraConfirms("process-1", 8251);

        List<RecoveryResult> results = reservaService.recoverReservations(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.CONFIRMED_BY_CATEDRA);
        assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
    }

    @Test
    void aQueuedProcessTheCatedraHasConfirmedIsRepairedToo() {
        ReservationProcess queued = withPhone("process-1", ReservationStatus.PHONE_QUEUED);
        catedraConfirms("process-1", 8251);

        reservaService.recoverReservations(STARTED_AT);

        assertThat(row(queued.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
    }

    @Test
    void aProcessTheCatedraDoesNotHaveConfirmedStaysAsItIs() {
        ReservationProcess submitted = withPhone("process-1", ReservationStatus.PHONE_SUBMITTED);

        List<RecoveryResult> results = reservaService.recoverReservations(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.STILL_OPEN);
        assertThat(status(submitted.getId())).isEqualTo("PHONE_SUBMITTED");
    }

    // Sin la cátedra no se toca lo que depende de ella, pero los huérfanos no la necesitan
    @Test
    void ifTheCatedraCannotBeAskedThePhoneProcessesStayAndTheOrphansAreStillFailed() {
        ReservationProcess orphan = orphan();
        ReservationProcess submitted = withPhone("process-1", ReservationStatus.PHONE_SUBMITTED);
        when(catedra.findAppointments(any(), anyInt(), anyInt()))
                .thenThrow(new CatedraNotReachedException("sin conexión", null));

        List<RecoveryResult> results = reservaService.recoverReservations(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.ORPHAN_FAILED,
                RecoveryOutcome.UNVERIFIED);
        assertThat(status(orphan.getId())).isEqualTo("FAILED");
        assertThat(status(submitted.getId())).isEqualTo("PHONE_SUBMITTED");
    }

    @Test
    void recoveringShowsUpOnTheStreamOfTheOwner() throws Exception {
        ReservationProcess orphan = orphan();
        MvcResult stream = openStream();

        reservaService.recoverReservations(STARTED_AT);

        assertThat(stream.getResponse().getContentAsString()).contains("event:reservation-updated",
                "\"id\":" + orphan.getId(), "\"status\":\"FAILED\"");
    }

    // Repetirla (otro arranque) no cambia nada: lo ya cerrado ya no es candidato y lo confirmado tampoco
    @Test
    void recoveringTwiceChangesNothingTheSecondTime() {
        orphan();
        ReservationProcess submitted = withPhone("process-1", ReservationStatus.PHONE_SUBMITTED);
        catedraConfirms("process-1", 8251);
        reservaService.recoverReservations(STARTED_AT);

        assertThat(reservaService.recoverReservations(STARTED_AT)).isEmpty();
        assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
    }

    // El consumer de Kafka repasa su atraso mientras corre la recuperación: gana el que guarda primero y el otro ve que
    // ya está confirmado. Siempre queda la misma reserva confirmada, sin estados mezclados
    @Test
    void theRecoveryAndTheConfirmationEventRacingLeaveAConsistentProcess() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                String processId = "process-race-" + round;
                long reservationId = 9000L + round;
                ReservationProcess submitted = withPhone(processId, ReservationStatus.PHONE_SUBMITTED);
                doReturn(new AppointmentPage(List.of(CatedraAppointment.builder().reservationId(reservationId)
                        .reservationProcessId(processId).status(AppointmentStatus.CONFIRMED).build()), 1))
                        .when(catedra).findAppointments(any(), anyInt(), anyInt());
                CountDownLatch gate = new CountDownLatch(1);
                Future<?> recovery = pool.submit(() -> {
                    gate.await();
                    return reservaService.recoverReservations(STARTED_AT);
                });
                int number = round;
                Future<?> confirmation = pool.submit(() -> {
                    gate.await();
                    return reservaService.processEvent(new CatedraEvent("confirmed-race-" + number,
                            "AppointmentConfirmed", processId, reservationId, null, null));
                });
                gate.countDown();
                recovery.get(30, TimeUnit.SECONDS);
                confirmation.get(30, TimeUnit.SECONDS);

                assertThat(row(submitted.getId())).containsEntry("status", "CONFIRMED")
                        .containsEntry("reservation_id", reservationId);
                jdbc.sql("DELETE FROM reservation_processes").update();
                jdbc.sql("DELETE FROM processed_events").update();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
