package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Contra PostgreSQL real: el bloqueo optimista y las carreras entre hilos no se pueden probar con mocks.
// Intentos de sobra para que ningún hilo se quede sin turno en la carrera de varios
@SpringBootTest(properties = "reservation.conflict-max-attempts=10")
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationProcessPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-02T13:00:00Z");

    private final ReservationProcessRepository repository;
    private final ApplyReservationTransitionUseCase applyTransition;
    private final JdbcClient jdbc;

    private ReservationProcess newProcess(ReservationStatus status) {
        return ReservationProcess.builder().reservationProcessId(null).userId("42").professionalId(15L)
                .professionalFirstName("Ana").professionalLastName("Gomez").appointmentDate(LocalDate.of(2026, 11, 9))
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .patientFirstName("Juan").patientLastName("Perez").createdAt(T0).updatedAt(T0).build();
    }

    private ReservationProcess stored(ReservationStatus status) {
        return repository.save(newProcess(status));
    }

    private List<TransitionResult> inParallel(List<Callable<TransitionResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<TransitionResult>> futures = new ArrayList<>();
            for (Callable<TransitionResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    return task.call();
                }));
            }
            // Todos arrancan juntos, para que choquen de verdad
            gate.countDown();
            List<TransitionResult> results = new ArrayList<>();
            for (Future<TransitionResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<TransitionResult> apply(Long id, ReservationStatus target) {
        return () -> applyTransition.applyTransition(id, ReservationTransition.to(target)).orElseThrow();
    }

    @BeforeEach
    void emptyTheTables() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    @Test
    void savesANewProcessWithAnIdAndTheFirstVersion() {
        ReservationProcess saved = repository.save(newProcess(ReservationStatus.HOLD_REQUESTED));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getReservationProcessId()).isNull();
        assertThat(repository.findById(saved.getId())).get().usingRecursiveComparison().isEqualTo(saved);
    }

    @Test
    void storesEveryFieldAndTheStatusAsText() {
        ReservationProcess process = newProcess(ReservationStatus.PHONE_SUBMITTED);
        process.setReservationProcessId("process-1");
        process.setHoldId("hold-1");
        process.setExpiresAt(Instant.parse("2026-11-02T13:10:00Z"));
        process.setPatientPhone("+5492615551234");
        process.setRequestEventId("event-1");
        process.setReservationId(99L);
        process.setFailureReason("motivo");
        process.setCancellationReason("razón");
        process.setCancelledAt(Instant.parse("2026-11-02T15:00:00Z"));

        ReservationProcess saved = repository.save(process);

        assertThat(repository.findById(saved.getId())).get().usingRecursiveComparison().isEqualTo(saved);
        assertThat(jdbc.sql("SELECT status FROM reservation_processes WHERE id = :id").param("id", saved.getId())
                .query(String.class).single()).isEqualTo("PHONE_SUBMITTED");
    }

    @Test
    void aMissingProcessIsEmpty() {
        assertThat(repository.findById(123456L)).isEmpty();
    }

    @Test
    void everyUpdateIncrementsTheVersion() {
        ReservationProcess saved = stored(ReservationStatus.HELD);

        saved.setStatus(ReservationStatus.WAITING_PHONE);
        ReservationProcess updated = repository.save(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(repository.findById(saved.getId())).get().extracting(ReservationProcess::getStatus)
                .isEqualTo(ReservationStatus.WAITING_PHONE);
    }

    @Test
    void aStaleCopyCannotOverwriteANewerOne() {
        ReservationProcess saved = stored(ReservationStatus.HELD);
        ReservationProcess first = repository.findById(saved.getId()).orElseThrow();
        ReservationProcess second = repository.findById(saved.getId()).orElseThrow();

        first.setStatus(ReservationStatus.WAITING_PHONE);
        repository.save(first);
        second.setStatus(ReservationStatus.EXPIRED);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(ReservationConflictException.class)
                .hasMessageContaining(String.valueOf(saved.getId()));
        assertThat(repository.findById(saved.getId())).get().extracting(ReservationProcess::getStatus)
                .isEqualTo(ReservationStatus.WAITING_PHONE);
    }

    @Test
    void theUseCaseAppliesAStepAndPersistsItWithTheNewVersion() {
        Long id = stored(ReservationStatus.HELD).getId();

        TransitionResult result = applyTransition.applyTransition(id, ReservationTransition.builder()
                .target(ReservationStatus.WAITING_PHONE).expiresAt(Instant.parse("2026-11-02T13:10:00Z")).build())
                .orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.APPLIED);
        ReservationProcess persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(persisted.getExpiresAt()).isEqualTo(Instant.parse("2026-11-02T13:10:00Z"));
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getUpdatedAt()).isAfter(T0);
        assertThat(persisted.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void aRejectedStepLeavesTheRowUntouched() {
        Long id = stored(ReservationStatus.EXPIRED).getId();

        TransitionResult result = applyTransition.applyTransition(id, ReservationTransition.builder()
                .target(ReservationStatus.CONFIRMED).reservationId(5L).build()).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.REJECTED);
        ReservationProcess persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(persisted.getReservationId()).isNull();
        assertThat(persisted.getVersion()).isZero();
    }

    // El mismo evento entregado dos veces a la vez: uno cambia el estado y el otro encuentra el cambio hecho
    @Test
    void theSameStepTwiceAtTheSameTimeIsAppliedOnlyOnce() throws Exception {
        Long id = stored(ReservationStatus.HELD).getId();

        List<TransitionResult> results = inParallel(List.of(apply(id, ReservationStatus.WAITING_PHONE),
                apply(id, ReservationStatus.WAITING_PHONE)));

        assertThat(results).extracting(TransitionResult::outcome)
                .containsExactlyInAnyOrder(TransitionOutcome.APPLIED, TransitionOutcome.ALREADY_IN_STATE);
        assertThat(repository.findById(id)).get().extracting(ReservationProcess::getVersion).isEqualTo(1L);
    }

    // Cuatro respuestas distintas a la vez sobre el mismo proceso: ninguna actualización se pierde, y los cambios
    // aplicados forman una cadena válida, uno detrás del otro, como si se hubieran dado en orden
    @Test
    void differentStepsAtTheSameTimeNeverLoseAnUpdate() throws Exception {
        for (int round = 0; round < 5; round++) {
            Long id = stored(ReservationStatus.PHONE_SUBMITTED).getId();

            List<TransitionResult> results = inParallel(List.of(apply(id, ReservationStatus.CONFIRMED),
                    apply(id, ReservationStatus.WAITING_PHONE), apply(id, ReservationStatus.EXPIRED),
                    apply(id, ReservationStatus.FAILED)));

            List<TransitionResult> applied = results.stream()
                    .filter(result -> result.outcome() == TransitionOutcome.APPLIED)
                    .sorted(Comparator.comparing(result -> result.process().getVersion())).toList();
            ReservationProcess persisted = repository.findById(id).orElseThrow();
            assertThat(applied).isNotEmpty();
            // Cada cambio aplicado sumó exactamente una versión: ninguno pisó a otro
            assertThat(persisted.getVersion()).isEqualTo((long) applied.size());
            assertThat(applied).extracting(result -> result.process().getVersion())
                    .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, applied.size()).boxed().toList());
            // La cadena empieza donde estaba el proceso y cada paso sale de donde terminó el anterior
            assertThat(applied.get(0).from()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
            for (int i = 1; i < applied.size(); i++) {
                assertThat(applied.get(i).from()).isEqualTo(applied.get(i - 1).target());
            }
            assertThat(persisted.getStatus()).isEqualTo(applied.get(applied.size() - 1).target());
            // Lo que no se aplicó se ignoró: nunca quedó un estado que el diagrama no permite
            assertThat(applied).allSatisfy(result -> assertThat(result.from().canTransitionTo(result.target())).isTrue());
        }
    }

    // ---------- Procesos a expirar

    private ReservationProcess storedExpiring(ReservationStatus status, Instant expiresAt) {
        ReservationProcess process = newProcess(status);
        process.setExpiresAt(expiresAt);
        return repository.save(process);
    }

    @Test
    void findExpirableReturnsOnlyOpenProcessesWithAnExpirationAtOrBeforeTheLimit() {
        Instant limit = Instant.parse("2026-11-04T14:00:00Z");
        ReservationProcess held = storedExpiring(ReservationStatus.HELD, limit.minusSeconds(600));
        ReservationProcess waiting = storedExpiring(ReservationStatus.WAITING_PHONE, limit.minusSeconds(300));
        ReservationProcess queued = storedExpiring(ReservationStatus.PHONE_QUEUED, limit.minusSeconds(60));
        ReservationProcess submitted = storedExpiring(ReservationStatus.PHONE_SUBMITTED, limit);
        // Estos no: todavía no vencieron, no tienen vencimiento, ya están confirmados o ya terminaron
        storedExpiring(ReservationStatus.HELD, limit.plusSeconds(1));
        storedExpiring(ReservationStatus.HOLD_REQUESTED, null);
        storedExpiring(ReservationStatus.HELD, null);
        storedExpiring(ReservationStatus.CONFIRMED, limit.minusSeconds(600));
        storedExpiring(ReservationStatus.EXPIRED, limit.minusSeconds(600));
        storedExpiring(ReservationStatus.FAILED, limit.minusSeconds(600));
        storedExpiring(ReservationStatus.CANCELLED, limit.minusSeconds(600));

        List<ReservationProcess> found = repository.findExpirable(limit, 100);

        assertThat(found).extracting(ReservationProcess::getId).containsExactly(held.getId(), waiting.getId(),
                queued.getId(), submitted.getId());
    }

    @Test
    void findExpirableGoesFromTheOldestExpirationToTheNewestAndBreaksTiesById() {
        Instant limit = Instant.parse("2026-11-04T14:00:00Z");
        ReservationProcess later = storedExpiring(ReservationStatus.HELD, limit.minusSeconds(60));
        ReservationProcess tiedFirst = storedExpiring(ReservationStatus.HELD, limit.minusSeconds(600));
        ReservationProcess tiedSecond = storedExpiring(ReservationStatus.WAITING_PHONE, limit.minusSeconds(600));

        assertThat(repository.findExpirable(limit, 100)).extracting(ReservationProcess::getId)
                .containsExactly(tiedFirst.getId(), tiedSecond.getId(), later.getId());
    }

    // Una tanda acotada: nunca trae más de lo que la tarea puede cerrar en una vuelta
    @Test
    void findExpirableNeverReturnsMoreThanTheLimit() {
        Instant limit = Instant.parse("2026-11-04T14:00:00Z");
        ReservationProcess oldest = storedExpiring(ReservationStatus.HELD, limit.minusSeconds(900));
        ReservationProcess second = storedExpiring(ReservationStatus.HELD, limit.minusSeconds(600));
        storedExpiring(ReservationStatus.HELD, limit.minusSeconds(300));

        assertThat(repository.findExpirable(limit, 2)).extracting(ReservationProcess::getId)
                .containsExactly(oldest.getId(), second.getId());
    }

    @Test
    void findExpirableWithNothingOverdueIsEmpty() {
        Instant limit = Instant.parse("2026-11-04T14:00:00Z");
        storedExpiring(ReservationStatus.HELD, limit.plusSeconds(60));

        assertThat(repository.findExpirable(limit, 100)).isEmpty();
    }
}
