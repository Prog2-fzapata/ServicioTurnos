package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestConstructor;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse.ReservationStreamRegistry;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse.ReservationUpdate;
import lombok.RequiredArgsConstructor;

// El stream por HTTP real, con servidor, Spring Security y un cliente que lee línea por línea: lo que MockMvc no
// puede mostrar (cabeceras, cierre de la conexión, JWT vencido). El latido va cada 200 ms para no esperar
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stream.heartbeat-interval=200ms")
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationStreamHttpIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");

    private final JdbcClient jdbc;
    private final ReservaService reservaService;
    private final ReservationProcessRepository repository;
    private final ReservationStreamRegistry registry;
    private final SecretKey jwtSecretKey;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Stream<String>> openStreams = new ArrayList<>();

    // Lo que el servidor va escribiendo, línea por línea; END marca que cerró la conexión
    private static final String END = "<<fin del stream>>";

    @AfterEach
    void closeTheStreams() {
        openStreams.forEach(Stream::close);
        openStreams.clear();
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private String token(String userId, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("maria").issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(expiresAt).claim("uid", userId).claim("auth", "ROLE_USER").build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).build(), claims)).getTokenValue();
    }

    private HttpRequest streamRequest(String token) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/reservations/stream"))
                .header("Accept", "text/event-stream").header("Authorization", "Bearer " + token).GET().build();
    }

    private BlockingQueue<String> open(String token) throws Exception {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        HttpResponse<Stream<String>> response = client.send(streamRequest(token), HttpResponse.BodyHandlers.ofLines());
        openStreams.add(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        Thread reader = new Thread(() -> {
            response.body().forEach(lines::add);
            lines.add(END);
        });
        reader.setDaemon(true);
        reader.start();
        return lines;
    }

    // Descarta líneas hasta encontrar la que cumple; falla si no llega a tiempo
    private String awaitLine(BlockingQueue<String> lines, String fragment) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line != null && line.contains(fragment)) {
                return line;
            }
        }
        throw new AssertionError("No llegó una línea con «" + fragment + "» en " + WAIT);
    }

    // Todas las líneas hasta la que cumple, inclusive
    private List<String> readUntil(BlockingQueue<String> lines, String fragment) throws InterruptedException {
        List<String> read = new ArrayList<>();
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line != null) {
                read.add(line);
                if (line.contains(fragment)) {
                    return read;
                }
            }
        }
        throw new AssertionError("No llegó una línea con «" + fragment + "» en " + WAIT);
    }

    private ReservationProcess heldProcess(String userId) {
        return repository.save(ReservationProcess.builder().userId(userId).reservationProcessId("process-1")
                .holdId("hold-1").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 11, 9)).startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 0)).status(ReservationStatus.HELD)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10))).patientFirstName("Juan")
                .patientLastName("Perez").createdAt(T0).updatedAt(T0).build());
    }

    @Test
    void theClientGetsTheHeadersRightAwayThenThePingsAndTheChangesOfItsUser() throws Exception {
        ReservationProcess held = heldProcess("42");
        BlockingQueue<String> lines = open(token("42", Instant.now().plus(Duration.ofHours(1))));
        awaitLine(lines, ":ping");

        reservaService.applyTransition(held.getId(), ReservationTransition.to(ReservationStatus.WAITING_PHONE));

        awaitLine(lines, "event:reservation-updated");
        assertThat(awaitLine(lines, "data:")).contains("\"status\":\"WAITING_PHONE\"")
                .contains("\"id\":" + held.getId());
    }

    @Test
    void aStreamOfAnotherUserNeverSeesTheChange() throws Exception {
        ReservationProcess held = heldProcess("42");
        BlockingQueue<String> someoneElses = open(token("99", Instant.now().plus(Duration.ofHours(1))));
        awaitLine(someoneElses, ":ping");

        reservaService.applyTransition(held.getId(), ReservationTransition.to(ReservationStatus.WAITING_PHONE));
        // Un evento marcador por la misma conexión: la conexión conserva el orden, así que cuando el marcador llega
        // todo lo enviado antes ya está leído y se puede afirmar que el cambio no estuvo
        registry.send("99", "marker", new ReservationUpdate(0L, ReservationStatus.HELD, null, false, T0));

        List<String> untilTheMarker = readUntil(someoneElses, "id:marker");

        assertThat(untilTheMarker).noneMatch(line -> line.startsWith("id:" + held.getId() + "-"));
    }

    // Una vez enviada la cabecera 200 no existe un 401: el servidor cierra el stream y la app debe renovar el token
    @Test
    void whenTheTokenExpiresTheServerClosesTheStream() throws Exception {
        BlockingQueue<String> lines = open(token("42", Instant.now().plusSeconds(2)));
        awaitLine(lines, ":ping");

        awaitLine(lines, END);
    }

    // El decodificador de Spring tolera 60 s de desfase de reloj: un token vencido hace menos que eso todavía se acepta
    // y la conexión nueva se cierra de nuevo en el siguiente latido. Por eso la app no debe reconectar con el mismo
    // token sino renovarlo; pasada la tolerancia el intento recibe 401 (lo cubre SecurityRejectionTest)
    @Test
    void withinTheClockSkewAnExpiredTokenReconnectsAndIsClosedAgainAtTheNextHeartbeat() throws Exception {
        String token = token("42", Instant.now().plusSeconds(1));
        BlockingQueue<String> lines = open(token);
        awaitLine(lines, END);

        BlockingQueue<String> again = open(token);

        awaitLine(again, END);
    }
}
