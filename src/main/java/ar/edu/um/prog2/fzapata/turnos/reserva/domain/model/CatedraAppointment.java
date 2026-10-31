package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una reserva tal como la informa la cátedra. La cátedra devuelve las de toda la cuenta técnica: filtrar por
// usuario es responsabilidad de turnos. Sin toString a propósito: lleva el nombre y el teléfono del paciente
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatedraAppointment {

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
