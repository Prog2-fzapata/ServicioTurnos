package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.MarkPhoneSentUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MarkPhoneSentUseCaseImpl implements MarkPhoneSentUseCase {

    private final OutboxRepository outboxRepository;
    private final ReservationProcessRepository reservationProcessRepository;
    private final ApplyReservationTransitionUseCase applyReservationTransition;
    private final Clock clock;

    // "Enviado" significa que el broker confirmó el mensaje, no que la cátedra lo aceptó (D12b). Si la cátedra ya
    // respondió, el paso a PHONE_SUBMITTED lo rechaza el diagrama y el proceso no retrocede
    @Override
    @Transactional
    public Optional<TransitionResult> markSent(OutboxEvent event) {
        OutboxEvent stored = outboxRepository.findById(event.getId())
                .orElseThrow(() -> new IllegalStateException("No existe el mensaje " + event.getId()));
        if (stored.getStatus() == OutboxStatus.SENT) {
            return Optional.empty();
        }
        ReservationProcess process = reservationProcessRepository
                .findByReservationProcessId(stored.getReservationProcessId())
                .orElseThrow(() -> new IllegalStateException(
                        "El mensaje " + stored.getEventId() + " no tiene proceso de reserva"));
        TransitionResult result = applyReservationTransition
                .applyTransitionOnce(process.getId(), ReservationTransition.to(ReservationStatus.PHONE_SUBMITTED))
                .orElseThrow(() -> new ReservationProcessNotFoundException(process.getId()));
        stored.setStatus(OutboxStatus.SENT);
        stored.setSentAt(clock.instant());
        outboxRepository.save(stored);
        return Optional.of(result);
    }
}
