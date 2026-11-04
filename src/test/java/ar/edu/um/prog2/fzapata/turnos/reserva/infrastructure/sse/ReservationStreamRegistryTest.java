package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.shared.config.StreamProperties;

// Sin Spring: un controller mínimo abre las conexiones y MockMvc deja leer lo que el registro escribe en cada una
class ReservationStreamRegistryTest {

    private static final Instant NOW = Instant.parse("2026-11-04T13:00:00Z");
    private static final ReservationUpdate UPDATE = new ReservationUpdate(101L, ReservationStatus.CONFIRMED, null,
            false, Instant.parse("2026-11-04T12:59:00Z"));

    private final List<SseEmitter> opened = new ArrayList<>();
    private ReservationStreamRegistry registry = registry(5);
    private MockMvc mockMvc = mockMvc();

    @RestController
    private class StreamStub {

        @GetMapping(path = "/stream/{user}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter open(@PathVariable String user, @RequestParam(required = false) Long expiresAt) {
            SseEmitter emitter = registry.register(user, expiresAt == null ? null : Instant.ofEpochSecond(expiresAt));
            opened.add(emitter);
            return emitter;
        }
    }

    private ReservationStreamRegistry registry(int maxConnectionsPerUser) {
        return new ReservationStreamRegistry(new StreamProperties(Duration.ofSeconds(25), maxConnectionsPerUser),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new StreamStub()).build();
    }

    private void limitTo(int maxConnectionsPerUser) {
        registry = registry(maxConnectionsPerUser);
        mockMvc = mockMvc();
    }

    private MvcResult connect(String user) throws Exception {
        return mockMvc.perform(get("/stream/{user}", user)).andExpect(request().asyncStarted()).andReturn();
    }

    private MvcResult connect(String user, Instant expiresAt) throws Exception {
        return mockMvc.perform(get("/stream/{user}", user).param("expiresAt",
                String.valueOf(expiresAt.getEpochSecond()))).andExpect(request().asyncStarted()).andReturn();
    }

    private static String received(MvcResult connection) throws Exception {
        return connection.getResponse().getContentAsString();
    }

    private static long count(String text, String fragment) {
        return text.lines().filter(line -> line.contains(fragment)).count();
    }

    // Sin esto el cliente no recibe ni las cabeceras hasta el primer evento, que puede tardar horas
    @Test
    void connectingSendsACommentRightAwayToOpenTheResponse() throws Exception {
        MvcResult connection = connect("42");

        assertThat(received(connection)).contains(":ping");
        assertThat(connection.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(registry.connectionCount("42")).isEqualTo(1);
    }

    @Test
    void anEventCarriesItsNameItsIdAndTheSummaryAsJson() throws Exception {
        MvcResult connection = connect("42");

        registry.send("42", "101-5", UPDATE);

        assertThat(received(connection)).contains("event:reservation-updated", "id:101-5",
                "\"id\":101", "\"status\":\"CONFIRMED\"", "\"canSubmitPhone\":false",
                "\"updatedAt\":\"2026-11-04T12:59:00Z\"");
    }

    @Test
    void anEventReachesOnlyTheConnectionsOfItsUser() throws Exception {
        MvcResult mine = connect("42");
        MvcResult someoneElses = connect("43");

        registry.send("42", "101-5", UPDATE);

        assertThat(count(received(mine), "event:reservation-updated")).isEqualTo(1);
        assertThat(received(someoneElses)).doesNotContain("reservation-updated");
    }

    @Test
    void everyConnectionOfTheUserGetsTheEvent() throws Exception {
        MvcResult phone = connect("42");
        MvcResult tablet = connect("42");

        registry.send("42", "101-5", UPDATE);

        assertThat(count(received(phone), "event:reservation-updated")).isEqualTo(1);
        assertThat(count(received(tablet), "event:reservation-updated")).isEqualTo(1);
        assertThat(registry.connectionCount("42")).isEqualTo(2);
    }

    @Test
    void sendingToAUserWithoutConnectionsDoesNothing() {
        registry.send("99", "101-5", UPDATE);

        assertThat(registry.connectionCount("99")).isZero();
    }

    @Test
    void theHeartbeatPingsEveryConnection() throws Exception {
        MvcResult first = connect("42");
        MvcResult second = connect("43");

        registry.heartbeat();

        assertThat(count(received(first), ":ping")).isEqualTo(2);
        assertThat(count(received(second), ":ping")).isEqualTo(2);
    }

    // El JWT venció: ya no se avisa a esa conexión y la app tendrá que autenticarse de nuevo
    @Test
    void theHeartbeatClosesAConnectionWhoseTokenHasExpired() throws Exception {
        MvcResult expired = connect("42", NOW.minusSeconds(1));
        MvcResult valid = connect("42", NOW.plusSeconds(60));

        registry.heartbeat();
        registry.send("42", "101-5", UPDATE);

        assertThat(registry.connectionCount("42")).isEqualTo(1);
        assertThat(received(expired)).doesNotContain("reservation-updated");
        assertThat(received(valid)).contains("reservation-updated");
    }

    @Test
    void aTokenExpiringRightNowIsAlreadyExpired() throws Exception {
        connect("42", NOW);

        registry.heartbeat();

        assertThat(registry.connectionCount("42")).isZero();
    }

    @Test
    void aTokenWithoutExpirationNeverExpires() throws Exception {
        connect("42");

        registry.heartbeat();

        assertThat(registry.connectionCount("42")).isEqualTo(1);
    }

    @Test
    void exceedingTheLimitClosesTheOldestConnection() throws Exception {
        limitTo(2);
        MvcResult oldest = connect("42");
        MvcResult middle = connect("42");
        MvcResult newest = connect("42");

        registry.send("42", "101-5", UPDATE);

        assertThat(registry.connectionCount("42")).isEqualTo(2);
        assertThat(received(oldest)).doesNotContain("reservation-updated");
        assertThat(received(middle)).contains("reservation-updated");
        assertThat(received(newest)).contains("reservation-updated");
    }

    @Test
    void theLimitIsPerUser() throws Exception {
        limitTo(1);
        connect("42");
        connect("43");

        assertThat(registry.connectionCount("42")).isEqualTo(1);
        assertThat(registry.connectionCount("43")).isEqualTo(1);
    }

    // Un cliente que se fue no debe seguir ocupando lugar ni hacer fallar los envíos a los demás
    @Test
    void aConnectionThatAlreadyClosedIsDroppedWhenSendingAndTheOthersStillReceive() throws Exception {
        connect("42");
        MvcResult alive = connect("42");
        opened.getFirst().complete();

        registry.send("42", "101-5", UPDATE);

        assertThat(registry.connectionCount("42")).isEqualTo(1);
        assertThat(received(alive)).contains("reservation-updated");
    }

    @Test
    void aClosedConnectionIsDroppedByTheHeartbeatToo() throws Exception {
        connect("42");
        opened.getFirst().complete();

        registry.heartbeat();

        assertThat(registry.connectionCount("42")).isZero();
    }

    // Al apagar el servicio los streams se cierran, o el apagado esperaría a conexiones que nunca terminan
    @Test
    void stoppingClosesEveryConnectionOfEveryUser() throws Exception {
        registry.start();
        connect("42");
        connect("43");

        registry.stop();

        assertThat(registry.isRunning()).isFalse();
        assertThat(registry.connectionCount("42")).isZero();
        assertThat(registry.connectionCount("43")).isZero();
    }

    @Test
    void itStopsBeforeTheWebServerWhichWaitsForOpenConnections() {
        assertThat(registry.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void itIsRunningOnceStarted() {
        registry.start();

        assertThat(registry.isRunning()).isTrue();
    }
}
