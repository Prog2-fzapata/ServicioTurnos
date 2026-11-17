package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.ReservationProcessEntity;

@Component
public class ReservationProcessMapper {

    public ReservationProcessEntity toEntity(ReservationProcess process) {
        if (process == null) return null;
        return ReservationProcessEntity.builder()
                .id(process.getId())
                .reservationProcessId(process.getReservationProcessId())
                .holdId(process.getHoldId())
                .userId(process.getUserId())
                .professionalId(process.getProfessionalId())
                .professionalFirstName(process.getProfessionalFirstName())
                .professionalLastName(process.getProfessionalLastName())
                .appointmentDate(process.getAppointmentDate())
                .startTime(process.getStartTime())
                .endTime(process.getEndTime())
                .status(process.getStatus())
                .expiresAt(process.getExpiresAt())
                .patientFirstName(process.getPatientFirstName())
                .patientLastName(process.getPatientLastName())
                .patientPhone(process.getPatientPhone())
                .requestEventId(process.getRequestEventId())
                .reservationId(process.getReservationId())
                .failureReason(process.getFailureReason())
                .cancellationReason(process.getCancellationReason())
                .cancelledAt(process.getCancelledAt())
                .createdAt(process.getCreatedAt())
                .updatedAt(process.getUpdatedAt())
                .version(process.getVersion())
                .build();
    }

    public ReservationProcess toDomainModel(ReservationProcessEntity entity) {
        if (entity == null) return null;
        return ReservationProcess.builder()
                .id(entity.getId())
                .reservationProcessId(entity.getReservationProcessId())
                .holdId(entity.getHoldId())
                .userId(entity.getUserId())
                .professionalId(entity.getProfessionalId())
                .professionalFirstName(entity.getProfessionalFirstName())
                .professionalLastName(entity.getProfessionalLastName())
                .appointmentDate(entity.getAppointmentDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(entity.getStatus())
                .expiresAt(entity.getExpiresAt())
                .patientFirstName(entity.getPatientFirstName())
                .patientLastName(entity.getPatientLastName())
                .patientPhone(entity.getPatientPhone())
                .requestEventId(entity.getRequestEventId())
                .reservationId(entity.getReservationId())
                .failureReason(entity.getFailureReason())
                .cancellationReason(entity.getCancellationReason())
                .cancelledAt(entity.getCancelledAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
