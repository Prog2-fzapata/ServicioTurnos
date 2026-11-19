package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogScheduleTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    private static CatalogSchedule schedule(String start, String end, int minutes) {
        return CatalogSchedule.builder().dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end)).slotDurationMinutes(minutes).enabled(true).build();
    }

    @Test
    void theMondayUsedByTheTestsIsAMonday() {
        assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void stepsOneSlotAtATimeUntilTheScheduleEnds() {
        assertThat(schedule("09:00", "10:30", 30).slotsOn(MONDAY))
                .extracting(Slot::getStartTime, Slot::getEndTime)
                .containsExactly(
                        tuple(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                        tuple(LocalTime.of(9, 30), LocalTime.of(10, 0)),
                        tuple(LocalTime.of(10, 0), LocalTime.of(10, 30)));
    }

    @Test
    void everySlotCarriesTheDateItWasAskedFor() {
        assertThat(schedule("09:00", "10:00", 30).slotsOn(MONDAY)).extracting(Slot::getDate).containsOnly(MONDAY);
    }

    @Test
    void aSlotThatDoesNotFitBeforeTheEndIsDiscarded() {
        assertThat(schedule("09:00", "10:00", 45).slotsOn(MONDAY)).singleElement().satisfies(slot -> {
            assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(slot.getEndTime()).isEqualTo(LocalTime.of(9, 45));
        });
    }

    @Test
    void aScheduleShorterThanOneSlotProducesNothing() {
        assertThat(schedule("09:00", "09:20", 30).slotsOn(MONDAY)).isEmpty();
    }

    @Test
    void producesNothingOnAnotherDayOfTheWeek() {
        assertThat(schedule("09:00", "10:00", 30).slotsOn(MONDAY.plusDays(1))).isEmpty();
    }

    @Test
    void aDisabledScheduleProducesNothing() {
        CatalogSchedule disabled = schedule("09:00", "10:00", 30);
        disabled.setEnabled(false);

        assertThat(disabled.slotsOn(MONDAY)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -30 })
    void aDurationThatDoesNotAdvanceIsIgnoredInsteadOfLoopingForever(int minutes) {
        assertThat(schedule("09:00", "10:00", minutes).slotsOn(MONDAY)).isEmpty();
    }
}
