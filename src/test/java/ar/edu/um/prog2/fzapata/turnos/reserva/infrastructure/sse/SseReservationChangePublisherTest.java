package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

class SseReservationChangePublisherTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-11-04T13:00:05Z");

    private final ReservationStreamRegistry registry = mock(ReservationStreamRegistry.class);
    private final SseReservationChangePublisher publisher = new SseReservationChangePublisher(registry,
            new ReservationUpdateMapper());

    private static ReservationProcess process(ReservationStatus status) {
        return ReservationProcess.builder().id(101L).userId("42").status(status).updatedAt(UPDATED_AT).version(5L)
                .build();
    }

    // Simula la transacción de un caso de uso: el aviso queda registrado y corre al terminar
    private void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
    }

    private void rollBack() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    private final Logger loggedClass = (Logger) LoggerFactory.getLogger(SseReservationChangePublisher.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        loggedClass.setLevel(Level.DEBUG);
        appender.start();
        loggedClass.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        loggedClass.detachAppender(appender);
        appender.stop();
    }

    // Los campos de una línea de log (los que salen como campos del JSON)
    private static Map<String, Object> fieldsOf(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        return fields;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aMissingProcessIsRejectedRightAway() {
        assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("El proceso a publicar es obligatorio");

        verifyNoInteractions(registry);
    }

    @Test
    void withoutATransactionTheEventGoesOutRightAway() {
        publisher.publish(process(ReservationStatus.WAITING_PHONE));

        verify(registry).send("42", "101-5",
                new ReservationUpdate(101L, ReservationStatus.WAITING_PHONE, null, false, UPDATED_AT));
    }

    // Antes del COMMIT la app podría consultar y no ver el cambio
    @Test
    void insideATransactionTheEventWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(process(ReservationStatus.WAITING_PHONE));
        verifyNoInteractions(registry);

        commit();
        verify(registry).send("42", "101-5",
                new ReservationUpdate(101L, ReservationStatus.WAITING_PHONE, null, false, UPDATED_AT));
    }

    // Un aviso de algo que se deshizo sería falso
    @Test
    void aRolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(process(ReservationStatus.WAITING_PHONE));
        rollBack();

        verifyNoInteractions(registry);
    }

    @Test
    void theContentIsFixedWhenPublishingNotWhenCommitting() {
        TransactionSynchronizationManager.initSynchronization();
        ReservationProcess process = process(ReservationStatus.HELD);

        publisher.publish(process);
        process.setStatus(ReservationStatus.CANCELLED);
        commit();

        verify(registry).send("42", "101-5", new ReservationUpdate(101L, ReservationStatus.HELD, null, false,
                UPDATED_AT));
    }

    // Si el aviso fallara después del COMMIT, el consumer de Kafka reintentaría un evento que ya se aplicó
    @Test
    void aFailureWhileSendingNeverEscapes() {
        doThrow(new IllegalStateException("el registro falló")).when(registry).send(anyString(), anyString(), any());

        assertThatCode(() -> publisher.publish(process(ReservationStatus.HELD))).doesNotThrowAnyException();
    }

    @Test
    void aFailureAfterTheCommitNeverEscapesEither() {
        doThrow(new IllegalStateException("el registro falló")).when(registry).send(anyString(), anyString(), any());
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(process(ReservationStatus.HELD));

        assertThatCode(this::commit).doesNotThrowAnyException();
    }

    @Test
    void aFailureWhileSendingLeavesAWarningWithTheEventIdAndTheCause() {
        doThrow(new IllegalStateException("el registro falló")).when(registry).send(anyString(), anyString(), any());

        publisher.publish(process(ReservationStatus.HELD));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("No se pudo avisar el cambio por el stream");
            assertThat(fieldsOf(event)).containsOnlyKeys("eventId");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("el registro falló");
        });
    }
}
