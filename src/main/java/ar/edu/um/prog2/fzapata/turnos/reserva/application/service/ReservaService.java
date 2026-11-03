package ar.edu.um.prog2.fzapata.turnos.reserva.application.service;

import org.springframework.stereotype.Service;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.CancelReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ListReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.StartReservationUseCase;
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
}
