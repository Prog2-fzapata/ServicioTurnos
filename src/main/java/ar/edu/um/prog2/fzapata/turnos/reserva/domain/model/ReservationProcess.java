package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El proceso local de una reserva. reservationProcessId y holdId son de la cátedra y quedan en null hasta que el hold
// responde. Las horas son locales de atención, sin zona horaria. version es el bloqueo optimista: null hasta guardarlo
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationProcess {

    private Long id;
    private String reservationProcessId;
    private String holdId;
    private String userId;
    private Long professionalId;
    private String professionalFirstName;
    private String professionalLastName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ReservationStatus status;
    private Instant expiresAt;
    private String patientFirstName;
    private String patientLastName;
    private String patientPhone;
    private String requestEventId;
    private Long reservationId;
    private String failureReason;
    private String cancellationReason;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
