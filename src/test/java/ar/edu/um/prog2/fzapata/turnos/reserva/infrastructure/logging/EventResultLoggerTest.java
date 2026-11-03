package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;

class EventResultLoggerTest {

    private static final CatedraEvent EVENT = new CatedraEvent("evento-1", "AppointmentConfirmed", "process-1", 8251L,
            null, null);

    private final EventResultLogger eventResultLogger = new EventResultLogger(new TransitionOutcomeLogger());
    private final Logger eventLogger = (Logger) LoggerFactory.getLogger(EventResultLogger.class);
    private final Logger transitionLogger = (Logger) LoggerFactory.getLogger(TransitionOutcomeLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        eventLogger.setLevel(Level.DEBUG);
        transitionLogger.setLevel(Level.DEBUG);
        appender.start();
        eventLogger.addAppender(appender);
        transitionLogger.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        eventLogger.detachAppender(appender);
        transitionLogger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void aProcessedEventLogsTheTransitionWithTheEventAsOrigin() {
        ReservationProcess process = ReservationProcess.builder().id(7L).patientFirstName("Juan")
                .patientPhone("+5492615551234").build();
        EventResult result = EventResult.processed(new TransitionResult(TransitionOutcome.APPLIED,
                ReservationStatus.PHONE_SUBMITTED, ReservationStatus.CONFIRMED, process));

        eventResultLogger.log(EVENT, result);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo(
                "Evento AppointmentConfirmed evento-1: el proceso 7 pasó de PHONE_SUBMITTED a CONFIRMED")
                .doesNotContain("Juan").doesNotContain("5492615551234");
    }

    @Test
    void aDuplicateIsInfo() {
        eventResultLogger.log(EVENT, EventResult.without(EventOutcome.DUPLICATE));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("evento-1").contains("ya se había procesado");
    }

    @Test
    void anUnknownProcessIsAWarningWithTheProcessId() {
        eventResultLogger.log(EVENT, EventResult.without(EventOutcome.UNKNOWN_PROCESS));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("evento-1").contains("process-1").contains("se ignora");
    }

    @Test
    void anUnknownTypeIsAWarning() {
        eventResultLogger.log(new CatedraEvent("evento-2", "AlgoNuevo", "process-1", null, null, null),
                EventResult.without(EventOutcome.UNKNOWN_TYPE));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("AlgoNuevo").contains("evento-2").contains("se ignora");
    }
}
