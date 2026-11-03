package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PatientNameRequiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AgendaSlots;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogAccount;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Hold;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Slot;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.StartReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatalogClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// No mantiene ninguna transacción abierta mientras llama a la cátedra: cada cambio de estado es la suya
@Component
@RequiredArgsConstructor
public class StartReservationUseCaseImpl implements StartReservationUseCase {

    // El mínimo que exige la cátedra para el nombre y el apellido del paciente
    private static final int MIN_NAME_LENGTH = 2;
    // Motivos que quedan en el proceso: cortos y estables, sin datos del paciente
    private static final String HOLD_RESULT_UNKNOWN = "HOLD_RESULT_UNKNOWN";
    private static final String CATEDRA_NOT_REACHED = "CATEDRA_NOT_REACHED";
    private static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    private final CatalogClient catalogClient;
    private final CatedraApiClient catedraApiClient;
    private final ReservationProcessRepository reservationProcessRepository;
    private final ApplyReservationTransitionUseCase applyReservationTransition;
    private final ProfessionalGuard professionalGuard;
    private final HoldRetryPolicy holdRetryPolicy;
    private final Clock clock;

    private record Patient(String firstName, String lastName) {
    }

    // Un intento termina bien (reserved) o, si el hold se perdió, pide otro y guarda lo que se lanzaría si no quedan
    private record Attempt(ReservationProcess reserved, RuntimeException failureIfExhausted) {

        static Attempt success(ReservationProcess reserved) {
            return new Attempt(reserved, null);
        }

        static Attempt retry(RuntimeException failureIfExhausted) {
            return new Attempt(null, failureIfExhausted);
        }
    }

    private Slot requireSlot(CatalogProfessional professional, ReservationRequest request) {
        LocalDateTime start = LocalDateTime.of(request.date(), request.startTime());
        Slot slot = AgendaSlots.generate(professional, request.date(), request.date()).get(start);
        if (slot == null) {
            throw new InvalidSlotException("El horario no está en la agenda del profesional " + professional.getId());
        }
        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new InvalidSlotException("El horario ya pasó");
        }
        return slot;
    }

    private Patient requirePatient(CatalogAccount account) {
        String firstName = account.getFirstName() == null ? "" : account.getFirstName().strip();
        String lastName = account.getLastName() == null ? "" : account.getLastName().strip();
        if (firstName.length() < MIN_NAME_LENGTH || lastName.length() < MIN_NAME_LENGTH) {
            throw new PatientNameRequiredException();
        }
        return new Patient(firstName, lastName);
    }

    private ReservationProcess newProcess(ReservationRequest request, CatalogProfessional professional, Slot slot,
            Patient patient) {
        Instant now = clock.instant();
        return ReservationProcess.builder().userId(request.userId()).professionalId(professional.getId())
                .professionalFirstName(professional.getFirstName()).professionalLastName(professional.getLastName())
                .appointmentDate(slot.getDate()).startTime(slot.getStartTime()).endTime(slot.getEndTime())
                .status(ReservationStatus.HOLD_REQUESTED).patientFirstName(patient.firstName())
                .patientLastName(patient.lastName()).createdAt(now).updatedAt(now).build();
    }

    // Un paso que acá debe aplicarse siempre: si el diagrama lo rechaza, el proceso no está donde este caso de uso
    // cree. Que ya estuviera en el estado pedido es posible: la cátedra publica el pedido de teléfono justo después de
    // confirmar y su evento puede procesarse antes de que este paso llegue
    private ReservationProcess transition(Long processId, ReservationTransition transition) {
        TransitionResult result = applyReservationTransition.applyTransition(processId, transition)
                .orElseThrow(() -> new ReservationProcessNotFoundException(processId));
        if (result.outcome() == TransitionOutcome.REJECTED) {
            throw new IllegalStateException("El proceso " + processId + " no pudo pasar de " + result.from() + " a "
                    + transition.target());
        }
        return result.process();
    }

    // Cierra el intento. Un fallo al registrar el cierre no debe tapar el error original
    private void close(Long processId, ReservationStatus finalStatus, String reason, RuntimeException cause) {
        try {
            applyReservationTransition.applyTransition(processId,
                    ReservationTransition.builder().target(finalStatus).failureReason(reason).build());
        } catch (RuntimeException e) {
            cause.addSuppressed(e);
        }
    }

    private RuntimeException toException(CatedraRejection rejection, ReservationRequest request) {
        return switch (rejection) {
            case SLOT_ALREADY_HELD, SLOT_ALREADY_RESERVED -> new SlotUnavailableException();
            case PROFESSIONAL_DISABLED -> new ProfessionalDisabledException(
                    "La cátedra informa deshabilitado al profesional " + request.professionalId());
            case PROFESSIONAL_NOT_FOUND -> new ProfessionalNotFoundException(request.professionalId());
            case INVALID_SLOT -> new InvalidSlotException(
                    "La cátedra no reconoce el horario en la agenda del profesional " + request.professionalId());
            default -> new CatedraResponseException("La cátedra rechazó el hold con " + rejection, null);
        };
    }

    // D80: sin forma de recuperar el hold perdido, se mira qué figura ocupado. Si el horario aparece ocupado (HELD o
    // CONFIRMED) no se asume que el hold es nuestro; si está libre se puede pedir otro
    private Attempt afterLostHold(ReservationRequest request, CatedraUnavailableException lost) {
        boolean occupied = catedraApiClient.findOccupancies(request.professionalId(), request.date(), request.date())
                .stream().anyMatch(occupancy -> occupancy.getStartTime().equals(request.startTime()));
        if (occupied) {
            throw new SlotUnavailableException();
        }
        return Attempt.retry(lost);
    }

    // El hold ya no existe (venció o desapareció): nada quedó bloqueado, así que se puede pedir otro
    private Attempt afterConfirmRejection(Long processId, CatedraRejectedException rejected) {
        CatedraRejection rejection = rejected.getRejection();
        if (rejection == CatedraRejection.HOLD_EXPIRED || rejection == CatedraRejection.HOLD_NOT_FOUND) {
            RuntimeException expired = new ReservationExpiredException();
            close(processId, rejection == CatedraRejection.HOLD_EXPIRED ? ReservationStatus.EXPIRED
                    : ReservationStatus.FAILED, rejection.name(), expired);
            return Attempt.retry(expired);
        }
        RuntimeException unexpected = new CatedraResponseException(
                "La cátedra rechazó la confirmación con " + rejection, rejected);
        close(processId, ReservationStatus.FAILED, UNEXPECTED_ERROR, unexpected);
        throw unexpected;
    }

    private Attempt confirm(Long processId, Hold hold, ReservationRequest request, Patient patient) {
        transition(processId, ReservationTransition.builder().target(ReservationStatus.HELD)
                .holdId(hold.getHoldId()).reservationProcessId(hold.getReservationProcessId())
                .expiresAt(hold.getExpiresAt()).build());
        Instant expiresAt;
        try {
            expiresAt = catedraApiClient.confirmHold(hold.getHoldId(), request.userId(), patient.firstName(),
                    patient.lastName()).getExpiresAt();
        } catch (CatedraRejectedException e) {
            if (e.getRejection() != CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED) {
                return afterConfirmRejection(processId, e);
            }
            // Una repetición de la confirmación que ya había salido bien: es la misma reserva, con el mismo vencimiento
            expiresAt = hold.getExpiresAt();
        } catch (CatedraUnavailableException e) {
            // El hold existe y la cátedra pudo haberlo confirmado: el proceso queda en HELD, que no es final, para que
            // el vencimiento o la reconciliación lo resuelvan
            throw e;
        } catch (RuntimeException e) {
            close(processId, ReservationStatus.FAILED, UNEXPECTED_ERROR, e);
            throw e;
        }
        return Attempt.success(transition(processId,
                ReservationTransition.builder().target(ReservationStatus.WAITING_PHONE).expiresAt(expiresAt).build()));
    }

    // Un intento: el hold y su confirmación sobre un proceso nuevo
    private Attempt attempt(ReservationProcess created, ReservationRequest request, Patient patient) {
        Long processId = created.getId();
        Hold hold;
        try {
            hold = catedraApiClient.createHold(request.professionalId(), request.date(), request.startTime(),
                    request.userId());
        } catch (CatedraRejectedException e) {
            RuntimeException failure = toException(e.getRejection(), request);
            close(processId, ReservationStatus.FAILED, e.getRejection().name(), failure);
            throw failure;
        } catch (CatedraNotReachedException e) {
            // Nada llegó a la cátedra: no hay hold perdido que investigar
            close(processId, ReservationStatus.FAILED, CATEDRA_NOT_REACHED, e);
            throw e;
        } catch (CatedraUnavailableException e) {
            close(processId, ReservationStatus.FAILED, HOLD_RESULT_UNKNOWN, e);
            return afterLostHold(request, e);
        } catch (RuntimeException e) {
            close(processId, ReservationStatus.FAILED, UNEXPECTED_ERROR, e);
            throw e;
        }
        return confirm(processId, hold, request, patient);
    }

    @Override
    public Optional<ReservationProcess> startReservation(ReservationRequest request) {
        // El mapper del controller devuelve null ante un cuerpo null: se exige acá, antes de usar la solicitud
        Objects.requireNonNull(request, "La solicitud de reserva es obligatoria");
        Optional<CatalogProfessional> found = catalogClient.findProfessional(request.professionalId());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        CatalogProfessional professional = found.get();
        professionalGuard.requireEnabled(professional);
        Slot slot = requireSlot(professional, request);
        // Antes de crear nada: si falta el nombre no queda ningún proceso ni nada bloqueado en la cátedra
        Patient patient = requirePatient(catalogClient.findAccount());
        for (int number = 1;; number++) {
            ReservationProcess created = reservationProcessRepository
                    .save(newProcess(request, professional, slot, patient));
            Attempt attempt = attempt(created, request, patient);
            if (attempt.reserved() != null) {
                return Optional.of(attempt.reserved());
            }
            if (number >= holdRetryPolicy.maxAttempts()) {
                throw attempt.failureIfExhausted();
            }
        }
    }
}
