package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
        ReservationProcess process = ReservationProcess.builder().id(7L).userId("42").status(from)
                .patientFirstName("Juan").patientLastName("Perez").patientPhone("+5492615551234").build();
        return new TransitionResult(outcome, from, target, process);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void anAppliedStepIsInfoWithBothStatuses() {
        transitionOutcomeLogger.log("AppointmentConfirmed", "evento-1", 7L, Optional.of(
                result(TransitionOutcome.APPLIED, ReservationStatus.PHONE_SUBMITTED, ReservationStatus.CONFIRMED)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Transición aplicada");
        assertThat(LoggedFields.of(event)).isEqualTo(Map.of("eventType", "AppointmentConfirmed",
                "eventId", "evento-1", "reservationId", 7L, "userId", "42", "from", "PHONE_SUBMITTED",
                "to", "CONFIRMED"));
    }

    @Test
    void completedDataIsInfoAndSaysTheStatusDidNotChange() {
        transitionOutcomeLogger.log("AdditionalInformationRequested", "evento-1", 7L, Optional.of(result(
                TransitionOutcome.DATA_COMPLETED, ReservationStatus.WAITING_PHONE, ReservationStatus.WAITING_PHONE)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Datos registrados, el estado no cambia");
        assertThat(LoggedFields.of(event)).containsEntry("reservationId", 7L).containsEntry("userId", "42")
                .containsEntry("status", "WAITING_PHONE");
    }

    @Test
    void aRepeatedStepIsInfoAndSaysNothingChanged() {
        transitionOutcomeLogger.log("AppointmentCancelled", "evento-1", 7L, Optional.of(
                result(TransitionOutcome.ALREADY_IN_STATE, ReservationStatus.CANCELLED, ReservationStatus.CANCELLED)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Sin cambios, ya estaba en ese estado");
        assertThat(LoggedFields.of(event)).containsEntry("reservationId", 7L).containsEntry("status", "CANCELLED");
    }

    @Test
    void aRejectedStepIsAWarningThatSaysItIsIgnored() {
        transitionOutcomeLogger.log("AppointmentConfirmed", "evento-1", 7L,
                Optional.of(result(TransitionOutcome.REJECTED, ReservationStatus.EXPIRED, ReservationStatus.CONFIRMED)));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Transición ignorada, el diagrama no la permite");
        assertThat(LoggedFields.of(event)).containsEntry("from", "EXPIRED").containsEntry("to", "CONFIRMED")
                .containsEntry("reservationId", 7L);
    }

    @Test
    void aMissingReservationIsAWarning() {
        transitionOutcomeLogger.log("AppointmentConfirmed", "evento-1", 7L, Optional.empty());

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Reserva inexistente");
        assertThat(LoggedFields.of(event)).isEqualTo(Map.of("eventType", "AppointmentConfirmed",
                "eventId", "evento-1", "reservationId", 7L));
    }

    // El usuario y el id del evento ayudan a buscar; si no se conocen, el campo no sale
    @Test
    void withoutAKnownUserOrEventIdTheFieldsAreLeftOut() {
        ReservationProcess process = ReservationProcess.builder().id(7L).status(ReservationStatus.HELD).build();

        transitionOutcomeLogger.log("Cancelación", null, 7L, Optional.of(new TransitionResult(
                TransitionOutcome.APPLIED, ReservationStatus.HELD, ReservationStatus.WAITING_PHONE, process)));

        assertThat(LoggedFields.of(onlyEvent())).isEqualTo(Map.of("eventType", "Cancelación", "reservationId", 7L,
                "from", "HELD", "to", "WAITING_PHONE"));
    }

    @Test
    void neverLogsPatientData() {
        for (TransitionOutcome outcome : TransitionOutcome.values()) {
            transitionOutcomeLogger.log("Origen", "evento-1", 7L,
                    Optional.of(result(outcome, ReservationStatus.HELD, ReservationStatus.WAITING_PHONE)));
        }

        assertThat(appender.list).hasSize(TransitionOutcome.values().length)
                .allSatisfy(event -> assertThat(LoggedFields.everything(event))
                        .doesNotContain("Juan").doesNotContain("Perez").doesNotContain("5492615551234"));
    }
}
