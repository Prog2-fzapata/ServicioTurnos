package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ar.edu.um.prog2.fzapata.turnos.KafkaTestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// El flujo de reserva de punta a punta, sin sembrar ningún estado a mano y sin ningún mock: PostgreSQL y Kafka reales,
// el listener y el relay del outbox corriendo solos, y la cátedra y catálogo como servidores HTTP locales. Quien hace
// de cátedra es el test: responde los holds por HTTP y publica sus eventos por Kafka siguiendo el contrato. Prueba que
// nuestras piezas encadenan (los ids, los estados, el outbox, el listener), no cómo se comporta la cátedra de verdad.
// Cada test usa un proceso propio: el topic se comparte. "Hoy" es el lunes 12/10/2026 a las 10:10
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "outbox.relay-enabled=true",
        "outbox.relay-interval=100ms",
        "catedra.kafka.max-retries=1",
        "catedra.kafka.initial-backoff=20ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class,
        ReservationFlowIntegrationTest.FixedClock.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationFlowIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-12T13:10:00Z"), ZoneId.of("America/Argentina/Mendoza"));
        }
    }

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String OWNER = "42";
    private static final String OTHER_USER = "43";
    private static final String PHONE = "+54 9 261 615-9550";
    private static final String NORMALIZED_PHONE = "+5492616159550";
    private static final String EXPIRES_AT = "2026-10-12T13:25:00Z";

    private static final FakeHttpServer CATALOG = newFakeServer();
    private static final FakeHttpServer CATEDRA = newFakeServer();

    private static final String PROFESSIONAL = """
            {"id": 15, "categoryId": 1, "categoryName": "Clinica medica", "categoryEnabled": true,
             "firstName": "Ana", "lastName": "Gomez", "enabled": true,
             "weeklySchedules": [
               {"id": 1, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
                "slotDurationMinutes": 30, "enabled": true}]}""";
    private static final String ACCOUNT = "{\"id\": 7, \"login\": \"juan\", \"firstName\": \"Juan\", "
            + "\"lastName\": \"Perez\"}";
    private static final String CANCELLED = """
            {"reservationId": 8251, "reservationProcessId": "process-1", "status": "CANCELLED",
             "cancelledAt": "2026-10-12T13:10:00Z"}""";
    private static final String START_BODY = "{\"professionalId\": 15, \"date\": \"2026-10-19\", \"startTime\": \"09:30\"}";

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${catedra.kafka.appointment-actions-topic}")
    private String actionsTopic;

    @Value("${catedra.kafka.appointment-phone-topic}")
    private String phoneTopic;

    private static FakeHttpServer newFakeServer() {
        try {
            return new FakeHttpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void pointTheClientsToTheFakeServers(DynamicPropertyRegistry registry) {
        registry.add("catalogo.base-url", CATALOG::baseUrl);
        registry.add("catedra.base-url", CATEDRA::baseUrl);
        // Un solo intento HTTP: ningún test espera reintentos
        registry.add("catalogo.max-attempts", () -> "1");
        registry.add("catedra.max-attempts", () -> "1");
    }

    @AfterAll
    static void stopTheFakeServers() {
        CATALOG.close();
        CATEDRA.close();
    }

    @BeforeEach
    void cleanUp() {
        CATALOG.reset();
        CATEDRA.reset();
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private static String newProcessId() {
        return "process-" + UUID.randomUUID();
    }

    private static String hold(String processId) {
        return "{\"holdId\": \"hold-1\", \"reservationProcessId\": \"" + processId + "\", \"status\": \"HELD\", "
                + "\"expiresAt\": \"2026-10-12T13:20:00Z\"}";
    }

    private static String confirmation(String processId) {
        return "{\"reservationProcessId\": \"" + processId + "\", \"status\": \"WAITING_FOR_PHONE\", "
                + "\"expiresAt\": \"2026-10-12T13:20:01Z\"}";
    }

    private static String event(String eventId, String eventType, String processId, String extra) {
        return """
                {"eventId":"%s","eventType":"%s","groupId":"fzapata","reservationProcessId":"%s","holdId":"hold-1",
                 "occurredAt":"2026-10-12T13:10:00Z","schemaVersion":1%s}""".formatted(eventId, eventType, processId,
                extra.isEmpty() ? "" : "," + extra);
    }

    private ResultActions asUser(String userId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.claim("uid", userId))));
    }

    private ResultActions read(String userId, long id) throws Exception {
        return asUser(userId, get("/api/reservations/" + id));
    }

    private void send(String processId, String payload) {
        kafkaTemplate.send(actionsTopic, processId, payload);
        kafkaTemplate.flush();
    }

    private void waitUntil(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as("se esperaba: %s", description).isTrue();
    }

    private String statusOf(String processId) {
        return jdbc.sql("SELECT status FROM reservation_processes WHERE reservation_process_id = :id")
                .param("id", processId).query(String.class).single();
    }

    private boolean canSubmitPhone(long id) {
        try {
            return jsonMapper.readTree(read(OWNER, id).andReturn().getResponse().getContentAsString())
                    .get("canSubmitPhone").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean wasProcessed(String eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id").param("id", eventId)
                .query(Long.class).single() > 0;
    }

    private List<Map<String, Object>> outbox(String processId) {
        return jdbc.sql("SELECT event_id, status, payload FROM outbox_events WHERE reservation_process_id = :id "
                + "ORDER BY id").param("id", processId).query().listOfRows();
    }

    private JsonNode json(String text) {
        return jsonMapper.readTree(text);
    }

    // Lo que el relay dejó en el topic del teléfono para ese proceso, leído desde el principio con un grupo propio
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
            consumer.subscribe(List.of(phoneTopic));
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

    // Paso 1: el usuario inicia la reserva. La cátedra crea el hold y lo confirma; catálogo conoce al profesional
    private long startReservation(String processId, FakeHttpServer.Behavior... afterwards) throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL), status(200, ACCOUNT));
        List<FakeHttpServer.Behavior> script = new ArrayList<>(List.of(status(201, hold(processId)),
                status(202, confirmation(processId))));
        script.addAll(List.of(afterwards));
        CATEDRA.thenRespond(script.toArray(FakeHttpServer.Behavior[]::new));

        ResultActions started = asUser(OWNER, post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(START_BODY)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.reservationProcessId").value(processId))
                .andExpect(jsonPath("$.canSubmitPhone").value(false));
        return json(started.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    // Paso 2: la cátedra pide el teléfono (evento por Kafka) y recién entonces la app puede enviarlo
    private void theCatedraAsksForThePhone(long id, String processId, String requestEventId) throws Exception {
        send(processId, event(requestEventId, "AdditionalInformationRequested", processId,
                "\"requiredField\":\"PHONE_NUMBER\",\"expiresAt\":\"" + EXPIRES_AT + "\""));
        waitUntil("que la reserva habilite el envío del teléfono", () -> canSubmitPhone(id));
    }

    // Paso 3: el usuario envía el teléfono y el relay lo publica solo en el topic de la cátedra
    private void sendThePhone(long id, String processId) throws Exception {
        asUser(OWNER, post("/api/reservations/" + id + "/phone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"" + PHONE + "\"}")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PHONE_QUEUED"));
        waitUntil("que el relay publique el teléfono y la reserva quede PHONE_SUBMITTED",
                () -> "PHONE_SUBMITTED".equals(statusOf(processId)));
    }

    // Paso 4: la cátedra confirma el turno (evento por Kafka)
    private void theCatedraConfirms(String processId, String eventId) throws Exception {
        send(processId, event(eventId, "AppointmentConfirmed", processId, "\"reservationId\":8251"));
        waitUntil("que la reserva quede CONFIRMED", () -> "CONFIRMED".equals(statusOf(processId)));
    }

    @Test
    void aReservationGoesFromTheStartToConfirmedAndOnlyItsOwnerSeesIt() throws Exception {
        String processId = newProcessId();
        long id = startReservation(processId);
        read(OWNER, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING_PHONE"));
        read(OTHER_USER, id).andExpect(status().isNotFound());
        // Mientras la cátedra no pida el teléfono no se puede enviar
        asUser(OWNER, post("/api/reservations/" + id + "/phone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"" + PHONE + "\"}")).andExpect(status().isConflict());

        theCatedraAsksForThePhone(id, processId, "request-" + processId);
        asUser(OTHER_USER, post("/api/reservations/" + id + "/phone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"" + PHONE + "\"}")).andExpect(status().isNotFound());
        assertThat(outbox(processId)).isEmpty();
        sendThePhone(id, processId);

        // Lo que llegó al topic de la cátedra: clave y ids del proceso, el pedido que se contesta y el teléfono
        assertThat(outbox(processId)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "SENT"));
        assertThat(published(processId, 1)).singleElement().satisfies(record -> {
            JsonNode message = json(record.value());
            assertThat(record.key()).isEqualTo(processId);
            assertThat(message.get("reservationProcessId").asString()).isEqualTo(processId);
            assertThat(message.get("holdId").asString()).isEqualTo("hold-1");
            assertThat(message.get("requestEventId").asString()).isEqualTo("request-" + processId);
            assertThat(message.get("information").get("phoneNumber").asString()).isEqualTo(NORMALIZED_PHONE);
        });

        theCatedraConfirms(processId, "confirmation-" + processId);

        read(OWNER, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.canSubmitPhone").value(false))
                .andExpect(jsonPath("$.patientPhone").value(NORMALIZED_PHONE))
                .andExpect(jsonPath("$.professionalFirstName").value("Ana"));
        read(OTHER_USER, id).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT reservation_id FROM reservation_processes WHERE reservation_process_id = :id")
                .param("id", processId).query(Long.class).single()).isEqualTo(8251L);
        assertThat(CATEDRA.requests()).extracting(request -> request.method() + " " + request.rawPath())
                .containsExactly("POST /api/appointment-holds", "POST /api/appointment-holds/hold-1/confirm");
    }

    @Test
    void aRejectedPhoneIsSentAgainWithANewMessageAndTheReservationEndsConfirmed() throws Exception {
        String processId = newProcessId();
        long id = startReservation(processId);
        theCatedraAsksForThePhone(id, processId, "request-" + processId);
        sendThePhone(id, processId);

        send(processId, event("rejection-" + processId, "AdditionalInformationRejected", processId,
                "\"reason\":\"INVALID_PHONE_NUMBER\",\"expiresAt\":\"" + EXPIRES_AT + "\""));
        waitUntil("que la reserva vuelva a esperar el teléfono", () -> "WAITING_PHONE".equals(statusOf(processId)));
        read(OWNER, id).andExpect(jsonPath("$.canSubmitPhone").value(true));
        sendThePhone(id, processId);
        theCatedraConfirms(processId, "confirmation-" + processId);

        // Dos mensajes distintos para el mismo proceso, y el segundo contesta al mismo pedido de la cátedra
        List<ConsumerRecord<String, String>> messages = published(processId, 2);
        assertThat(messages).hasSize(2).allSatisfy(record -> assertThat(record.key()).isEqualTo(processId));
        assertThat(messages).extracting(record -> json(record.value()).get("eventId").asString())
                .doesNotHaveDuplicates();
        assertThat(messages).extracting(record -> json(record.value()).get("requestEventId").asString())
                .containsOnly("request-" + processId);
        assertThat(outbox(processId)).hasSize(2).allSatisfy(row -> assertThat(row).containsEntry("status", "SENT"));
        read(OWNER, id).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void aConfirmedReservationCanBeCancelledAndNoLateOrRepeatedEventReopensIt() throws Exception {
        String processId = newProcessId();
        long id = startReservation(processId, status(200, CANCELLED));
        theCatedraAsksForThePhone(id, processId, "request-" + processId);
        sendThePhone(id, processId);
        theCatedraConfirms(processId, "confirmation-" + processId);

        asUser(OTHER_USER, post("/api/reservations/" + id + "/cancel")).andExpect(status().isNotFound());
        assertThat(CATEDRA.requestCount()).isEqualTo(2);
        asUser(OWNER, post("/api/reservations/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Ya no puedo ir\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Ya no puedo ir"));
        // Cancela con el id del proceso que la cátedra devolvió al crear el hold
        assertThat(CATEDRA.requests().get(2).rawPath()).isEqualTo("/api/appointments/" + processId + "/cancel");
        assertThat(CATEDRA.requests().get(2).body()).contains("Ya no puedo ir");

        // El mismo evento repetido, otro de confirmación con otro id y un evento de cierre para saber cuándo terminó
        String last = "last-" + processId;
        send(processId, event("confirmation-" + processId, "AppointmentConfirmed", processId, "\"reservationId\":8251"));
        send(processId, event("late-" + processId, "AppointmentConfirmed", processId, "\"reservationId\":8251"));
        send(processId, event(last, "TipoNuevo", processId, ""));
        waitUntil("que el listener procese el último evento", () -> wasProcessed(last));

        assertThat(wasProcessed("late-" + processId)).isTrue();
        assertThat(statusOf(processId)).isEqualTo("CANCELLED");
        read(OWNER, id).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Ya no puedo ir"));
        assertThat(CATEDRA.requestCount()).isEqualTo(3);
    }
}
