package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidReservationStateException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhoneWindowExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneMessageSettings;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneNumber;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmission;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmissionRules;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.SubmitPhoneUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhoneMessageSerializer;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SubmitPhoneUseCaseImpl implements SubmitPhoneUseCase {

    private final ReservationProcessRepository reservationProcessRepository;
    private final OutboxRepository outboxRepository;
    private final ApplyReservationTransitionUseCase applyReservationTransition;
    private final PhoneMessageSerializer phoneMessageSerializer;
    private final PhoneMessageSettings phoneMessageSettings;
    private final Clock clock;

    private ReservationProcess requireSubmittable(ReservationProcess process, Instant now) {
        if (!PhoneSubmissionRules.canSubmit(process)) {
            throw new InvalidReservationStateException(process.getId(), process.getStatus());
        }
        if (process.getExpiresAt() != null && !now.isBefore(process.getExpiresAt())) {
            throw new PhoneWindowExpiredException(process.getId());
        }
        return process;
    }

    private OutboxEvent newOutboxEvent(ReservationProcess queued, Instant now) {
        String eventId = "phone-submitted-" + UUID.randomUUID();
        String payload = phoneMessageSerializer.toPayload(new PhoneSubmission(eventId,
                queued.getReservationProcessId(), queued.getHoldId(), queued.getRequestEventId(),
                queued.getPatientPhone(), now));
        return OutboxEvent.builder().eventId(eventId).reservationProcessId(queued.getReservationProcessId())
                .topic(phoneMessageSettings.topic()).messageKey(queued.getReservationProcessId()).payload(payload)
                .status(OutboxStatus.PENDING).createdAt(now).build();
    }

    // El estado y el mensaje se guardan juntos: o quedan los dos o ninguno. Un solo intento por transacción, igual que
    // al procesar un evento (D84): el reintento por choque de versión lo hace quien llama
    @Override
    @Transactional
    public Optional<ReservationProcess> submitPhone(Long id, String userId, String phoneNumber) {
        Optional<ReservationProcess> found = reservationProcessRepository.findByIdAndUserId(id, userId);
        if (found.isEmpty()) {
            return found;
        }
        Instant now = clock.instant();
        requireSubmittable(found.get(), now);
        TransitionResult result = applyReservationTransition.applyTransitionOnce(id, ReservationTransition.builder()
                .target(ReservationStatus.PHONE_QUEUED).patientPhone(PhoneNumber.normalize(phoneNumber)).build())
                .orElseThrow(() -> new ReservationProcessNotFoundException(id));
        // Otro cambio se adelantó (por ejemplo, el vencimiento): el paso ya no corresponde
        if (result.outcome() != TransitionOutcome.APPLIED) {
            throw new InvalidReservationStateException(id, result.from());
        }
        outboxRepository.save(newOutboxEvent(result.process(), now));
        return Optional.of(result.process());
    }
}
