package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.ExpirationRoundLogger;
import lombok.RequiredArgsConstructor;

// La tarea automática encendida y con el margen en cero: se deja un proceso vencido y se espera a que la tarea lo cierre
// sola. Se espera con el timeout de Mockito sobre el logger de la vuelta, sin dormir el hilo del test
@SpringBootTest(properties = {"expiration.enabled=true", "expiration.interval=200ms", "expiration.grace=0s"})
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ExpirationSchedulerIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");

    private final JdbcClient jdbc;
    private final ReservationProcessRepository repository;

    @MockitoSpyBean
    private ExpirationRoundLogger roundLogger;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    @Test
    void theTaskExpiresAnOverdueProcessOnItsOwn() {
        ReservationProcess held = repository.save(ReservationProcess.builder().userId("42")
                .reservationProcessId("process-1").holdId("hold-1").professionalId(15L).professionalFirstName("Ana")
                .professionalLastName("Gomez").appointmentDate(LocalDate.of(2026, 11, 9))
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).status(ReservationStatus.HELD)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(5))).patientFirstName("Juan")
                .patientLastName("Perez").createdAt(T0).updatedAt(T0).build());

        verify(roundLogger, timeout(15_000)).log(argThat(results -> results.stream().anyMatch(
                result -> result.processId().equals(held.getId()) && result.outcome() == ExpirationOutcome.EXPIRED)));

        assertThat(jdbc.sql("SELECT status FROM reservation_processes WHERE id = :id").param("id", held.getId())
                .query(String.class).single()).isEqualTo("EXPIRED");
    }
}
