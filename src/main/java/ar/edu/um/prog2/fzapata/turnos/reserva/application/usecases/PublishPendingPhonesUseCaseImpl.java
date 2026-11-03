package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RelayBatchPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.MarkPhoneSentUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.PublishPendingPhonesUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhonePublisher;
import lombok.RequiredArgsConstructor;

// Sin transacción abierta: publicar en Kafka no participa de la de la base, y cada mensaje se marca en la suya
@Component
@RequiredArgsConstructor
public class PublishPendingPhonesUseCaseImpl implements PublishPendingPhonesUseCase {

    private final OutboxRepository outboxRepository;
    private final PhonePublisher phonePublisher;
    private final MarkPhoneSentUseCase markPhoneSentUseCase;
    private final RelayBatchPolicy relayBatchPolicy;
    private final ConflictRetryPolicy conflictRetryPolicy;

    // Si el proceso cambió mientras tanto se repite solo la actualización: el mensaje ya está publicado
    private Optional<TransitionResult> markSentRetrying(OutboxEvent event) {
        for (int attemptNumber = 1;; attemptNumber++) {
            try {
                return markPhoneSentUseCase.markSent(event);
            } catch (ReservationConflictException e) {
                if (attemptNumber >= conflictRetryPolicy.maxAttempts()) {
                    throw e;
                }
            }
        }
    }

    private PhoneDelivery deliver(OutboxEvent event) {
        try {
            phonePublisher.publish(event.getTopic(), event.getMessageKey(), event.getPayload());
        } catch (PhonePublishException e) {
            event.setAttempts(event.getAttempts() + 1);
            outboxRepository.save(event);
            return new PhoneDelivery(event, false, Optional.empty());
        }
        return new PhoneDelivery(event, true, markSentRetrying(event));
    }

    @Override
    public List<PhoneDelivery> publishPending() {
        List<PhoneDelivery> deliveries = new ArrayList<>();
        for (OutboxEvent event : outboxRepository.findPending(relayBatchPolicy.size())) {
            deliveries.add(deliver(event));
        }
        return deliveries;
    }
}
