package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ProcessedEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ProcessCatedraEventUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ProcessedEventRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessCatedraEventUseCaseImpl implements ProcessCatedraEventUseCase {

    private final ReservationProcessRepository reservationProcessRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ApplyReservationTransitionUseCase applyReservationTransition;
    private final Clock clock;

    private EventResult apply(CatedraEvent event) {
        Optional<ReservationTransition> transition = event.toTransition();
        if (transition.isEmpty()) {
            return EventResult.without(EventOutcome.UNKNOWN_TYPE);
        }
        Optional<ReservationProcess> process = reservationProcessRepository
                .findByReservationProcessId(event.reservationProcessId());
        if (process.isEmpty()) {
            return EventResult.without(EventOutcome.UNKNOWN_PROCESS);
        }
        Long processId = process.get().getId();
        TransitionResult result = applyReservationTransition.applyTransitionOnce(processId, transition.get())
                .orElseThrow(() -> new ReservationProcessNotFoundException(processId));
        return EventResult.processed(result);
    }

    // Un evento que no se puede aplicar (duplicado, tipo o proceso desconocidos, paso que el diagrama no permite) se
    // registra igual: reentregarlo no lo va a arreglar y no debe trabar la lectura de los que siguen. Si la versión
    // choca, la excepción deshace también el registro y quien llama repite el evento entero
    @Override
    @Transactional
    public EventResult processEvent(CatedraEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return EventResult.without(EventOutcome.DUPLICATE);
        }
        EventResult result = apply(event);
        processedEventRepository.save(ProcessedEvent.builder().eventId(event.eventId())
                .eventType(event.eventType()).processedAt(clock.instant()).build());
        return result;
    }
}
