package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.cutConnection;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.KafkaTestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhonePublisher;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import lombok.RequiredArgsConstructor;

// Criterio del issue #19: ni el JWT, ni la contraseña, ni el teléfono ni el nombre del paciente llegan a un log. Cada
// test corre un flujo real (PostgreSQL, Kafka y servidores HTTP simulados), captura TODO lo que se escribe (mensaje y
// traza de cada excepción) y comprueba que ningún valor sentinela aparece. Antes comprueba que el valor sí viajó por el
// flujo: sin eso el test pasaría igual si el flujo no hubiera usado el valor. "Hoy" es el lunes 12/10/2026 a las 10:10
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "outbox.relay-enabled=true",
        "outbox.relay-interval=100ms",
        "catedra.kafka.max-retries=1",
        "catedra.kafka.initial-backoff=20ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class,
        SecretsNotInLogsIntegrationTest.FixedClock.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class SecretsNotInLogsIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-12T13:10:00Z"), ZoneId.of("America/Argentina/Mendoza"));
        }
    }

    private static final String USER_JWT = "USER-JWT-SENTINEL-4f9a1c";
    private static final String CATEDRA_JWT = "CATEDRA-JWT-SENTINEL-77c1e0";
    // La clave con que se firman los tokens en los tests (src/test/resources/config/application.properties)
    private static final String JWT_SECRET = "RH0hhbK6hjXUQb7k8USPY8wvXPR2c922ftXe9uImaj";
    private static final String FIRST_NAME = "SentinelNombre";
    private static final String LAST_NAME = "SentinelApellido";
    private static final String RAW_PHONE = "+54 9 261 615-9550";
    private static final String CANCEL_REASON = "MOTIVO-SENTINEL-93bd";
    // El teléfono tal como llega, normalizado y sus partes: ninguna forma de escribirlo debe aparecer
    private static final List<String> SECRETS = List.of(USER_JWT, CATEDRA_JWT, JWT_SECRET, FIRST_NAME, LAST_NAME,
            RAW_PHONE, "+5492616159550", "5492616159550", "615-9550", "6159550", CANCEL_REASON);

    private static final FakeHttpServer CATALOG = newFakeServer();
    private static final FakeHttpServer CATEDRA = newFakeServer();

    private static final String PROFESSIONAL = """
            {"id": 15, "categoryId": 1, "categoryName": "Clinica medica", "categoryEnabled": true,
             "firstName": "Ana", "lastName": "Gomez", "enabled": true,
             "weeklySchedules": [
               {"id": 1, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
                "slotDurationMinutes": 30, "enabled": true}]}""";
    private static final String ACCOUNT = "{\"id\": 7, \"login\": \"juan\", \"firstName\": \"" + FIRST_NAME
            + "\", \"lastName\": \"" + LAST_NAME + "\"}";
    private static final String HOLD = """
            {"holdId": "hold-1", "reservationProcessId": "process-1", "status": "HELD",
             "expiresAt": "2026-10-12T13:20:00Z"}""";
    private static final String CONFIRM = """
            {"reservationProcessId": "process-1", "status": "WAITING_FOR_PHONE", "expiresAt": "2026-10-12T13:20:01Z"}""";
    private static final String CANCELLED = """
            {"reservationId": 8251, "reservationProcessId": "process-1", "status": "CANCELLED",
             "cancelledAt": "2026-10-12T13:10:00Z"}""";
    private static final String NO_OCCUPANCIES = """
            {"professionalId": 15, "from": "2026-10-19", "to": "2026-10-19", "occupancies": []}""";
    private static final String START_BODY = "{\"professionalId\": 15, \"date\": \"2026-10-19\", \"startTime\": \"09:30\"}";

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservationProcessRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${catedra.kafka.appointment-actions-topic}")
    private String actionsTopic;

    @MockitoSpyBean
    private PhonePublisher phonePublisher;

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger ownLogger = (Logger) LoggerFactory.getLogger("ar.edu.um.prog2.fzapata.turnos");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousOwnLevel;

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
        registry.add("catedra.jwt", () -> CATEDRA_JWT);
        // Un solo intento HTTP: los tests de caída no esperan reintentos
        registry.add("catalogo.max-attempts", () -> "1");
        registry.add("catedra.max-attempts", () -> "1");
    }

    @AfterAll
    static void stopTheFakeServers() {
        CATALOG.close();
        CATEDRA.close();
    }

    // Lo propio a DEBUG (incluye lo que en producción no se vería) y el resto como en producción
    @BeforeEach
    void captureTheLog() {
        CATALOG.reset();
        CATEDRA.reset();
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
        previousOwnLevel = ownLogger.getLevel();
        ownLogger.setLevel(Level.DEBUG);
        appender.list.clear();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        rootLogger.detachAppender(appender);
        appender.stop();
        ownLogger.setLevel(previousOwnLevel);
    }

    // Todo lo que una línea escribe: el mensaje, los campos (los que salen como campos del JSON), el MDC y la traza
    private static String render(ILoggingEvent event) {
        String fields = event.getKeyValuePairs() == null ? "" : event.getKeyValuePairs().stream()
                .map(pair -> pair.key + "=" + pair.value).collect(Collectors.joining(" "));
        String trace = event.getThrowableProxy() == null ? "" : "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy());
        return event.getLevel() + " " + event.getLoggerName() + " " + event.getFormattedMessage() + " " + fields + " "
                + event.getMDCPropertyMap() + trace;
    }

    private List<String> logged() {
        return List.copyOf(appender.list).stream().map(SecretsNotInLogsIntegrationTest::render).toList();
    }

    private void assertNoSecretIsLogged() {
        String everything = String.join("\n", logged());
        for (String secret : SECRETS) {
            assertThat(everything).as("el log no debe contener %s", secret).doesNotContain(secret);
        }
    }

    private void assertSomethingWasLogged(String fragment) {
        assertThat(logged()).as("el flujo debía dejar un log con '%s'", fragment).anyMatch(line -> line.contains(fragment));
    }

    private void waitForTheLog(String fragment) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && logged().stream().noneMatch(line -> line.contains(fragment))) {
            Thread.sleep(100);
        }
        assertSomethingWasLogged(fragment);
    }

    private void waitForTheOutboxToBeSent() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && jdbc.sql("SELECT count(*) FROM outbox_events WHERE status <> 'SENT'")
                .query(Long.class).single() > 0) {
            Thread.sleep(100);
        }
    }

    // Un servicio que repite en su mensaje de error lo que recibió (el JWT, el teléfono): el log no debe copiarlo
    private static String echoing(String code, String detail) {
        return "{\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}";
    }

    private ResultActions asUser(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.tokenValue(USER_JWT).claim("uid", "42"))));
    }

    private ResultActions startReservation() throws Exception {
        return asUser(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(START_BODY));
    }

    private ResultActions submitPhone(Long id, String body) throws Exception {
        return asUser(post("/api/reservations/" + id + "/phone").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions submitPhone(Long id) throws Exception {
        return submitPhone(id, "{\"phoneNumber\": \"" + RAW_PHONE + "\"}");
    }

    private ResultActions cancel(Long id) throws Exception {
        return asUser(post("/api/reservations/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + CANCEL_REASON + "\"}"));
    }

    private ReservationProcess seed(String processId, ReservationStatus status) {
        return repository.save(ReservationProcess.builder().userId("42").reservationProcessId(processId)
                .holdId("hold-1").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 10, 19)).startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 0)).status(status).requestEventId("request-event-8a31")
                .expiresAt(Instant.parse("2026-10-12T14:00:00Z")).patientFirstName(FIRST_NAME)
                .patientLastName(LAST_NAME).createdAt(Instant.parse("2026-10-12T13:00:00Z"))
                .updatedAt(Instant.parse("2026-10-12T13:00:00Z")).build());
    }

    private String newProcessId() {
        return "process-" + UUID.randomUUID();
    }

    private String event(String eventId, String eventType, String processId, String extra) {
        return """
                {"eventId":"%s","eventType":"%s","groupId":"fzapata","reservationProcessId":"%s","holdId":"hold-1",
                 "occurredAt":"2026-10-12T13:10:00Z","schemaVersion":1%s}""".formatted(eventId, eventType, processId,
                extra.isEmpty() ? "" : "," + extra);
    }

    private void send(String processId, String payload) {
        kafkaTemplate.send(actionsTopic, processId, payload);
        kafkaTemplate.flush();
    }

    // ---------- Iniciar

    @Test
    void startingAReservationDoesNotLogTheUserJwtTheTechnicalJwtOrThePatientName() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL), status(200, ACCOUNT));
        CATEDRA.thenRespond(status(201, HOLD), status(202, CONFIRM));

        startReservation().andExpect(status().isCreated());

        assertThat(CATALOG.authorizations()).containsOnly("Bearer " + USER_JWT);
        assertThat(CATEDRA.authorizations()).containsOnly("Bearer " + CATEDRA_JWT);
        assertThat(CATEDRA.requests().get(1).body()).contains(FIRST_NAME);
        // Deja su traza (id, usuario y estado, y los ids de la cátedra) y aun así sin secretos (#50)
        assertSomethingWasLogged("Reserva iniciada reservationId=");
        assertSomethingWasLogged("status=WAITING_PHONE");
        assertSomethingWasLogged("La cátedra creó el hold holdId=hold-1 reservationProcessId=process-1");
        assertSomethingWasLogged("La cátedra aceptó confirmar el hold holdId=hold-1");
        assertNoSecretIsLogged();
    }

    @Test
    void aCatedraThatRejectsTheTechnicalJwtDoesNotLeakItInTheError() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL), status(200, ACCOUNT));
        CATEDRA.thenRespond(status(401, echoing("UNAUTHORIZED", CATEDRA_JWT)));

        startReservation();

        assertThat(CATEDRA.authorizations()).containsOnly("Bearer " + CATEDRA_JWT);
        assertSomethingWasLogged("rechazó el JWT técnico");
        assertNoSecretIsLogged();
    }

    @Test
    void aCatedraThatFailsOrCutsTheConnectionDoesNotLeakAnything() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL), status(200, ACCOUNT), status(200, PROFESSIONAL),
                status(200, ACCOUNT));
        CATEDRA.thenRespond(status(500, echoing("BOOM", USER_JWT + " " + RAW_PHONE)), cutConnection());

        startReservation();
        startReservation();

        assertThat(CATEDRA.authorizations()).containsOnly("Bearer " + CATEDRA_JWT);
        assertSomethingWasLogged("La cátedra respondió con un error del servidor status=500");
        assertSomethingWasLogged("La cátedra no respondió");
        assertNoSecretIsLogged();
    }

    @Test
    void aCatalogThatRejectsOrFailsDoesNotLeakTheUserJwt() throws Exception {
        CATALOG.thenRespond(status(401, echoing("UNAUTHORIZED", USER_JWT + " " + FIRST_NAME)), cutConnection());

        startReservation();
        startReservation();

        assertThat(CATALOG.authorizations()).containsOnly("Bearer " + USER_JWT);
        assertSomethingWasLogged("Catálogo respondió con un status inesperado");
        assertSomethingWasLogged("status=401");
        assertSomethingWasLogged("No se pudo conectar con catálogo");
        assertNoSecretIsLogged();
    }

    // ---------- Teléfono

    // El relay real corre solo (cada 100 ms): publica el mensaje, primero sin ack de Kafka y después bien
    @Test
    void sendingThePhoneAndPublishingItDoesNotLogItEvenWhenKafkaDoesNotConfirm() throws Exception {
        ReservationProcess seeded = seed(newProcessId(), ReservationStatus.WAITING_PHONE);
        doThrow(new PhonePublishException("sin ack", null)).doCallRealMethod().when(phonePublisher)
                .publish(any(), any(), any());

        submitPhone(seeded.getId()).andExpect(status().isAccepted());
        waitForTheLog("Kafka no confirmó");
        waitForTheOutboxToBeSent();

        assertThat(jdbc.sql("SELECT payload FROM outbox_events").query(String.class).single())
                .contains("+5492616159550");
        assertSomethingWasLogged("Teléfono recibido reservationId=");
        assertNoSecretIsLogged();
    }

    @Test
    void aMalformedBodyWithThePhoneDoesNotLogIt() throws Exception {
        ReservationProcess seeded = seed(newProcessId(), ReservationStatus.WAITING_PHONE);

        submitPhone(seeded.getId(), "{\"phoneNumber\": \"" + RAW_PHONE + "\"").andExpect(status().isBadRequest());
        submitPhone(seeded.getId(), "{\"phoneNumber\": \"" + RAW_PHONE + " ext\"}").andExpect(status().isBadRequest());
        submitPhone(seeded.getId(), "{\"phoneNumber\": [\"" + RAW_PHONE + "\"]}").andExpect(status().isBadRequest());

        assertNoSecretIsLogged();
    }

    // ---------- Cancelar

    @Test
    void cancellingDoesNotLogTheReasonNorTheJwts() throws Exception {
        ReservationProcess confirmed = seed(newProcessId(), ReservationStatus.CONFIRMED);
        CATEDRA.thenRespond(status(200, CANCELLED), status(500, "{}"));

        cancel(confirmed.getId()).andExpect(status().isOk());
        assertThat(CATEDRA.firstRequest().body()).contains(CANCEL_REASON);
        ReservationProcess other = seed(newProcessId(), ReservationStatus.CONFIRMED);
        cancel(other.getId());

        assertThat(CATEDRA.authorizations()).containsOnly("Bearer " + CATEDRA_JWT);
        assertSomethingWasLogged("Reserva cancelada reservationId=");
        assertSomethingWasLogged("La cátedra canceló la reserva reservationProcessId=");
        assertNoSecretIsLogged();
    }

    // ---------- Errores inesperados

    // El relay registra la excepción entera (mensaje y traza): ninguna debe arrastrar el teléfono del mensaje
    @Test
    void aFailureOfTheRelayIsLoggedWithoutTheMessageItWasPublishing() throws Exception {
        ReservationProcess seeded = seed(newProcessId(), ReservationStatus.WAITING_PHONE);
        doThrow(new IllegalStateException("fallo interno de prueba")).doCallRealMethod().when(phonePublisher)
                .publish(any(), any(), any());

        submitPhone(seeded.getId()).andExpect(status().isAccepted());
        waitForTheLog("El relay del outbox falló");
        waitForTheOutboxToBeSent();

        assertNoSecretIsLogged();
    }

    // ---------- Eventos de la cátedra por Kafka (el listener real, con su manejador de errores)

    @Test
    void theEventsFromTheCatedraAreLoggedWithoutAnyPersonalDataEvenWhenTheyAreInvalid() throws Exception {
        String known = newProcessId();
        seed(known, ReservationStatus.PHONE_SUBMITTED);
        String patient = "\"patient\":{\"firstName\":\"" + FIRST_NAME + "\",\"phoneNumber\":\"" + RAW_PHONE + "\"}";
        String last = "last-" + UUID.randomUUID();

        send(known, event("confirm-" + UUID.randomUUID(), "AppointmentConfirmed", known, "\"reservationId\":8251," + patient));
        send(known, "{\"phoneNumber\":\"" + RAW_PHONE + "\"");
        send(known, "{\"eventType\":\"AppointmentConfirmed\",\"reservationProcessId\":\"" + known + "\"," + patient + "}");
        send("desconocido", event("unknown-" + UUID.randomUUID(), "AppointmentConfirmed", "desconocido", patient));
        send(known, event(last, "TipoNuevo", known, patient));

        waitForTheLog(last);

        // Los inválidos llegan al manejador de errores del listener: deja el tópico y el offset, nunca el mensaje
        assertSomethingWasLogged("exhausted for");
        assertSomethingWasLogged("Falta el eventId");
        assertSomethingWasLogged("no se trata");
        assertNoSecretIsLogged();
    }
}
