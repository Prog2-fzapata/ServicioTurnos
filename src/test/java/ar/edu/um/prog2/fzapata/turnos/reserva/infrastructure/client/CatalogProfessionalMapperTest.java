package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogProfessionalResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogScheduleResponse;

class CatalogProfessionalMapperTest {

    private final CatalogProfessionalMapper mapper = new CatalogProfessionalMapper();

    @Test
    void mapsEveryFieldOfTheProfessionalAndOfItsSchedules() {
        CatalogProfessionalResponse response = CatalogProfessionalResponse.builder().id(15L).categoryId(2L)
                .categoryName("Pediatria").firstName("Ana").lastName("Gomez").enabled(false)
                .weeklySchedules(List.of(CatalogScheduleResponse.builder().id(7L).dayOfWeek(DayOfWeek.THURSDAY)
                        .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(13, 30)).slotDurationMinutes(45)
                        .enabled(false).build()))
                .build();

        CatalogProfessional professional = mapper.toDomainModel(response);

        assertThat(professional.getId()).isEqualTo(15L);
        assertThat(professional.getCategoryId()).isEqualTo(2L);
        assertThat(professional.getCategoryName()).isEqualTo("Pediatria");
        assertThat(professional.getFirstName()).isEqualTo("Ana");
        assertThat(professional.getLastName()).isEqualTo("Gomez");
        assertThat(professional.isEnabled()).isFalse();
        assertThat(professional.getWeeklySchedules()).singleElement().satisfies(schedule -> {
            assertThat(schedule.getId()).isEqualTo(7L);
            assertThat(schedule.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
            assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(13, 30));
            assertThat(schedule.getSlotDurationMinutes()).isEqualTo(45);
            assertThat(schedule.isEnabled()).isFalse();
        });
    }

    @Test
    void aMissingListOfSchedulesIsAnEmptyList() {
        CatalogProfessionalResponse response = CatalogProfessionalResponse.builder().id(15L).build();

        assertThat(mapper.toDomainModel(response).getWeeklySchedules()).isEmpty();
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toDomainModel(null)).isNull();
    }
}
