package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Cancellation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Hold;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldConfirmation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.AppointmentResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CancelResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ConfirmResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.HoldResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.OccupancyListResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.OccupancyResponse;

@Component
public class CatedraMapper {

    private Occupancy toDomainModel(OccupancyResponse response) {
        return Occupancy.builder()
                .date(response.getDate())
                .startTime(response.getStartTime())
                .status(response.getStatus())
                .expiresAt(response.getExpiresAt())
                .build();
    }

    // Una lista ausente se toma como vacía: "sin ocupaciones" es una respuesta válida
    public List<Occupancy> toDomainModel(OccupancyListResponse response) {
        if (response == null || response.getOccupancies() == null) return List.of();
        return response.getOccupancies().stream().map(this::toDomainModel).toList();
    }

    public Hold toDomainModel(HoldResponse response) {
        if (response == null) return null;
        return Hold.builder()
                .holdId(response.getHoldId())
                .reservationProcessId(response.getReservationProcessId())
                .expiresAt(response.getExpiresAt())
                .build();
    }

    public HoldConfirmation toDomainModel(ConfirmResponse response) {
        if (response == null) return null;
        return HoldConfirmation.builder()
                .reservationProcessId(response.getReservationProcessId())
                .expiresAt(response.getExpiresAt())
                .build();
    }

    public CatedraAppointment toDomainModel(AppointmentResponse response) {
        if (response == null) return null;
        return CatedraAppointment.builder()
                .reservationId(response.getReservationId())
                .reservationProcessId(response.getReservationProcessId())
                .externalPatientId(response.getExternalPatientId())
                .appointmentDate(response.getAppointmentDate())
                .startTime(response.getStartTime())
                .endTime(response.getEndTime())
                .professionalId(response.getProfessionalId())
                .professionalFirstName(response.getProfessionalFirstName())
                .professionalLastName(response.getProfessionalLastName())
                .patientFirstName(response.getPatientFirstName())
                .patientLastName(response.getPatientLastName())
                .patientPhone(response.getPatientPhone())
                .status(response.getStatus())
                .createdAt(response.getCreatedAt())
                .confirmedAt(response.getConfirmedAt())
                .cancelledAt(response.getCancelledAt())
                .cancellationReason(response.getCancellationReason())
                .build();
    }

    public Cancellation toDomainModel(CancelResponse response) {
        if (response == null) return null;
        return Cancellation.builder()
                .reservationId(response.getReservationId())
                .reservationProcessId(response.getReservationProcessId())
                .cancelledAt(response.getCancelledAt())
                .build();
    }
}
