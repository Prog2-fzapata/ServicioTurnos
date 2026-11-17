package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;

class PhoneDeliveryLoggerTest {

    private final PhoneDeliveryLogger phoneDeliveryLogger = new PhoneDeliveryLogger(new TransitionOutcomeLogger());
    private final Logger deliveryLogger = (Logger) LoggerFactory.getLogger(PhoneDeliveryLogger.class);
    private final Logger transitionLogger = (Logger) LoggerFactory.getLogger(TransitionOutcomeLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        deliveryLogger.setLevel(Level.DEBUG);
        transitionLogger.setLevel(Level.DEBUG);
        appender.start();
        deliveryLogger.addAppender(appender);
        transitionLogger.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        deliveryLogger.detachAppender(appender);
        transitionLogger.detachAppender(appender);
        appender.stop();
    }

    // Con el teléfono dentro del payload a propósito: el logger no debe sacarlo nunca
    private static OutboxEvent event(int attempts) {
        return OutboxEvent.builder().id(3L).eventId("phone-submitted-1").reservationProcessId("process-1")
                .payload("{\"information\":{\"phoneNumber\":\"+5492615551234\"}}").status(OutboxStatus.PENDING)
                .attempts(attempts).build();
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void aSentMessageLogsTheStateChangeOfTheReservation() {
        TransitionResult transition = new TransitionResult(TransitionOutcome.APPLIED, ReservationStatus.PHONE_QUEUED,
                ReservationStatus.PHONE_SUBMITTED, ReservationProcess.builder().id(7L).userId("42").build());

        phoneDeliveryLogger.log(List.of(new PhoneDelivery(event(0), true, Optional.of(transition))));

        ILoggingEvent logged = onlyEvent();
        assertThat(logged.getLevel()).isEqualTo(Level.INFO);
        assertThat(logged.getFormattedMessage()).isEqualTo("Transición aplicada");
        assertThat(LoggedFields.of(logged)).isEqualTo(Map.of("eventType", "AdditionalInformationSubmitted",
                "eventId", "phone-submitted-1", "reservationId", 7L, "userId", "42", "from", "PHONE_QUEUED",
                "to", "PHONE_SUBMITTED"));
    }

    @Test
    void aMessageThatKafkaDidNotConfirmIsAWarningWithTheAttemptNumber() {
        phoneDeliveryLogger.log(List.of(new PhoneDelivery(event(2), false, Optional.empty())));

        ILoggingEvent logged = onlyEvent();
        assertThat(logged.getLevel()).isEqualTo(Level.WARN);
        assertThat(logged.getFormattedMessage()).isEqualTo("Kafka no confirmó el teléfono, queda pendiente");
        assertThat(LoggedFields.of(logged)).isEqualTo(Map.of("eventId", "phone-submitted-1",
                "reservationProcessId", "process-1", "attempt", 2));
    }

    @Test
    void aMessageThatWasAlreadySentIsInfo() {
        phoneDeliveryLogger.log(List.of(new PhoneDelivery(event(0), true, Optional.empty())));

        ILoggingEvent logged = onlyEvent();
        assertThat(logged.getLevel()).isEqualTo(Level.INFO);
        assertThat(logged.getFormattedMessage()).isEqualTo("El teléfono ya figuraba como enviado");
        assertThat(LoggedFields.of(logged)).isEqualTo(Map.of("eventId", "phone-submitted-1"));
    }

    @Test
    void neverLogsThePhoneNumberNorThePayload() {
        TransitionResult transition = new TransitionResult(TransitionOutcome.REJECTED, ReservationStatus.CONFIRMED,
                ReservationStatus.PHONE_SUBMITTED, ReservationProcess.builder().id(7L).patientPhone("+5492615551234")
                        .build());

        phoneDeliveryLogger.log(List.of(new PhoneDelivery(event(0), true, Optional.of(transition)),
                new PhoneDelivery(event(1), false, Optional.empty()),
                new PhoneDelivery(event(0), true, Optional.empty())));

        assertThat(appender.list).hasSize(3).allSatisfy(logged -> assertThat(LoggedFields.everything(logged))
                .doesNotContain("5492615551234").doesNotContain("phoneNumber"));
    }

    @Test
    void nothingToReportLogsNothing() {
        phoneDeliveryLogger.log(List.of());

        assertThat(appender.list).isEmpty();
    }
}
