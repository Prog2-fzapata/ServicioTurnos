package ar.edu.um.prog2.fzapata.turnos.reserva.application.service;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.CancelReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ExpireOverdueReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ListReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ProcessCatedraEventUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.PublishPendingPhonesUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.StartReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.SubmitPhoneUseCase;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final GetAvailabilityUseCase getAvailabilityUseCase;
    private final ApplyReservationTransitionUseCase applyReservationTransitionUseCase;
    private final StartReservationUseCase startReservationUseCase;
    private final ListReservationsUseCase listReservationsUseCase;
    private final GetReservationUseCase getReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final ProcessCatedraEventUseCase processCatedraEventUseCase;
    private final SubmitPhoneUseCase submitPhoneUseCase;
    private final PublishPendingPhonesUseCase publishPendingPhonesUseCase;
    private final ExpireOverdueReservationsUseCase expireOverdueReservationsUseCase;
    private final ConflictRetryPolicy conflictRetryPolicy;

    public Availability getAvailability(AvailabilityQuery query) {
        return getAvailabilityUseCase.getAvailability(query)
                .orElseThrow(() -> new ProfessionalNotFoundException(query.professionalId()));
    }

    public TransitionResult applyTransition(Long processId, ReservationTransition transition) {
        return applyReservationTransitionUseCase.applyTransition(processId, transition)
                .orElseThrow(() -> new ReservationProcessNotFoundException(processId));
    }

    public ReservationProcess startReservation(ReservationRequest request) {
        return startReservationUseCase.startReservation(request)
                .orElseThrow(() -> new ProfessionalNotFoundException(request.professionalId()));
    }

    public PagedResult<ReservationProcess> listReservations(String userId, ReservationStatus status, PageQuery page) {
        return listReservationsUseCase.listReservations(userId, status, page);
    }

    public ReservationProcess getReservation(Long id, String userId) {
        return getReservationUseCase.getReservation(id, userId)
                .orElseThrow(() -> new ReservationProcessNotFoundException(id));
    }

    public ReservationProcess cancelReservation(Long id, String userId, String reason) {
        return cancelReservationUseCase.cancelReservation(id, userId, reason)
                .orElseThrow(() -> new ReservationProcessNotFoundException(id));
    }

    // Cada intento es una transacción nueva del caso de uso: si la versión del proceso chocó, se deshizo todo y se
    // repite entero (D84). Es seguro porque lo que se deshizo no dejó nada escrito
    private <T> T retryingOnConflict(Supplier<T> attempt) {
        for (int attemptNumber = 1;; attemptNumber++) {
            try {
                return attempt.get();
            } catch (ReservationConflictException e) {
                if (attemptNumber >= conflictRetryPolicy.maxAttempts()) {
                    throw e;
                }
            }
        }
    }

    // Un evento ya registrado vuelve como DUPLICATE, así que repetirlo es seguro
    public EventResult processEvent(CatedraEvent event) {
        return retryingOnConflict(() -> processCatedraEventUseCase.processEvent(event));
    }

    // Una segunda petición que llega después del choque ve la reserva ya en PHONE_QUEUED y responde 409
    public ReservationProcess submitPhone(Long id, String userId, String phoneNumber) {
        return retryingOnConflict(() -> submitPhoneUseCase.submitPhone(id, userId, phoneNumber)
                .orElseThrow(() -> new ReservationProcessNotFoundException(id)));
    }

    public List<PhoneDelivery> publishPendingPhones() {
        return publishPendingPhonesUseCase.publishPending();
    }

    public List<ExpirationResult> expireOverdueReservations() {
        return expireOverdueReservationsUseCase.expireOverdue();
    }
}
