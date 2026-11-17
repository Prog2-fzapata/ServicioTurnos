package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogSchedule;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OccupancyStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Slot;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatalogClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;

// El reloj fijo marca el lunes 12/10/2026 a las 10:10 en Mendoza (13:10 UTC)
@ExtendWith(MockitoExtension.class)
class GetAvailabilityUseCaseImplTest {

    private static final ZoneId MENDOZA = ZoneId.of("America/Argentina/Mendoza");
    private static final Instant NOW = Instant.parse("2026-10-12T13:10:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 19);

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private CatedraApiClient catedraApiClient;

    private GetAvailabilityUseCaseImpl useCase;

    private static CatalogSchedule schedule(DayOfWeek day, String start, String end, int minutes) {
        return CatalogSchedule.builder().dayOfWeek(day).startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                .slotDurationMinutes(minutes).enabled(true).build();
    }

    private static CatalogSchedule disabled(CatalogSchedule schedule) {
        schedule.setEnabled(false);
        return schedule;
    }

    private static CatalogProfessional professional(boolean enabled, boolean categoryEnabled,
            CatalogSchedule... schedules) {
        return CatalogProfessional.builder().id(15L).categoryId(1L).categoryName("Clinica medica").firstName("Ana")
                .lastName("Gomez").enabled(enabled).categoryEnabled(categoryEnabled)
                .weeklySchedules(List.of(schedules)).build();
    }

    private static Occupancy occupancy(LocalDate date, String start, OccupancyStatus status) {
        return Occupancy.builder().date(date).startTime(LocalTime.parse(start)).status(status).build();
    }

    private static AvailabilityQuery query(LocalDate from, LocalDate to) {
        return new AvailabilityQuery(15L, from, to);
    }

    private void catalogReturns(CatalogProfessional professional) {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional));
    }

    private void catedraReturns(Occupancy... occupancies) {
        when(catedraApiClient.findOccupancies(any(), any(), any())).thenReturn(List.of(occupancies));
    }

    private List<String> starts(Availability availability) {
        return availability.getSlots().stream().map(slot -> slot.getDate() + " " + slot.getStartTime()).toList();
    }

    @BeforeEach
    void createUseCase() {
        useCase = new GetAvailabilityUseCaseImpl(catalogClient, catedraApiClient, new ProfessionalGuard(), Clock.fixed(NOW, MENDOZA));
    }

    @Test
    void generatesTheSlotsOfTheScheduleStepByStepUntilItEnds() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "12:00", 30)));
        catedraReturns();

        Availability availability = useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow();

        assertThat(availability.getProfessionalId()).isEqualTo(15L);
        assertThat(availability.getFrom()).isEqualTo(NEXT_MONDAY);
        assertThat(availability.getTo()).isEqualTo(NEXT_MONDAY);
        assertThat(availability.getSlots()).extracting(Slot::getStartTime).containsExactly(LocalTime.of(9, 0),
                LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0),
                LocalTime.of(11, 30));
        assertThat(availability.getSlots()).extracting(Slot::getEndTime).containsExactly(LocalTime.of(9, 30),
                LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30),
                LocalTime.of(12, 0));
        assertThat(availability.getSlots()).extracting(Slot::getDate).containsOnly(NEXT_MONDAY);
    }

    @Test
    void aSlotThatDoesNotFitBeforeTheEndOfTheScheduleIsDiscarded() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 45)));
        catedraReturns();

        Availability availability = useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow();

        assertThat(availability.getSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(slot.getEndTime()).isEqualTo(LocalTime.of(9, 45));
        });
    }

    @Test
    void aSlotThatEndsExactlyWhenTheScheduleEndsIsKept() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        catedraReturns();

        assertThat(starts(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow()))
                .containsExactly("2026-10-19 09:00", "2026-10-19 09:30");
    }

    @Test
    void ignoresDisabledSchedules() {
        catalogReturns(professional(true, true, disabled(schedule(DayOfWeek.MONDAY, "09:00", "12:00", 30)),
                schedule(DayOfWeek.MONDAY, "14:00", "15:00", 30)));
        catedraReturns();

        assertThat(starts(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow()))
                .containsExactly("2026-10-19 14:00", "2026-10-19 14:30");
    }

    @Test
    void onlyTheDaysOfTheWeekOfEachScheduleProduceSlotsAcrossTheWholeRange() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 60),
                schedule(DayOfWeek.WEDNESDAY, "16:00", "17:00", 60)));
        catedraReturns();

        // Del lunes 19 al domingo 1/11: dos lunes y dos miércoles
        Availability availability = useCase
                .getAvailability(query(NEXT_MONDAY, LocalDate.of(2026, 11, 1))).orElseThrow();

        assertThat(starts(availability)).containsExactly("2026-10-19 09:00", "2026-10-21 16:00", "2026-10-26 09:00",
                "2026-10-28 16:00");
    }

    @Test
    void theSlotsComeOrderedByDateAndTimeWhateverTheOrderOfTheSchedules() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.TUESDAY, "08:00", "09:00", 60),
                schedule(DayOfWeek.MONDAY, "15:00", "16:00", 60), schedule(DayOfWeek.MONDAY, "08:00", "09:00", 60)));
        catedraReturns();

        assertThat(starts(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY.plusDays(1))).orElseThrow()))
                .containsExactly("2026-10-19 08:00", "2026-10-19 15:00", "2026-10-20 08:00");
    }

    @Test
    void twoSchedulesThatOverlapDoNotRepeatTheSameStart() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "12:00", 30),
                schedule(DayOfWeek.MONDAY, "11:00", "14:00", 30)));
        catedraReturns();

        List<String> starts = starts(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow());

        assertThat(starts).hasSize(10).doesNotHaveDuplicates().startsWith("2026-10-19 09:00")
                .endsWith("2026-10-19 13:30");
    }

    @Test
    void subtractsTheConfirmedAndTheHeldOccupancies() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "11:00", 30)));
        catedraReturns(occupancy(NEXT_MONDAY, "09:30", OccupancyStatus.HELD),
                occupancy(NEXT_MONDAY, "10:00", OccupancyStatus.CONFIRMED));

        assertThat(starts(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow()))
                .containsExactly("2026-10-19 09:00", "2026-10-19 10:30");
    }

    @Test
    void anOccupancyOnAnotherDateOrOutsideTheSlotsDoesNotRemoveAnything() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        catedraReturns(occupancy(NEXT_MONDAY.plusDays(7), "09:00", OccupancyStatus.CONFIRMED),
                occupancy(NEXT_MONDAY, "09:15", OccupancyStatus.HELD));

        assertThat(starts(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow()))
                .containsExactly("2026-10-19 09:00", "2026-10-19 09:30");
    }

    @Test
    void asksTheCatedraForTheSameRange() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        catedraReturns();

        useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY.plusDays(10)));

        verify(catedraApiClient).findOccupancies(15L, NEXT_MONDAY, NEXT_MONDAY.plusDays(10));
    }

    @Test
    void discardsTheSlotsThatAlreadyStartedTodayInTheLocalTimeOfAttention() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "12:00", 30)));
        catedraReturns();

        // Son las 10:10 en Mendoza: el slot de las 10:00 ya empezó y el de las 10:30 no
        assertThat(starts(useCase.getAvailability(query(TODAY, TODAY)).orElseThrow()))
                .containsExactly("2026-10-12 10:30", "2026-10-12 11:00", "2026-10-12 11:30");
    }

    @Test
    void aSlotThatStartsExactlyNowIsStillOffered() {
        useCase = new GetAvailabilityUseCaseImpl(catalogClient, catedraApiClient, new ProfessionalGuard(),
                Clock.fixed(Instant.parse("2026-10-12T13:30:00Z"), MENDOZA));
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "10:00", "11:00", 30)));
        catedraReturns();

        assertThat(starts(useCase.getAvailability(query(TODAY, TODAY)).orElseThrow()))
                .containsExactly("2026-10-12 10:30");
    }

    // Con el reloj en UTC (13:10) todos los horarios de la mañana de Mendoza ya habrían pasado
    @Test
    void whichSlotsArePastDependsOnTheZoneOfTheClock() {
        useCase = new GetAvailabilityUseCaseImpl(catalogClient, catedraApiClient, new ProfessionalGuard(),
                Clock.fixed(NOW, ZoneId.of("UTC")));
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "12:00", 30)));
        catedraReturns();

        assertThat(useCase.getAvailability(query(TODAY, TODAY)).orElseThrow().getSlots()).isEmpty();
    }

    @Test
    void aProfessionalWithoutSchedulesHasNoSlots() {
        catalogReturns(professional(true, true));
        catedraReturns();

        assertThat(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow().getSlots()).isEmpty();
    }

    // Una duración cero o negativa no avanza nunca: se ignora el horario en vez de colgar el pedido
    @Test
    void aScheduleWithAnInvalidDurationIsIgnoredInsteadOfLooping() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "12:00", 0),
                schedule(DayOfWeek.MONDAY, "13:00", "14:00", -15), schedule(DayOfWeek.MONDAY, "15:00", "16:00", 60)));
        catedraReturns();

        // Preemptivo: un bucle que no termina no se puede interrumpir desde el mismo hilo
        Availability availability = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow());

        assertThat(starts(availability)).containsExactly("2026-10-19 15:00");
    }

    // Sumar la duración pasada la medianoche volvería a las 00:00 y el bucle no terminaría
    @Test
    void aScheduleThatEndsJustBeforeMidnightDoesNotWrapAround() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "23:00", "23:59", 30)));
        catedraReturns();

        Availability availability = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)).orElseThrow());

        assertThat(starts(availability)).containsExactly("2026-10-19 23:00");
    }

    @Test
    void aMissingProfessionalIsEmptyAndTheCatedraIsNotAsked() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.empty());

        assertThat(useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY))).isEmpty();
        verifyNoInteractions(catedraApiClient);
    }

    @Test
    void aDisabledProfessionalIsRejectedAndTheCatedraIsNotAsked() {
        catalogReturns(professional(false, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30)));

        assertThatThrownBy(() -> useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)))
                .isInstanceOf(ProfessionalDisabledException.class).hasMessageContaining("profesional 15");
        verifyNoInteractions(catedraApiClient);
    }

    @Test
    void aDisabledCategoryBlocksItsProfessionalAndTheCatedraIsNotAsked() {
        catalogReturns(professional(true, false, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30)));

        assertThatThrownBy(() -> useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)))
                .isInstanceOf(ProfessionalDisabledException.class).hasMessageContaining("categoría");
        verifyNoInteractions(catedraApiClient);
    }

    @Test
    void aCatalogFailureIsNotSwallowed() {
        when(catalogClient.findProfessional(15L)).thenThrow(new CatalogUnavailableException("caído", null));

        assertThatThrownBy(() -> useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void aCatedraFailureIsNotSwallowedAndNoPartialAnswerIsGiven() {
        catalogReturns(professional(true, true, schedule(DayOfWeek.MONDAY, "09:00", "10:00", 30)));
        when(catedraApiClient.findOccupancies(any(), any(), any()))
                .thenThrow(new CatedraUnavailableException("caída", null));

        assertThatThrownBy(() -> useCase.getAvailability(query(NEXT_MONDAY, NEXT_MONDAY)))
                .isInstanceOf(CatedraUnavailableException.class);
    }
}
