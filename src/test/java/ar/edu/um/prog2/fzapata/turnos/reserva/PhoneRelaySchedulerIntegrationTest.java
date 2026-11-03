package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.um.prog2.fzapata.turnos.KafkaTestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhonePublisher;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// El relay automático encendido, con una vuelta cada 100 ms: nadie lo dispara a mano
@SpringBootTest(properties = {"outbox.relay-enabled=true", "outbox.relay-interval=100ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class PhoneRelaySchedulerIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservationProcessRepository reservationProcessRepository;

    @MockitoSpyBean
    private PhonePublisher phonePublisher;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private ReservationProcess seed() {
        return reservationProcessRepository.save(ReservationProcess.builder().userId("42")
                .reservationProcessId("process-scheduler").holdId("hold-1").professionalId(15L)
                .professionalFirstName("Ana").professionalLastName("Gomez").appointmentDate(LocalDate.of(2026, 11, 9))
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(ReservationStatus.WAITING_PHONE)
                .requestEventId("request-event-8a31").expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .patientFirstName("Juan").patientLastName("Perez").createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    private void submit(Long id) throws Exception {
        mockMvc.perform(post("/api/reservations/" + id + "/phone").with(jwt().jwt(token -> token.claim("uid", "42")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\": \"2616159550\"}"))
                .andExpect(status().isAccepted());
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

    private String outboxStatus() {
        return jdbc.sql("SELECT status FROM outbox_events").query(String.class).single();
    }

    private String processStatus() {
        return jdbc.sql("SELECT status FROM reservation_processes").query(String.class).single();
    }

    @Test
    void theRelayPublishesOnItsOwnAndLeavesTheReservationSubmitted() throws Exception {
        submit(seed().getId());

        waitUntil("el mensaje enviado y la reserva en PHONE_SUBMITTED",
                () -> "SENT".equals(outboxStatus()) && "PHONE_SUBMITTED".equals(processStatus()));
    }

    // Un fallo de Kafka no tumba el relay: sigue intentando en cada vuelta hasta que el mensaje sale
    @Test
    void ifKafkaFailsTheRelayKeepsTryingOnEveryRoundUntilTheMessageGoesOut() throws Exception {
        doThrow(new PhonePublishException("sin ack", null)).doThrow(new PhonePublishException("sin ack", null))
                .doCallRealMethod().when(phonePublisher).publish(any(), any(), any());
        submit(seed().getId());

        waitUntil("el mensaje enviado", () -> "SENT".equals(outboxStatus()));

        assertThat(jdbc.sql("SELECT attempts FROM outbox_events").query(Integer.class).single()).isEqualTo(2);
        assertThat(processStatus()).isEqualTo("PHONE_SUBMITTED");
    }
}
