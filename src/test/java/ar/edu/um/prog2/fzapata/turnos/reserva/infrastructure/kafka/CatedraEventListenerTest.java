package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.EventResultLogger;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.TransitionOutcomeLogger;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CatedraEventListenerTest {

    private static final String PAYLOAD = "{\"eventId\":\"evento-1\",\"eventType\":\"AppointmentConfirmed\","
            + "\"reservationProcessId\":\"process-1\",\"reservationId\":8251}";
    private static final CatedraEvent EVENT = new CatedraEvent("evento-1", "AppointmentConfirmed", "process-1", 8251L,
            null, null);

    @Mock
    private ReservaService reservaService;

    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

    private CatedraEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new CatedraEventListener(reservaService, new CatedraEventMapper(JsonMapper.builder().build()),
                new EventResultLogger(new TransitionOutcomeLogger()));
    }

    @Test
    void confirmsTheOffsetOnlyAfterTheEventWasProcessed() {
        when(reservaService.processEvent(EVENT)).thenReturn(EventResult.without(EventOutcome.UNKNOWN_PROCESS));

        listener.onEvent(PAYLOAD, acknowledgment);

        InOrder order = inOrder(reservaService, acknowledgment);
        order.verify(reservaService).processEvent(EVENT);
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void confirmsTheOffsetOfADuplicateEventToo() {
        when(reservaService.processEvent(EVENT)).thenReturn(EventResult.without(EventOutcome.DUPLICATE));

        listener.onEvent(PAYLOAD, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotConfirmTheOffsetWhenProcessingFails() {
        ReservationConflictException failure = new ReservationConflictException(7L, null);
        when(reservaService.processEvent(EVENT)).thenThrow(failure);

        assertThatThrownBy(() -> listener.onEvent(PAYLOAD, acknowledgment)).isSameAs(failure);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void anInvalidMessageIsNeitherProcessedNorConfirmed() {
        assertThatThrownBy(() -> listener.onEvent("no es json", acknowledgment))
                .isInstanceOf(InvalidCatedraEventException.class);

        verifyNoInteractions(reservaService);
        verify(acknowledgment, never()).acknowledge();
    }
}
