package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogSchedule;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogProfessionalResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogScheduleResponse;

@Component
public class CatalogProfessionalMapper {

    private CatalogSchedule toDomainModel(CatalogScheduleResponse response) {
        return CatalogSchedule.builder()
                .id(response.getId())
                .dayOfWeek(response.getDayOfWeek())
                .startTime(response.getStartTime())
                .endTime(response.getEndTime())
                .slotDurationMinutes(response.getSlotDurationMinutes())
                .enabled(response.isEnabled())
                .build();
    }

    public CatalogProfessional toDomainModel(CatalogProfessionalResponse response) {
        if (response == null) return null;
        List<CatalogScheduleResponse> schedules = response.getWeeklySchedules() == null ? List.of()
                : response.getWeeklySchedules();
        return CatalogProfessional.builder()
                .id(response.getId())
                .categoryId(response.getCategoryId())
                .categoryName(response.getCategoryName())
                .firstName(response.getFirstName())
                .lastName(response.getLastName())
                .enabled(response.isEnabled())
                .weeklySchedules(schedules.stream().map(this::toDomainModel).toList())
                .build();
    }
}
