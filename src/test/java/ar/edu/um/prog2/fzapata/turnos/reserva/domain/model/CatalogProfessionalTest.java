package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogProfessionalTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    private static CatalogSchedule schedule(DayOfWeek day, String start, String end, int minutes) {
        return CatalogSchedule.builder().dayOfWeek(day).startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                .slotDurationMinutes(minutes).enabled(true).build();
    }

    private static CatalogProfessional professional(CatalogSchedule... schedules) {
        return CatalogProfessional.builder().id(7L).weeklySchedules(List.of(schedules)).build();
    }

    private static LocalDateTime at(LocalDate date, String time) {
        return LocalDateTime.of(date, LocalTime.parse(time));
    }

    @Test
    void slotsBetweenIncludesBothEndsOfTheRange() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "09:30", 30));

        // Tres lunes: el primero y el último son los bordes del rango
        assertThat(professional.slotsBetween(MONDAY, MONDAY.plusDays(14)).keySet())
                .containsExactly(at(MONDAY, "09:00"), at(MONDAY.plusDays(7), "09:00"),
                        at(MONDAY.plusDays(14), "09:00"));
    }

    @Test
    void slotsBetweenOnlyHasTheDaysOfTheWeekOfEachSchedule() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "09:30", 30),
                schedule(DayOfWeek.WEDNESDAY, "15:00", "15:30", 30));

        assertThat(professional.slotsBetween(MONDAY, MONDAY.plusDays(6)).keySet())
                .containsExactly(at(MONDAY, "09:00"), at(MONDAY.plusDays(2), "15:00"));
    }

    @Test
    void slotsBetweenComeOrderedWhateverTheOrderOfTheSchedules() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "15:00", "15:30", 30),
                schedule(DayOfWeek.MONDAY, "09:00", "09:30", 30));

        assertThat(professional.slotsBetween(MONDAY, MONDAY).keySet())
                .containsExactly(at(MONDAY, "09:00"), at(MONDAY, "15:00"));
    }

    @Test
    void slotsBetweenKeepsTheFirstScheduleWhenTwoOverlapOnTheSameStart() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30),
                schedule(DayOfWeek.MONDAY, "09:00", "10:00", 60));

        assertThat(professional.slotsBetween(MONDAY, MONDAY).get(at(MONDAY, "09:00")).getEndTime())
                .isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void slotsBetweenWithoutSchedulesIsEmpty() {
        assertThat(professional().slotsBetween(MONDAY, MONDAY.plusDays(6))).isEmpty();
    }

    @Test
    void slotsBetweenIsANewMapOnEveryCall() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "09:30", 30));

        professional.slotsBetween(MONDAY, MONDAY).clear();

        assertThat(professional.slotsBetween(MONDAY, MONDAY)).hasSize(1);
    }

    @Test
    void slotAtFindsTheSlotThatStartsExactlyThen() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30));

        assertThat(professional.slotAt(at(MONDAY, "09:30"))).hasValueSatisfying(slot -> {
            assertThat(slot.getDate()).isEqualTo(MONDAY);
            assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(9, 30));
            assertThat(slot.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        });
    }

    @Test
    void slotAtIsEmptyWhenNoSlotStartsThen() {
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30));

        // En medio de un slot, antes de abrir, justo al cerrar y otro día de la semana
        assertThat(professional.slotAt(at(MONDAY, "09:10"))).isEmpty();
        assertThat(professional.slotAt(at(MONDAY, "08:30"))).isEmpty();
        assertThat(professional.slotAt(at(MONDAY, "10:00"))).isEmpty();
        assertThat(professional.slotAt(at(MONDAY.plusDays(1), "09:00"))).isEmpty();
    }

    @Test
    void slotAtAndSlotsBetweenApplyTheSameRule() {
        CatalogSchedule disabled = schedule(DayOfWeek.MONDAY, "11:00", "12:00", 30);
        disabled.setEnabled(false);
        CatalogProfessional professional = professional(schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30), disabled,
                schedule(DayOfWeek.MONDAY, "13:00", "14:00", 0));

        // Slot no define equals: se comparan sus campos
        assertThat(professional.slotsBetween(MONDAY, MONDAY)).hasSize(2);
        professional.slotsBetween(MONDAY, MONDAY).forEach(
                (start, slot) -> assertThat(professional.slotAt(start)).get().usingRecursiveComparison()
                        .isEqualTo(slot));
        assertThat(professional.slotAt(at(MONDAY, "11:00"))).isEmpty();
        assertThat(professional.slotAt(at(MONDAY, "13:00"))).isEmpty();
    }
}
