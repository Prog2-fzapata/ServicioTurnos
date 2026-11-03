package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ar.edu.um.prog2.fzapata.turnos.KafkaTestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ProcessedEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ProcessedEventRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Kafka y PostgreSQL reales. Sin @Transactional en la clase: el listener corre en otro hilo y las transacciones de los
// casos de uso deben confirmarse de verdad. Cada test usa eventos y procesos propios
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "catedra.kafka.max-retries=2",
        "catedra.kafka.initial-backoff=20ms"})
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class CatedraEventListenerIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final Instant T0 = Instant.parse("2026-11-03T13:00:00Z");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final Environment environment;
    private final JdbcClient jdbc;

    @MockitoSpyBean
    private ReservationProcessRepository reservationProcessRepository;

    @MockitoSpyBean
    private ProcessedEventRepository processedEventRepository;

    @MockitoSpyBean
    private CatedraEventMapper catedraEventMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private String topic() {
        return environment.getRequiredProperty("catedra.kafka.appointment-actions-topic");
    }

    // La message key es el reservationProcessId, como publica la cátedra
    private void send(String processId, String payload) {
        kafkaTemplate.send(topic(), processId, payload);
        kafkaTemplate.flush();
    }

    private String event(String eventId, String eventType, String processId, String extraFields) {
        return """
                {"eventId":"%s","eventType":"%s","groupId":"fzapata","reservationProcessId":"%s","holdId":"hold-1",
                 "occurredAt":"2026-11-03T13:30:00Z","schemaVersion":1%s}""".formatted(eventId, eventType, processId,
                extraFields.isEmpty() ? "" : "," + extraFields);
    }

    private ReservationProcess seed(String processId, ReservationStatus status) {
        return reservationProcessRepository.save(ReservationProcess.builder().userId("42")
                .reservationProcessId(processId).holdId("hold-1").professionalId(15L).professionalFirstName("Ana")
                .professionalLastName("Gomez").appointmentDate(LocalDate.of(2026, 11, 9))
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .expiresAt(Instant.parse("2026-11-03T13:10:00Z")).patientFirstName("Juan").patientLastName("Perez")
                .createdAt(T0).updatedAt(T0).build());
    }

    // Mensajes ya leídos y confirmados del grupo (el topic tiene una sola partición)
    private long committedOffset() throws Exception {
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            String group = environment.getRequiredProperty("spring.kafka.consumer.group-id");
            return admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS)
                    .entrySet().stream()
                    .filter(entry -> entry.getKey().topic().equals(topic()))
                    .map(Map.Entry::getValue)
                    .mapToLong(OffsetAndMetadata::offset)
                    .sum();
        }
    }

    private void waitUntil(String description, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Se esperaba: " + description);
    }

    private boolean offsetReached(long offset) {
        try {
            return committedOffset() >= offset;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> row(String processId) {
        return jdbc.sql("""
                SELECT status, request_event_id, reservation_id, failure_reason, cancellation_reason, version,
                       cancelled_at
                FROM reservation_processes WHERE reservation_process_id = :id""").param("id", processId).query()
                .singleRow();
    }

    private List<String> processedEventIds() {
        return jdbc.sql("SELECT event_id FROM processed_events ORDER BY event_id").query(String.class).list();
    }

    @Test
    void thePhoneRequestStoresItsEventIdAsTheRequestEventIdWithoutChangingTheStatus() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.WAITING_PHONE);

        send("process-1", event("request-event-8a31", "AdditionalInformationRequested", "process-1",
                "\"requiredField\":\"PHONE_NUMBER\",\"expiresAt\":\"2026-11-03T13:09:00Z\""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        // Si el offset ya está confirmado, la transacción tuvo que confirmarse antes
        assertThat(row("process-1")).containsEntry("status", "WAITING_PHONE")
                .containsEntry("request_event_id", "request-event-8a31");
        assertThat(processedEventIds()).containsExactly("request-event-8a31");
    }

    // La cátedra publica el pedido de teléfono apenas confirma el hold: el proceso puede seguir en HELD
    @Test
    void thePhoneRequestCanArriveWhileTheProcessIsStillHeld() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.HELD);

        send("process-1", event("request-event-8a31", "AdditionalInformationRequested", "process-1", ""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "WAITING_PHONE")
                .containsEntry("request_event_id", "request-event-8a31");
    }

    @Test
    void aConfirmationConfirmsTheReservationAndKeepsTheReservationIdOfTheCatedra() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
    }

    @Test
    void aRejectedPhoneGoesBackToWaitingForThePhone() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-1", event("rejection-1", "AdditionalInformationRejected", "process-1",
                "\"reason\":\"INVALID_PHONE_NUMBER\",\"expiresAt\":\"2026-11-03T13:09:00Z\""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "WAITING_PHONE");
    }

    @Test
    void anExpiredProcessEndsExpiredWithItsReason() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.WAITING_PHONE);

        send("process-1", event("expired-1", "AppointmentProcessExpired", "process-1", "\"reason\":\"HOLD_EXPIRED\""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "EXPIRED").containsEntry("failure_reason", "HOLD_EXPIRED");
    }

    @Test
    void anInvalidProcessFailsWithItsReason() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-1", event("invalid-1", "AppointmentProcessInvalid", "process-1",
                "\"reason\":\"REQUEST_EVENT_MISMATCH\""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "REQUEST_EVENT_MISMATCH");
    }

    @Test
    void aCancellationFromTheCatedraCancelsTheReservationWithItsReason() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.CONFIRMED);

        send("process-1", event("cancellation-1", "AppointmentCancelled", "process-1",
                "\"reservationId\":8251,\"reason\":\"Cambio solicitado por el usuario\""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "CANCELLED")
                .containsEntry("cancellation_reason", "Cambio solicitado por el usuario");
        assertThat(row("process-1").get("cancelled_at")).isNotNull();
    }

    @Test
    void aRepeatedEventDoesNotChangeTheResultAgain() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);
        String confirmation = event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251");

        send("process-1", confirmation);
        send("process-1", confirmation);
        waitUntil("los dos mensajes confirmados", () -> offsetReached(before + 2));

        assertThat(row("process-1")).containsEntry("status", "CONFIRMED").containsEntry("version", 1L);
        assertThat(processedEventIds()).containsExactly("confirmation-1");
    }

    // La clave de idempotencia es el eventId, no el contenido
    @Test
    void theEventIdAloneIsEnoughToDiscardARepeatedEvent() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.CONFIRMED);

        send("process-1", event("evento-mismo-id", "AppointmentCancelled", "process-1", "\"reason\":\"primero\""));
        send("process-1", event("evento-mismo-id", "AppointmentCancelled", "process-1", "\"reason\":\"segundo\""));
        waitUntil("los dos mensajes confirmados", () -> offsetReached(before + 2));

        assertThat(row("process-1")).containsEntry("cancellation_reason", "primero");
    }

    // Un evento tardío o fuera de orden no hace retroceder un estado final
    @Test
    void aLateEventNeverMovesTheReservationBackwards() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-1", event("cancellation-1", "AppointmentCancelled", "process-1", "\"reason\":\"antes\""));
        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        send("process-1", event("request-1", "AdditionalInformationRequested", "process-1", ""));
        send("process-1", event("expired-1", "AppointmentProcessExpired", "process-1", "\"reason\":\"HOLD_EXPIRED\""));
        waitUntil("los cuatro mensajes confirmados", () -> offsetReached(before + 4));

        // La cancelación sobre PHONE_SUBMITTED no está en el diagrama (solo se cancela lo confirmado): la ignora
        assertThat(row("process-1")).containsEntry("status", "CONFIRMED");
        assertThat(processedEventIds()).containsExactlyInAnyOrder("cancellation-1", "confirmation-1", "request-1",
                "expired-1");
    }

    @Test
    void afterTheCancellationNothingReopensTheReservation() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.CONFIRMED);

        send("process-1", event("cancellation-1", "AppointmentCancelled", "process-1", "\"reason\":\"motivo\""));
        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        send("process-1", event("request-1", "AdditionalInformationRequested", "process-1", ""));
        waitUntil("los tres mensajes confirmados", () -> offsetReached(before + 3));

        assertThat(row("process-1")).containsEntry("status", "CANCELLED")
                .containsEntry("cancellation_reason", "motivo").containsEntry("request_event_id", null);
    }

    @Test
    void anEventOfAnUnknownProcessIsIgnoredAndRecordedAndTheNextOneIsProcessed() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-desconocido", event("evento-ajeno", "AppointmentConfirmed", "process-desconocido",
                "\"reservationId\":1"));
        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        waitUntil("los dos mensajes confirmados", () -> offsetReached(before + 2));

        assertThat(row("process-1")).containsEntry("status", "CONFIRMED");
        assertThat(processedEventIds()).containsExactlyInAnyOrder("evento-ajeno", "confirmation-1");
    }

    @Test
    void anUnknownEventTypeIsIgnoredAndRecordedWithItsType() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-1", event("evento-nuevo", "AppointmentRescheduled", "process-1", ""));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "PHONE_SUBMITTED");
        assertThat(jdbc.sql("SELECT event_type FROM processed_events WHERE event_id = 'evento-nuevo'")
                .query(String.class).single()).isEqualTo("AppointmentRescheduled");
    }

    @Test
    void anInvalidMessageIsSkippedWithoutRetryingAndTheNextOneIsProcessed() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);

        send("process-1", "esto no es json");
        send("process-1", "{\"eventType\":\"AppointmentConfirmed\",\"reservationProcessId\":\"process-1\"}");
        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        waitUntil("los tres mensajes confirmados", () -> offsetReached(before + 3));

        assertThat(row("process-1")).containsEntry("status", "CONFIRMED");
        assertThat(processedEventIds()).containsExactly("confirmation-1");
        verify(processedEventRepository, times(1)).save(any());
        // Reintentar un mensaje que no se puede interpretar no lo arregla: se lee una sola vez
        verify(catedraEventMapper, times(1)).toDomainModel("esto no es json");
    }

    // Dos escrituras que chocan por la versión: se deshace todo y se repite el evento en una transacción nueva
    @Test
    void aVersionConflictRepeatsTheWholeEventAndItIsAppliedOnce() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);
        doThrow(new ReservationConflictException(1L, null)).doCallRealMethod().when(reservationProcessRepository)
                .save(any());

        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
        assertThat(processedEventIds()).containsExactly("confirmation-1");
        verify(processedEventRepository, times(1)).save(any());
    }

    // Un choque real: otra conexión cambia el proceso entre que el evento lo lee y lo guarda. El choque tiene que saltar
    // dentro de la transacción (con la versión vieja), deshacerla entera y repetir el evento con la versión nueva
    @Test
    void aRealVersionConflictInsideTheTransactionIsRepeatedAndTheOtherWriteIsKept() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);
        AtomicBoolean otherWriteDone = new AtomicBoolean();
        doAnswer(invocation -> {
            Object found = invocation.callRealMethod();
            if (otherWriteDone.compareAndSet(false, true)) {
                // Desde otro hilo, que usa otra conexión: en este se uniría a la transacción del evento
                CompletableFuture.runAsync(() -> jdbc.sql("""
                        UPDATE reservation_processes SET version = version + 1, patient_phone = '+5492615551234'
                        WHERE reservation_process_id = 'process-1'""").update()).join();
            }
            return found;
        }).when(reservationProcessRepository).findById(any());

        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L)
                .containsEntry("version", 2L);
        assertThat(jdbc.sql("SELECT patient_phone FROM reservation_processes").query(String.class).single())
                .isEqualTo("+5492615551234");
        assertThat(processedEventIds()).containsExactly("confirmation-1");
        verify(reservationProcessRepository, times(2)).findById(any());
    }

    // Si no se puede registrar el eventId, el cambio de estado tampoco queda: es una sola transacción
    @Test
    void ifTheEventIdCannotBeRecordedTheStatusDoesNotChangeEither() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);
        IllegalStateException failure = new IllegalStateException("no se pudo guardar");
        doThrow(failure).when(processedEventRepository).save(any());

        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        waitUntil("el mensaje descartado y confirmado", () -> offsetReached(before + 1));

        assertThat(row("process-1")).containsEntry("status", "PHONE_SUBMITTED").containsEntry("reservation_id", null);
        assertThat(processedEventIds()).isEmpty();
    }

    // Sin cola de eventos muertos (D70): agotados los reintentos se descarta y el siguiente evento se procesa
    @Test
    void anEventThatAlwaysFailsIsSkippedAfterTheRetriesAndTheNextOneIsProcessed() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);
        seed("process-2", ReservationStatus.PHONE_SUBMITTED);
        IllegalStateException failure = new IllegalStateException("no se pudo guardar");
        // Primer intento más dos reintentos
        doThrow(failure).doThrow(failure).doThrow(failure).doCallRealMethod().when(processedEventRepository)
                .save(any());

        send("process-1", event("roto", "AppointmentConfirmed", "process-1", "\"reservationId\":1"));
        send("process-2", event("siguiente", "AppointmentConfirmed", "process-2", "\"reservationId\":2"));
        waitUntil("los dos mensajes confirmados", () -> offsetReached(before + 2));

        assertThat(row("process-1")).containsEntry("status", "PHONE_SUBMITTED");
        assertThat(row("process-2")).containsEntry("status", "CONFIRMED");
        assertThat(processedEventIds()).containsExactly("siguiente");
    }

    @Test
    void theOffsetIsNotConfirmedWhileTheEventIsStillBeingProcessed() throws Exception {
        long before = committedOffset();
        seed("process-1", ReservationStatus.PHONE_SUBMITTED);
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            processing.countDown();
            release.await(30, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(processedEventRepository).save(any());

        send("process-1", event("confirmation-1", "AppointmentConfirmed", "process-1", "\"reservationId\":8251"));
        assertThat(processing.await(30, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1500);
        assertThat(committedOffset()).isEqualTo(before);

        release.countDown();
        waitUntil("el offset confirmado", () -> offsetReached(before + 1));
        assertThat(row("process-1")).containsEntry("status", "CONFIRMED");
    }
}
