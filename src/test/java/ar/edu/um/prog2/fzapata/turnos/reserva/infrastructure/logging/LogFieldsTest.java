package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LogFieldsTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogFieldsTest.class);
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

    @Test
    void roundAddsTheReservationAndTheStateItWasIn() {
        LogFields.round(logger.atInfo().setMessage("ronda"),
                new ExpirationResult(42L, ReservationStatus.HELD, ExpirationOutcome.EXPIRED)).log();

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(LoggedFields.of(event))
                .containsExactly(Map.entry("reservationId", 42L), Map.entry("status", "HELD")));
    }

    // Los dos resultados de ronda se anotan igual
    @Test
    void roundTreatsEveryKindOfResultTheSame() {
        LogFields.round(logger.atInfo().setMessage("ronda"),
                new RecoveryResult(7L, ReservationStatus.HOLD_REQUESTED, RecoveryOutcome.ORPHAN_FAILED)).log();

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(LoggedFields.of(event))
                .containsExactly(Map.entry("reservationId", 7L), Map.entry("status", "HOLD_REQUESTED")));
    }
}
