package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Lleva el nombre y el teléfono del paciente: sin toString a propósito para que no llegue a un log
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppointmentResponse {
    private Long reservationId;
    private String reservationProcessId;
    private String externalPatientId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long professionalId;
    private String professionalFirstName;
    private String professionalLastName;
    private String patientFirstName;
    private String patientLastName;
    private String patientPhone;
    private AppointmentStatus status;
    private Instant createdAt;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private String cancellationReason;
}
