package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

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
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;

class TransitionOutcomeLoggerTest {

    private final TransitionOutcomeLogger transitionOutcomeLogger = new TransitionOutcomeLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TransitionOutcomeLogger.class);
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

    private static TransitionResult result(TransitionOutcome outcome, ReservationStatus from, ReservationStatus target) {
        // Con datos del paciente a propósito: el logger no debe sacarlos nunca
        ReservationProcess process = ReservationProcess.builder().id(7L).status(from).patientFirstName("Juan")
                .patientLastName("Perez").patientPhone("+5492615551234").build();
        return new TransitionResult(outcome, from, target, process);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void anAppliedStepIsInfoWithBothStatuses() {
        transitionOutcomeLogger.log("Evento AppointmentConfirmed", 7L, Optional.of(
                result(TransitionOutcome.APPLIED, ReservationStatus.PHONE_SUBMITTED, ReservationStatus.CONFIRMED)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo(
                "Evento AppointmentConfirmed: el proceso 7 pasó de PHONE_SUBMITTED a CONFIRMED");
    }

    @Test
    void aRepeatedStepIsInfoAndSaysNothingChanged() {
        transitionOutcomeLogger.log("Cancelación", 7L, Optional.of(
                result(TransitionOutcome.ALREADY_IN_STATE, ReservationStatus.CANCELLED, ReservationStatus.CANCELLED)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Cancelación: el proceso 7 ya estaba en CANCELLED, sin cambios");
    }

    @Test
    void aRejectedStepIsAWarningThatSaysItIsIgnored() {
        transitionOutcomeLogger.log("Evento AppointmentConfirmed", 7L,
                Optional.of(result(TransitionOutcome.REJECTED, ReservationStatus.EXPIRED, ReservationStatus.CONFIRMED)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("se ignora").contains("EXPIRED").contains("CONFIRMED")
                .contains("proceso 7");
    }

    @Test
    void aMissingProcessIsAWarning() {
        transitionOutcomeLogger.log("Evento AppointmentConfirmed", 7L, Optional.empty());

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Evento AppointmentConfirmed: el proceso de reserva 7 no existe");
    }

    @Test
    void neverLogsPatientData() {
        for (TransitionOutcome outcome : TransitionOutcome.values()) {
            transitionOutcomeLogger.log("Origen", 7L,
                    Optional.of(result(outcome, ReservationStatus.HELD, ReservationStatus.WAITING_PHONE)));
        }

        assertThat(appender.list).hasSize(TransitionOutcome.values().length)
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain("Juan").doesNotContain("Perez").doesNotContain("5492615551234"));
    }
}
