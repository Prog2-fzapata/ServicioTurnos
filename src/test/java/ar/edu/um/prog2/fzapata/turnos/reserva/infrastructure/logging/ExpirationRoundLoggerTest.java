package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ExpirationRoundLoggerTest {

    private final ExpirationRoundLogger roundLogger = new ExpirationRoundLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExpirationRoundLogger.class);
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

    // Lo que merece atención (la cátedra sin responder, un choque, una reparación) sale como aviso; lo normal, como info
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"EXPIRED, INFO", "UNCHANGED, INFO", "CONFIRMED_BY_CATEDRA, WARN", "UNVERIFIED, WARN",
            "CONFLICT, WARN"})
    void eachOutcomeIsLoggedAtItsLevelWithTheReservationAndItsStatus(ExpirationOutcome outcome, String level) {
        roundLogger.log(List.of(new ExpirationResult(7L, ReservationStatus.PHONE_SUBMITTED, outcome)));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel().toString()).isEqualTo(level);
            assertThat(event.getFormattedMessage()).startsWith("Vencimiento:");
            assertThat(LoggedFields.of(event)).isEqualTo(Map.of("reservationId", 7L, "status", "PHONE_SUBMITTED"));
        });
    }

    @Test
    void theMessagesNameTheOutcomeInWords() {
        roundLogger.log(List.of(
                new ExpirationResult(1L, ReservationStatus.HELD, ExpirationOutcome.EXPIRED),
                new ExpirationResult(2L, ReservationStatus.PHONE_SUBMITTED, ExpirationOutcome.CONFIRMED_BY_CATEDRA)));

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).satisfiesExactly(
                first -> assertThat(first).isEqualTo("Vencimiento: reserva expirada"),
                second -> assertThat(second).contains("la tenía confirmada").contains("CONFIRMED"));
    }

    @Test
    void aRoundWithNothingToDoLogsNothing() {
        roundLogger.log(List.of());

        assertThat(appender.list).isEmpty();
    }
}
