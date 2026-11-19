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
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
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
    // También lo usa la recuperación al arrancar para los pedidos de hold que quedaron sin resultado
    static final String HOLD_RESULT_UNKNOWN = "HOLD_RESULT_UNKNOWN";
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

    // Un intento termina bien (Reserved) o, si el hold se perdió, pide otro y guarda lo que se lanzaría si no quedan
    // (Lost)
    private sealed interface Attempt {

        record Reserved(ReservationProcess process) implements Attempt {
        }

        record Lost(RuntimeException failureIfExhausted) implements Attempt {
        }
    }

    @Override
    public Optional<ReservationProcess> startReservation(ReservationRequest request) {
        // Por Http no llega null, por si otro adaptador lo llama y no lo verifica
        Objects.requireNonNull(request, "La solicitud de reserva es obligatoria");
        Optional<CatalogProfessional> found = catalogClient.findProfessional(request.professionalId());
        if (found.isEmpty()) {
            return Optional.empty(); // ProfessionalNotFoundException en Service
        }
        // Extraemos el professional del optional
        CatalogProfessional professional = found.get();
        professionalGuard.requireEnabled(professional); // Verifica que el profesional y su categoría enabled

        // Consulta la agenda del día de la request y devuelve slot si está disponible
        Slot slot = getSlot(professional, request);
        // Antes de crear nada: si falta el nombre, PatientNameRequiredException, no bloquea nada
        Patient patient = toPatient(catalogClient.findAccount());
        // Intentamos hasta maxAttempts
        for (int number = 1;; number++) {
            // Armamos el ReservationProcess y lo guardamos con el repository
            ReservationProcess process = reservationProcessRepository
                    .save(newProcess(request, professional, slot, patient));
            // Intenta crear el hold
            switch (attempt(process, request, patient)) {
                case Attempt.Reserved reserved -> {
                    return Optional.of(reserved.process());
                }
                case Attempt.Lost lost -> {
                    if (number >= holdRetryPolicy.maxAttempts()) {
                        throw lost.failureIfExhausted();
                    }
                }
            }
        }
    }

    // Busca en la agenda del profesional el slot que empieza a la hora seleccionada; si no hay, InvalidSlotException
    private Slot getSlot(CatalogProfessional professional, ReservationRequest request) {
        // Convertimos a LocalDateTime la fecha y hora de la request
        LocalDateTime start = LocalDateTime.of(request.date(), request.startTime());

        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new InvalidSlotException("El horario ya pasó");
        }

        return professional.slotAt(start).orElseThrow(() -> new InvalidSlotException(
                "El horario no está en la agenda del profesional " + professional.getId()));
    }

    // Verifica que el usuario tenga name y lastName cargados y retorna Patient, si no, PatientNameRequiredException
    // Son campos opcionales en el registro pero necesarios para reservar
    private Patient toPatient(CatalogAccount account) {
        String firstName = account.getFirstName() == null ? "" : account.getFirstName().strip();
        String lastName = account.getLastName() == null ? "" : account.getLastName().strip();
        if (firstName.length() < MIN_NAME_LENGTH || lastName.length() < MIN_NAME_LENGTH) {
            throw new PatientNameRequiredException();
        }
        return new Patient(firstName, lastName);
    }

    // Retorna un nuevo ReservationProcess con builder.
    private ReservationProcess newProcess(ReservationRequest request, CatalogProfessional professional, Slot slot,
            Patient patient) {
        Instant now = clock.instant();
        return ReservationProcess.builder()
                .userId(request.userId())
                .professionalId(professional.getId())
                .professionalFirstName(professional.getFirstName())
                .professionalLastName(professional.getLastName())
                .appointmentDate(slot.getDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(ReservationStatus.HOLD_REQUESTED)
                .patientFirstName(patient.firstName())
                .patientLastName(patient.lastName())
                .createdAt(now)
                .updatedAt(now).build();
    }

    // Un intento: el hold y su confirmación sobre un proceso nuevo
    private Attempt attempt(ReservationProcess process, ReservationRequest request, Patient patient) {
        Long processId = process.getId();
        Hold hold;
        try {
            hold = catedraApiClient.createHold(request.professionalId(), request.date(), request.startTime(),
                    request.userId());
        // Atrapamos las excepciones y hacemos el cierre del intento del ReservationProcess registrando el error
        } catch (CatedraRejectedException e) {
            RuntimeException failure = HoldRejectionTranslator.translate(e.getRejection(), request.professionalId());
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

    // Cierra el intento. Aplicando la transición
    private void close(Long processId, ReservationStatus finalStatus, String reason, RuntimeException cause) {
        try {
            applyReservationTransition.applyTransition(processId,
                    ReservationTransition.builder().target(finalStatus).failureReason(reason).build());
        } catch (RuntimeException e) {
            cause.addSuppressed(e); // Un fallo al registrar el cierre no debe tapar el error original
        }
    }

    // D80: sin forma de recuperar el hold perdido, se mira qué figura ocupado. Si el horario aparece ocupado (HELD o
    // CONFIRMED) no se asume que el hold es nuestro; si está libre se puede pedir otro
    private Attempt afterLostHold(ReservationRequest request, CatedraUnavailableException lost) {
        boolean occupied = catedraApiClient.findOccupancies(request.professionalId(), request.date(), request.date())
                .stream().anyMatch(occupancy -> occupancy.getStartTime().equals(request.startTime()));
        if (occupied) {
            throw new SlotUnavailableException();
        }
        return new Attempt.Lost(lost);
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
        return new Attempt.Reserved(transition(processId,
                ReservationTransition.builder().target(ReservationStatus.WAITING_PHONE).expiresAt(expiresAt).build()));
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

    // El hold ya no existe (venció o desapareció): nada quedó bloqueado, así que se puede pedir otro
    private Attempt afterConfirmRejection(Long processId, CatedraRejectedException rejected) {
        CatedraRejection rejection = rejected.getRejection();
        if (rejection == CatedraRejection.HOLD_EXPIRED || rejection == CatedraRejection.HOLD_NOT_FOUND) {
            RuntimeException expired = new ReservationExpiredException();
            close(processId, rejection == CatedraRejection.HOLD_EXPIRED ? ReservationStatus.EXPIRED
                    : ReservationStatus.FAILED, rejection.name(), expired);
            return new Attempt.Lost(expired);
        }
        RuntimeException unexpected = new CatedraResponseException(
                "La cátedra rechazó la confirmación con " + rejection, rejected);
        close(processId, ReservationStatus.FAILED, UNEXPECTED_ERROR, unexpected);
        throw unexpected;
    }
}
