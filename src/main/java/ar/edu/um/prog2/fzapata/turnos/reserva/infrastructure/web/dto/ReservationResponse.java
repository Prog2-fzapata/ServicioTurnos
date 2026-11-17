package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El objeto Reservation del contrato. Nunca lleva el usuario dueño ni los ids de la cátedra
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    private Long id;
    private String reservationProcessId;
    private ReservationStatus status;
    private Long professionalId;
    private String professionalFirstName;
    private String professionalLastName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Instant expiresAt;
    private String patientPhone;
    private String failureReason;
    private String cancellationReason;
    // true: la reserva espera el teléfono y la cátedra ya lo pidió, así que la app puede habilitar el formulario. La app
    // compara expiresAt con la hora para saber si todavía hay tiempo
    private boolean canSubmitPhone;
    private Instant createdAt;
    private Instant updatedAt;
}
