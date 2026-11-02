package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.ReservationResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.StartReservationRequest;

@Component
public class ReservationDtoMapper {

    // El usuario llega aparte, leído del JWT por el controller
    public ReservationRequest toRequest(StartReservationRequest request, String userId) {
        if (request == null) return null;
        return new ReservationRequest(userId, request.getProfessionalId(), request.getDate(), request.getStartTime());
    }

    public ReservationResponse toResponse(ReservationProcess process) {
        if (process == null) return null;
        return ReservationResponse.builder()
                .id(process.getId())
                .reservationProcessId(process.getReservationProcessId())
                .status(process.getStatus())
                .professionalId(process.getProfessionalId())
                .professionalFirstName(process.getProfessionalFirstName())
                .professionalLastName(process.getProfessionalLastName())
                .appointmentDate(process.getAppointmentDate())
                .startTime(process.getStartTime())
                .endTime(process.getEndTime())
                // El vencimiento solo tiene sentido mientras el proceso sigue abierto
                .expiresAt(process.getStatus().isFinal() ? null : process.getExpiresAt())
                .patientPhone(process.getPatientPhone())
                .failureReason(process.getFailureReason())
                .cancellationReason(process.getCancellationReason())
                .createdAt(process.getCreatedAt())
                .updatedAt(process.getUpdatedAt())
                .build();
    }
}
