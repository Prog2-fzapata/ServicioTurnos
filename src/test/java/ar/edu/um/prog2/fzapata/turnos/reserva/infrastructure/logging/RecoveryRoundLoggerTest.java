package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RecoveryRoundLoggerTest {

    private final RecoveryRoundLogger roundLogger = new RecoveryRoundLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RecoveryRoundLogger.class);
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

    // Lo que merece atención sale como aviso; lo normal, como info. El último mensaje es siempre el resumen
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"ORPHAN_FAILED, WARN", "CONFIRMED_BY_CATEDRA, WARN", "STILL_OPEN, INFO", "UNCHANGED, INFO",
            "UNVERIFIED, WARN", "CONFLICT, WARN"})
    void eachOutcomeIsLoggedAtItsLevelWithTheProcess(RecoveryOutcome outcome, String level) {
        roundLogger.log(List.of(new RecoveryResult(7L, ReservationStatus.PHONE_SUBMITTED, outcome)));

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.getFirst().getLevel().toString()).isEqualTo(level);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("proceso 7");
    }

    @Test
    void theRoundEndsWithASummary() {
        roundLogger.log(List.of(
                new RecoveryResult(1L, ReservationStatus.HOLD_REQUESTED, RecoveryOutcome.ORPHAN_FAILED),
                new RecoveryResult(2L, ReservationStatus.PHONE_SUBMITTED, RecoveryOutcome.STILL_OPEN)));

        assertThat(appender.list.getLast().getFormattedMessage()).contains("2 procesos revisados");
        assertThat(appender.list.getLast().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void aRoundWithNothingToDoStillSaysSo() {
        roundLogger.log(List.of());

        assertThat(appender.list).singleElement().satisfies(
                event -> assertThat(event.getFormattedMessage()).contains("0 procesos revisados"));
    }
}
