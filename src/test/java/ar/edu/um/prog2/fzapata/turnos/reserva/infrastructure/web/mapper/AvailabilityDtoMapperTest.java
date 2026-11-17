package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Slot;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.AvailabilityResponse;

class AvailabilityDtoMapperTest {

    private final AvailabilityDtoMapper mapper = new AvailabilityDtoMapper();

    @Test
    void mapsEveryFieldAndKeepsTheOrderOfTheSlots() {
        Availability availability = Availability.builder().professionalId(15L).from(LocalDate.of(2026, 10, 12))
                .to(LocalDate.of(2026, 10, 25))
                .slots(List.of(
                        Slot.builder().date(LocalDate.of(2026, 10, 12)).startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(9, 30)).build(),
                        Slot.builder().date(LocalDate.of(2026, 10, 13)).startTime(LocalTime.of(10, 0))
                                .endTime(LocalTime.of(10, 30)).build()))
                .build();

        AvailabilityResponse response = mapper.toResponse(availability);

        assertThat(response.getProfessionalId()).isEqualTo(15L);
        assertThat(response.getFrom()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(response.getTo()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(response.getSlots()).hasSize(2);
        assertThat(response.getSlots().get(0).getDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(response.getSlots().get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.getSlots().get(0).getEndTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.getSlots().get(1).getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
