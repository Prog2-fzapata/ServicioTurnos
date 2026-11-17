package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

class ReservationActionLoggerTest {

    private final ReservationActionLogger actionLogger = new ReservationActionLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ReservationActionLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    // Con datos del paciente a propósito: el logger no debe sacarlos nunca
    private static ReservationProcess process(ReservationStatus status) {
        return ReservationProcess.builder().id(3L).userId("42").status(status).patientFirstName("Juan")
                .patientLastName("Perez").patientPhone("+5492611234567").cancellationReason("no puedo ir").build();
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void aStartedReservationIsInfoWithItsIdTheUserAndTheStatus() {
        actionLogger.started(process(ReservationStatus.WAITING_PHONE));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Reserva iniciada");
        assertThat(LoggedFields.of(event)).isEqualTo(Map.of("reservationId", 3L, "userId", "42",
                "status", "WAITING_PHONE"));
    }

    @Test
    void aReceivedPhoneIsInfoWithItsIdTheUserAndTheStatus() {
        actionLogger.phoneReceived(process(ReservationStatus.PHONE_QUEUED));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Teléfono recibido");
        assertThat(LoggedFields.of(event)).isEqualTo(Map.of("reservationId", 3L, "userId", "42",
                "status", "PHONE_QUEUED"));
    }

    @Test
    void aCancelledReservationIsInfoWithItsIdTheUserAndTheStatus() {
        actionLogger.cancelled(process(ReservationStatus.CANCELLED));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Reserva cancelada");
        assertThat(LoggedFields.of(event)).isEqualTo(Map.of("reservationId", 3L, "userId", "42",
                "status", "CANCELLED"));
    }

    @Test
    void neverLogsThePatientsPhoneNameOrTheCancellationReason() {
        actionLogger.started(process(ReservationStatus.WAITING_PHONE));
        actionLogger.phoneReceived(process(ReservationStatus.PHONE_QUEUED));
        actionLogger.cancelled(process(ReservationStatus.CANCELLED));

        assertThat(appender.list).hasSize(3).allSatisfy(event -> assertThat(LoggedFields.everything(event))
                .doesNotContain("Juan").doesNotContain("Perez").doesNotContain("1234567")
                .doesNotContain("no puedo ir"));
    }
}
