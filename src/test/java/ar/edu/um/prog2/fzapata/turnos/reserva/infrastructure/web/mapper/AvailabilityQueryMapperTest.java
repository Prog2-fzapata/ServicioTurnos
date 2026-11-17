package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidDateRangeException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.shared.config.AvailabilityProperties;

class AvailabilityQueryMapperTest {

    private final AvailabilityQueryMapper mapper = new AvailabilityQueryMapper(
            new AvailabilityProperties(ZoneId.of("UTC"), 31));

    @Test
    void buildsTheQueryFromAValidRange() {
        AvailabilityQuery query = mapper.toQuery(15L, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 25));

        assertThat(query.professionalId()).isEqualTo(15L);
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void aSingleDayIsAValidRange() {
        LocalDate day = LocalDate.of(2026, 10, 12);

        assertThat(mapper.toQuery(15L, day, day).to()).isEqualTo(day);
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> mapper.toQuery(15L, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 12)))
                .isInstanceOf(InvalidDateRangeException.class).hasMessageContaining("anterior");
    }

    // El máximo cuenta ambos extremos: del 1 al 31 de octubre son 31 días
    @Test
    void theMaximumNumberOfDaysIsAllowedAndOneMoreIsNot() {
        assertThat(mapper.toQuery(15L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).isNotNull();

        assertThatThrownBy(() -> mapper.toQuery(15L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(InvalidDateRangeException.class).hasMessageContaining("31");
    }

    @Test
    void theMaximumComesFromTheProperties() {
        AvailabilityQueryMapper shortRange = new AvailabilityQueryMapper(
                new AvailabilityProperties(ZoneId.of("UTC"), 7));

        assertThat(shortRange.toQuery(15L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))).isNotNull();
        assertThatThrownBy(() -> shortRange.toQuery(15L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8)))
                .isInstanceOf(InvalidDateRangeException.class).hasMessageContaining("7");
    }
}
