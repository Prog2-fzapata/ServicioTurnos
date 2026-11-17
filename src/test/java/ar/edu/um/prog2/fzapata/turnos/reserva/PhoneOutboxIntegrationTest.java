package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.KafkaTestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhonePublisher;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Teléfono y relay contra PostgreSQL y Kafka reales. El relay automático está apagado (configuración de tests): cada
// test lo dispara a mano con publishPendingPhones, así se ve el estado entre el POST y la publicación
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class PhoneOutboxIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservaService reservaService;
    private final KafkaAdmin kafkaAdmin;
    private final Environment environment;
    private final JsonMapper jsonMapper;

    @MockitoSpyBean
    private PhonePublisher phonePublisher;

    @MockitoSpyBean
    private OutboxRepository outboxRepository;

    @MockitoSpyBean
    private ReservationProcessRepository reservationProcessRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private String phoneTopic() {
        return environment.getRequiredProperty("catedra.kafka.appointment-phone-topic");
    }

    // Un proceso por test, con id propio: el topic se comparte y acumula los mensajes de todos
    private String newProcessId() {
        return "process-" + UUID.randomUUID();
    }

    private ReservationProcess seed(String processId, ReservationStatus status, String requestEventId,
            Instant expiresAt, String userId) {
        return reservationProcessRepository.save(ReservationProcess.builder().userId(userId)
                .reservationProcessId(processId).holdId("hold-1").professionalId(15L).professionalFirstName("Ana")
                .professionalLastName("Gomez").appointmentDate(LocalDate.of(2026, 11, 9))
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(status)
                .requestEventId(requestEventId).expiresAt(expiresAt).patientFirstName("Juan").patientLastName("Perez")
                .createdAt(T0).updatedAt(T0).build());
    }

    private ReservationProcess waiting(String processId) {
        return seed(processId, ReservationStatus.WAITING_PHONE, "request-event-8a31",
                Instant.now().plus(Duration.ofMinutes(10)), "42");
    }

    private ResultActions submit(Long id, String userId, String body) throws Exception {
        return mockMvc.perform(post("/api/reservations/" + id + "/phone")
                .with(jwt().jwt(token -> token.claim("uid", userId))).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions submit(Long id, String phone) throws Exception {
        return submit(id, "42", "{\"phoneNumber\": \"" + phone + "\"}");
    }

    private Map<String, Object> process(String processId) {
        return jdbc.sql("""
                SELECT status, patient_phone, reservation_id, request_event_id, version
                FROM reservation_processes WHERE reservation_process_id = :id""").param("id", processId).query()
                .singleRow();
    }

    private List<Map<String, Object>> outbox(String processId) {
        return jdbc.sql("""
                SELECT event_id, topic, message_key, payload, status, attempts, sent_at
                FROM outbox_events WHERE reservation_process_id = :id ORDER BY id""").param("id", processId).query()
                .listOfRows();
    }

    // Lee el topic desde el principio con un grupo propio y se queda con los mensajes de ese proceso. Si se esperan
    // más de cero espera hasta tenerlos; después mira un rato más por si llegó alguno de sobra
    private List<ConsumerRecord<String, String>> published(String processId, int expected) {
        Properties config = new Properties();
        config.putAll(kafkaAdmin.getConfigurationProperties());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "lector-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(phoneTopic()));
            long deadline = System.nanoTime() + WAIT.toNanos();
            long quietUntil = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(300)).forEach(record -> {
                    if (processId.equals(record.key())) {
                        found.add(record);
                    }
                });
                if (found.size() >= expected && System.nanoTime() > quietUntil) {
                    break;
                }
            }
        }
        return found;
    }

    private JsonNode json(String payload) {
        return jsonMapper.readTree(payload);
    }

    @Test
    void thePostLeavesTheReservationQueuedAndTheMessagePendingInTheSameTransaction() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);

        submit(seeded.getId(), "+54 9 261 615-9550")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PHONE_QUEUED"))
                .andExpect(jsonPath("$.patientPhone").value("+5492616159550"))
                .andExpect(jsonPath("$.canSubmitPhone").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist());

        assertThat(process(processId)).containsEntry("status", "PHONE_QUEUED")
                .containsEntry("patient_phone", "+5492616159550");
        assertThat(outbox(processId)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 0)
                    .containsEntry("topic", phoneTopic()).containsEntry("message_key", processId);
            assertThat(row.get("sent_at")).isNull();
            JsonNode payload = json((String) row.get("payload"));
            assertThat(payload.get("eventId").asString()).isEqualTo(row.get("event_id"));
            assertThat(payload.get("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
            assertThat(payload.get("groupId").asString()).isEqualTo("grupo-cuenta-de-prueba");
            assertThat(payload.get("reservationProcessId").asString()).isEqualTo(processId);
            assertThat(payload.get("holdId").asString()).isEqualTo("hold-1");
            assertThat(payload.get("requestEventId").asString()).isEqualTo("request-event-8a31");
            assertThat(payload.get("information").get("phoneNumber").asString()).isEqualTo("+5492616159550");
            assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        });
    }

    @Test
    void theRelayPublishesTheMessageWithTheProcessAsKeyAndThenMarksItSentAndSubmitted() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());
        Map<String, Object> pending = outbox(processId).get(0);

        List<PhoneDelivery> deliveries = reservaService.publishPendingPhones();

        assertThat(deliveries).filteredOn(delivery -> processId.equals(delivery.event().getReservationProcessId()))
                .singleElement().satisfies(delivery -> assertThat(delivery.sent()).isTrue());
        List<ConsumerRecord<String, String>> messages = published(processId, 1);
        assertThat(messages).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo(processId);
            // Es el payload guardado en el outbox, sin cambios
            assertThat(record.value()).isEqualTo(pending.get("payload"));
        });
        assertThat(outbox(processId)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("status", "SENT").containsEntry("attempts", 0);
            assertThat(row.get("sent_at")).isNotNull();
        });
        assertThat(process(processId)).containsEntry("status", "PHONE_SUBMITTED");
    }

    @Test
    void ifTheOutboxCannotBeSavedTheReservationIsNotQueuedEither() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        doThrow(new IllegalStateException("no se pudo guardar")).when(outboxRepository).save(any());

        submit(seeded.getId(), "2616159550").andExpect(status().isInternalServerError());

        assertThat(process(processId)).containsEntry("status", "WAITING_PHONE").containsEntry("patient_phone", null);
        assertThat(outbox(processId)).isEmpty();
    }

    // A33: el choque de versión que agota los reintentos responde 409 y no deja nada escrito: ni el estado ni el mensaje
    @Test
    void aVersionConflictThatExhaustsTheRetriesAnswers409AndChangesNothing() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        clearInvocations(reservationProcessRepository);
        doThrow(new ReservationConflictException(seeded.getId(), new RuntimeException("versión")))
                .when(reservationProcessRepository).save(any());

        submit(seeded.getId(), "2616159550")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));

        verify(reservationProcessRepository, times(3)).save(any());
        assertThat(process(processId)).containsEntry("status", "WAITING_PHONE").containsEntry("patient_phone", null);
        assertThat(outbox(processId)).isEmpty();
    }

    // Sin el ack el mensaje sigue pendiente y se reintenta con el mismo eventId y contenido (D12b)
    @Test
    void ifKafkaDoesNotConfirmTheMessageStaysPendingAndIsSentLaterWithTheSameEventId() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());
        Map<String, Object> pending = outbox(processId).get(0);
        doThrow(new PhonePublishException("sin ack", null)).doCallRealMethod().when(phonePublisher)
                .publish(any(), any(), any());

        reservaService.publishPendingPhones();

        assertThat(outbox(processId)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1));
        assertThat(process(processId)).containsEntry("status", "PHONE_QUEUED");

        reservaService.publishPendingPhones();

        assertThat(published(processId, 1)).singleElement()
                .satisfies(record -> assertThat(record.value()).isEqualTo(pending.get("payload")));
        assertThat(outbox(processId)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("status", "SENT").containsEntry("event_id", pending.get("event_id")));
        assertThat(process(processId)).containsEntry("status", "PHONE_SUBMITTED");
    }

    // Se publicó pero no se pudo marcar: se reenvía el mismo mensaje y la cátedra descarta el duplicado (D12b)
    @Test
    void ifItCannotBeMarkedAfterThePublicationItIsRepublishedWithTheSameEventId() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());
        doThrow(new IllegalStateException("la base se cayó")).doCallRealMethod().when(outboxRepository).save(any());

        assertThatThrownBy(() -> reservaService.publishPendingPhones()).isInstanceOf(IllegalStateException.class);

        assertThat(outbox(processId)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "PENDING"));
        assertThat(process(processId)).containsEntry("status", "PHONE_QUEUED");

        reservaService.publishPendingPhones();

        List<ConsumerRecord<String, String>> messages = published(processId, 2);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).value()).isEqualTo(messages.get(0).value());
        assertThat(outbox(processId)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "SENT"));
        assertThat(process(processId)).containsEntry("status", "PHONE_SUBMITTED");
    }

    // La cátedra contestó antes de que el relay anotara el ack: el estado local no retrocede (D12b)
    @Test
    void ifTheCatedraAnsweredBeforeTheAckTheReservationStaysConfirmed() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());
        reservaService.processEvent(new CatedraEvent("confirmation-" + processId, "AppointmentConfirmed", processId,
                8251L, null, null));
        assertThat(process(processId)).containsEntry("status", "CONFIRMED");

        reservaService.publishPendingPhones();

        assertThat(process(processId)).containsEntry("status", "CONFIRMED").containsEntry("reservation_id", 8251L);
        assertThat(outbox(processId)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "SENT"));
        assertThat(published(processId, 1)).hasSize(1);
    }

    // Decisión 3 (opción A): si el proceso ya terminó, el mensaje igual se publica y el estado no cambia
    @Test
    void aMessageWhoseReservationAlreadyEndedIsStillPublishedAndTheStateDoesNotChange() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());
        reservaService.processEvent(new CatedraEvent("expired-" + processId, "AppointmentProcessExpired", processId,
                null, "HOLD_EXPIRED", null));
        assertThat(process(processId)).containsEntry("status", "EXPIRED");

        reservaService.publishPendingPhones();

        assertThat(process(processId)).containsEntry("status", "EXPIRED");
        assertThat(outbox(processId)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "SENT"));
        assertThat(published(processId, 1)).hasSize(1);
    }

    @Test
    void aRejectedPhoneCanBeSentAgainWithANewMessage() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "1111111").andExpect(status().isAccepted());
        reservaService.publishPendingPhones();
        reservaService.processEvent(new CatedraEvent("rejection-" + processId, "AdditionalInformationRejected",
                processId, null, "INVALID_PHONE_NUMBER", Instant.now().plus(Duration.ofMinutes(5))));
        assertThat(process(processId)).containsEntry("status", "WAITING_PHONE");

        mockMvc.perform(get("/api/reservations/" + seeded.getId()).with(jwt().jwt(token -> token.claim("uid", "42"))))
                .andExpect(jsonPath("$.canSubmitPhone").value(true));
        submit(seeded.getId(), "2222222").andExpect(status().isAccepted());
        reservaService.publishPendingPhones();

        List<ConsumerRecord<String, String>> messages = published(processId, 2);
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(record -> json(record.value()).get("eventId").asString())
                .doesNotHaveDuplicates();
        assertThat(messages).extracting(record -> json(record.value()).get("information").get("phoneNumber").asString())
                .containsExactly("1111111", "2222222");
        assertThat(process(processId)).containsEntry("status", "PHONE_SUBMITTED").containsEntry("patient_phone", "2222222");
    }

    @Test
    void aSecondPostWhileTheFirstIsQueuedIsAConflictAndCreatesNoSecondMessage() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());

        submit(seeded.getId(), "2616159550")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));

        assertThat(outbox(processId)).hasSize(1);
    }

    @Test
    void aForeignReservationIsNotFoundAndNothingIsQueued() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);

        submit(seeded.getId(), "99", "{\"phoneNumber\": \"2616159550\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

        assertThat(process(processId)).containsEntry("status", "WAITING_PHONE").containsEntry("patient_phone", null);
        assertThat(outbox(processId)).isEmpty();
    }

    @Test
    void anExpiredReservationIsRejectedAndNothingIsQueued() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = seed(processId, ReservationStatus.WAITING_PHONE, "request-event-8a31",
                Instant.now().minusSeconds(5), "42");

        submit(seeded.getId(), "2616159550")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));

        assertThat(process(processId)).containsEntry("status", "WAITING_PHONE");
        assertThat(outbox(processId)).isEmpty();
    }

    @Test
    void whileTheCatedraHasNotAskedForThePhoneItIsAConflict() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = seed(processId, ReservationStatus.WAITING_PHONE, null,
                Instant.now().plus(Duration.ofMinutes(10)), "42");

        submit(seeded.getId(), "2616159550")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));

        assertThat(outbox(processId)).isEmpty();
    }

    @Test
    void anInvalidPhoneIsRejectedAndNothingIsQueued() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);

        submit(seeded.getId(), "123").andExpect(status().isBadRequest());

        assertThat(process(processId)).containsEntry("status", "WAITING_PHONE");
        assertThat(outbox(processId)).isEmpty();
    }

    // Un choque real: otra conexión cambia el proceso entre que se lee y se guarda. Se deshace todo y se repite
    @Test
    void aRealVersionConflictWhileQueuingIsRepeatedAndTheMessageIsCreatedOnce() throws Exception {
        String processId = newProcessId();
        ReservationProcess seeded = waiting(processId);
        AtomicBoolean otherWriteDone = new AtomicBoolean();
        doAnswer(invocation -> {
            Object found = invocation.callRealMethod();
            if (otherWriteDone.compareAndSet(false, true)) {
                // Desde otro hilo, que usa otra conexión: en este se uniría a la transacción
                CompletableFuture.runAsync(() -> jdbc.sql("""
                        UPDATE reservation_processes SET version = version + 1, failure_reason = 'otro cambio'
                        WHERE reservation_process_id = :id""").param("id", processId).update()).join();
            }
            return found;
        }).when(reservationProcessRepository).findById(any());

        submit(seeded.getId(), "2616159550").andExpect(status().isAccepted());

        assertThat(process(processId)).containsEntry("status", "PHONE_QUEUED").containsEntry("version", 2L);
        assertThat(outbox(processId)).hasSize(1);
        verify(reservationProcessRepository, times(2)).findById(any());
    }

    @Test
    void theRelayTakesTheOldestMessagesFirstAndAtMostTheLimit() throws Exception {
        String first = newProcessId();
        String second = newProcessId();
        String third = newProcessId();
        for (String processId : List.of(first, second, third)) {
            submit(waiting(processId).getId(), "2616159550").andExpect(status().isAccepted());
        }

        assertThat(outboxRepository.findPending(2)).extracting(event -> event.getReservationProcessId())
                .containsExactly(first, second);
        assertThat(outboxRepository.findPending(10)).extracting(event -> event.getReservationProcessId())
                .containsExactly(first, second, third);
    }

    @Test
    void sentMessagesAreNotPublishedAgain() throws Exception {
        String processId = newProcessId();
        submit(waiting(processId).getId(), "2616159550").andExpect(status().isAccepted());
        reservaService.publishPendingPhones();

        List<PhoneDelivery> second = reservaService.publishPendingPhones();

        assertThat(second).noneMatch(delivery -> processId.equals(delivery.event().getReservationProcessId()));
        assertThat(published(processId, 1)).hasSize(1);
    }
}
