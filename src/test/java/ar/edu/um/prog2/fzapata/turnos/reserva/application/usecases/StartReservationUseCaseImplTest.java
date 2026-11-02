package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PatientNameRequiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogAccount;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogSchedule;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Hold;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldConfirmation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OccupancyStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatalogClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

// Catálogo y cátedra son mocks; la base es una en memoria y la máquina de estados es la real, así que se miran los
// procesos tal como quedaron. "Hoy" es el lunes 12/10/2026 a las 10:10 en Mendoza; se reserva el lunes 19 a las 9:30
@ExtendWith(MockitoExtension.class)
class StartReservationUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-10-12T13:10:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);
    private static final LocalTime START = LocalTime.of(9, 30);
    private static final Instant HOLD_EXPIRES = Instant.parse("2026-10-12T13:20:00Z");
    private static final Instant CONFIRM_EXPIRES = Instant.parse("2026-10-12T13:20:05Z");
    private static final ReservationRequest REQUEST = new ReservationRequest("42", 15L, DATE, START);

    // Una base en memoria: guarda el mismo objeto y le asigna id la primera vez
    private static class InMemoryProcesses implements ReservationProcessRepository {

        private final List<ReservationProcess> processes = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<ReservationProcess> findById(Long id) {
            return processes.stream().filter(process -> id.equals(process.getId())).findFirst();
        }

        @Override
        public ReservationProcess save(ReservationProcess process) {
            if (process.getId() == null) {
                process.setId(ids.incrementAndGet());
                processes.add(process);
            }
            return process;
        }
    }

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private CatedraApiClient catedraApiClient;

    private InMemoryProcesses store;
    private StartReservationUseCaseImpl useCase;

    private static CatalogSchedule monday(String start, String end, boolean enabled) {
        return CatalogSchedule.builder().dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end)).slotDurationMinutes(30).enabled(enabled).build();
    }

    private static CatalogProfessional professional(boolean enabled, boolean categoryEnabled,
            CatalogSchedule... schedules) {
        return CatalogProfessional.builder().id(15L).categoryId(1L).categoryName("Clinica medica").firstName("Ana")
                .lastName("Gomez").enabled(enabled).categoryEnabled(categoryEnabled)
                .weeklySchedules(List.of(schedules)).build();
    }

    private static CatalogAccount account(String firstName, String lastName) {
        return CatalogAccount.builder().firstName(firstName).lastName(lastName).build();
    }

    private static Hold hold(String suffix) {
        return Hold.builder().holdId("hold-" + suffix).reservationProcessId("process-" + suffix)
                .expiresAt(HOLD_EXPIRES).build();
    }

    private static HoldConfirmation confirmation(String suffix) {
        return HoldConfirmation.builder().reservationProcessId("process-" + suffix).expiresAt(CONFIRM_EXPIRES).build();
    }

    private static Occupancy occupancy(LocalTime start, OccupancyStatus status) {
        return Occupancy.builder().date(DATE).startTime(start).status(status).build();
    }

    private static CatedraRejectedException rejected(CatedraRejection rejection) {
        return new CatedraRejectedException(rejection);
    }

    private StartReservationUseCaseImpl useCaseWith(int holdAttempts) {
        Clock clock = Clock.fixed(NOW, ZoneId.of("America/Argentina/Mendoza"));
        ApplyReservationTransitionUseCaseImpl transitions = new ApplyReservationTransitionUseCaseImpl(store,
                new ConflictRetryPolicy(3), clock);
        return new StartReservationUseCaseImpl(catalogClient, catedraApiClient, store, transitions,
                new ProfessionalGuard(), new HoldRetryPolicy(holdAttempts), clock);
    }

    private void catalogKnowsTheProfessionalAndTheAccount() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));
        when(catalogClient.findAccount()).thenReturn(account("Juan", "Perez"));
    }

    private List<ReservationStatus> statuses() {
        return store.processes.stream().map(ReservationProcess::getStatus).toList();
    }

    private ReservationProcess only() {
        assertThat(store.processes).hasSize(1);
        return store.processes.get(0);
    }

    static Stream<Arguments> holdRejections() {
        return Stream.of(
                Arguments.of(CatedraRejection.SLOT_ALREADY_HELD, SlotUnavailableException.class),
                Arguments.of(CatedraRejection.SLOT_ALREADY_RESERVED, SlotUnavailableException.class),
                Arguments.of(CatedraRejection.PROFESSIONAL_DISABLED, ProfessionalDisabledException.class),
                Arguments.of(CatedraRejection.PROFESSIONAL_NOT_FOUND, ProfessionalNotFoundException.class),
                Arguments.of(CatedraRejection.INVALID_SLOT, InvalidSlotException.class),
                Arguments.of(CatedraRejection.HOLD_EXPIRED, CatedraResponseException.class));
    }

    static Stream<Arguments> accountsWithoutAUsableName() {
        return Stream.of(
                Arguments.of(null, "Perez"), Arguments.of("Juan", null), Arguments.of("", "Perez"),
                Arguments.of("Juan", "   "), Arguments.of("J", "Perez"), Arguments.of("Juan", "P"),
                Arguments.of(" J ", "Perez"));
    }

    static Stream<Arguments> slotsOutsideTheAgenda() {
        return Stream.of(
                // Entre dos slots
                Arguments.of(DATE, LocalTime.of(9, 15)),
                // Antes y después del horario de atención
                Arguments.of(DATE, LocalTime.of(8, 30)), Arguments.of(DATE, LocalTime.of(12, 0)),
                // Un día sin horario
                Arguments.of(DATE.plusDays(1), LocalTime.of(9, 30)));
    }

    @BeforeEach
    void createUseCase() {
        store = new InMemoryProcesses();
        useCase = useCaseWith(2);
    }

    @Test
    void startsTheReservationAndLeavesItWaitingForThePhone() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez")).thenReturn(confirmation("1"));

        ReservationProcess result = useCase.startReservation(REQUEST).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(result.getHoldId()).isEqualTo("hold-1");
        assertThat(result.getReservationProcessId()).isEqualTo("process-1");
        assertThat(result.getExpiresAt()).isEqualTo(CONFIRM_EXPIRES);
        ReservationProcess stored = only();
        assertThat(stored).isSameAs(result);
        assertThat(stored.getUserId()).isEqualTo("42");
        assertThat(stored.getProfessionalId()).isEqualTo(15L);
        assertThat(stored.getProfessionalFirstName()).isEqualTo("Ana");
        assertThat(stored.getProfessionalLastName()).isEqualTo("Gomez");
        assertThat(stored.getAppointmentDate()).isEqualTo(DATE);
        assertThat(stored.getStartTime()).isEqualTo(START);
        assertThat(stored.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(stored.getPatientFirstName()).isEqualTo("Juan");
        assertThat(stored.getPatientLastName()).isEqualTo("Perez");
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getFailureReason()).isNull();
    }

    @Test
    void asksInTheOrderOfTheDiagramAndTheNamesComeBeforeTheHold() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(confirmation("1"));

        useCase.startReservation(REQUEST);

        InOrder order = inOrder(catalogClient, catedraApiClient);
        order.verify(catalogClient).findProfessional(15L);
        order.verify(catalogClient).findAccount();
        order.verify(catedraApiClient).createHold(15L, DATE, START, "42");
        order.verify(catedraApiClient).confirmHold("hold-1", "42", "Juan", "Perez");
    }

    @Test
    void theNamesAreSentWithoutSurroundingSpaces() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));
        when(catalogClient.findAccount()).thenReturn(account("  Juan ", " Perez  "));
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez")).thenReturn(confirmation("1"));

        ReservationProcess result = useCase.startReservation(REQUEST).orElseThrow();

        assertThat(result.getPatientFirstName()).isEqualTo("Juan");
        assertThat(result.getPatientLastName()).isEqualTo("Perez");
    }

    @Test
    void aMissingProfessionalIsEmptyAndNothingElseIsAsked() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.empty());

        assertThat(useCase.startReservation(REQUEST)).isEmpty();
        verify(catalogClient, never()).findAccount();
        verifyNoInteractions(catedraApiClient);
        assertThat(store.processes).isEmpty();
    }

    @Test
    void aDisabledProfessionalOrCategoryCreatesNothing() {
        when(catalogClient.findProfessional(15L)).thenReturn(
                Optional.of(professional(false, true, monday("09:00", "12:00", true))),
                Optional.of(professional(true, false, monday("09:00", "12:00", true))));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(ProfessionalDisabledException.class);
        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(ProfessionalDisabledException.class);
        verify(catalogClient, never()).findAccount();
        verifyNoInteractions(catedraApiClient);
        assertThat(store.processes).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("slotsOutsideTheAgenda")
    void aSlotOutsideTheAgendaIsInvalidAndCreatesNothing(LocalDate date, LocalTime start) {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));

        assertThatThrownBy(() -> useCase.startReservation(new ReservationRequest("42", 15L, date, start)))
                .isInstanceOf(InvalidSlotException.class);
        verify(catalogClient, never()).findAccount();
        verifyNoInteractions(catedraApiClient);
        assertThat(store.processes).isEmpty();
    }

    @Test
    void aSlotOfADisabledScheduleIsInvalid() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", false))));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(InvalidSlotException.class);
        assertThat(store.processes).isEmpty();
    }

    @Test
    void aSlotThatAlreadyPassedIsInvalidEvenIfItIsInTheAgenda() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));

        // Hoy lunes 12 a las 10:10: el slot de las 9:30 ya empezó
        assertThatThrownBy(() -> useCase.startReservation(
                new ReservationRequest("42", 15L, LocalDate.of(2026, 10, 12), START)))
                .isInstanceOf(InvalidSlotException.class).hasMessageContaining("pasó");
        assertThat(store.processes).isEmpty();
    }

    @Test
    void aSlotLaterTodayIsAccepted() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, LocalDate.of(2026, 10, 12), LocalTime.of(10, 30), "42"))
                .thenReturn(hold("1"));
        when(catedraApiClient.confirmHold(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(confirmation("1"));

        assertThat(useCase.startReservation(new ReservationRequest("42", 15L, LocalDate.of(2026, 10, 12),
                LocalTime.of(10, 30)))).isPresent();
    }

    // Sin nombre no se pide ningún hold: no queda nada bloqueado en la cátedra ni un proceso a medias
    @ParameterizedTest(name = "[{0}] [{1}]")
    @MethodSource("accountsWithoutAUsableName")
    void aMissingOrTooShortNameStopsBeforeCreatingAnything(String firstName, String lastName) {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));
        when(catalogClient.findAccount()).thenReturn(account(firstName, lastName));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(PatientNameRequiredException.class);
        verifyNoInteractions(catedraApiClient);
        assertThat(store.processes).isEmpty();
    }

    @Test
    void aNameOfExactlyTwoCharactersIsEnough() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));
        when(catalogClient.findAccount()).thenReturn(account("Al", "Li"));
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Al", "Li")).thenReturn(confirmation("1"));

        assertThat(useCase.startReservation(REQUEST)).isPresent();
    }

    @Test
    void aCatalogFailureAskingForTheAccountCreatesNothing() {
        when(catalogClient.findProfessional(15L)).thenReturn(Optional.of(professional(true, true,
                monday("09:00", "12:00", true))));
        when(catalogClient.findAccount()).thenThrow(new CatalogUnavailableException("caído", null));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatalogUnavailableException.class);
        verifyNoInteractions(catedraApiClient);
        assertThat(store.processes).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("holdRejections")
    void aRejectedHoldClosesTheProcessAsFailedAndIsTranslated(CatedraRejection rejection,
            Class<? extends RuntimeException> expected) {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenThrow(rejected(rejection));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(expected);

        ReservationProcess stored = only();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo(rejection.name());
        assertThat(stored.getHoldId()).isNull();
        verify(catedraApiClient, never()).confirmHold(anyString(), anyString(), anyString(), anyString());
        verify(catedraApiClient, never()).findOccupancies(any(), any(), any());
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    // Nunca se interpreta como «es mi hold»: ni se confirma ni se vuelve a pedir
    @Test
    void anOccupiedSlotIsNeverTakenAsOurOwnHold() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(rejected(CatedraRejection.SLOT_ALREADY_HELD));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(SlotUnavailableException.class);
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void aCatedraThatWasNotReachedFailsTheProcessAndDoesNotInvestigateAnyHold() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(new CatedraNotReachedException("sin conexión", null));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraNotReachedException.class);

        assertThat(only().getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(only().getFailureReason()).isEqualTo("CATEDRA_NOT_REACHED");
        verify(catedraApiClient, never()).findOccupancies(any(), any(), any());
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    // D80: si se pierde la respuesta del hold no se repite el POST sobre el mismo intento; se mira qué hay ocupado
    @ParameterizedTest
    @MethodSource("busyStatuses")
    void aLostHoldAnswerWithTheSlotOccupiedIsUnavailable(OccupancyStatus status) {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(new CatedraUnavailableException("timeout", null));
        when(catedraApiClient.findOccupancies(15L, DATE, DATE)).thenReturn(List.of(occupancy(START, status)));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(SlotUnavailableException.class);

        ReservationProcess stored = only();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo("HOLD_RESULT_UNKNOWN");
        assertThat(stored.getHoldId()).isNull();
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    static Stream<OccupancyStatus> busyStatuses() {
        return Stream.of(OccupancyStatus.HELD, OccupancyStatus.CONFIRMED);
    }

    @Test
    void aLostHoldAnswerWithTheSlotFreeAsksForANewHoldOnANewProcess() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(new CatedraUnavailableException("timeout", null)).thenReturn(hold("2"));
        // Otro horario ocupado del mismo día no impide el nuestro
        when(catedraApiClient.findOccupancies(15L, DATE, DATE))
                .thenReturn(List.of(occupancy(LocalTime.of(9, 0), OccupancyStatus.CONFIRMED)));
        when(catedraApiClient.confirmHold("hold-2", "42", "Juan", "Perez")).thenReturn(confirmation("2"));

        ReservationProcess result = useCase.startReservation(REQUEST).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(result.getHoldId()).isEqualTo("hold-2");
        assertThat(statuses()).containsExactly(ReservationStatus.FAILED, ReservationStatus.WAITING_PHONE);
        assertThat(store.processes.get(0).getFailureReason()).isEqualTo("HOLD_RESULT_UNKNOWN");
        assertThat(store.processes.get(0).getHoldId()).isNull();
        assertThat(store.processes.get(0).getId()).isNotEqualTo(store.processes.get(1).getId());
        verify(catedraApiClient, times(2)).createHold(15L, DATE, START, "42");
    }

    @Test
    void whenTheAttemptsRunOutTheCatedraFailureIsReported() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(new CatedraUnavailableException("timeout", null));
        when(catedraApiClient.findOccupancies(15L, DATE, DATE)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraUnavailableException.class);

        assertThat(statuses()).containsExactly(ReservationStatus.FAILED, ReservationStatus.FAILED);
        verify(catedraApiClient, times(2)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void oneAttemptDisablesTheRetryAfterALostHold() {
        useCase = useCaseWith(1);
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(new CatedraUnavailableException("timeout", null));
        when(catedraApiClient.findOccupancies(15L, DATE, DATE)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraUnavailableException.class);

        assertThat(store.processes).hasSize(1);
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void ifTheOccupanciesCannotBeReadAfterALostHoldTheReservationFails() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(new CatedraUnavailableException("timeout", null));
        when(catedraApiClient.findOccupancies(15L, DATE, DATE))
                .thenThrow(new CatedraUnavailableException("sigue caída", null));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraUnavailableException.class);

        assertThat(statuses()).containsExactly(ReservationStatus.FAILED);
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void anUnexpectedErrorCreatingTheHoldClosesTheProcessAndIsRethrown() {
        catalogKnowsTheProfessionalAndTheAccount();
        CatedraResponseException unexpected = new CatedraResponseException("cuerpo ilegible", null);
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenThrow(unexpected);

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isSameAs(unexpected);

        assertThat(only().getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(only().getFailureReason()).isEqualTo("UNEXPECTED_ERROR");
    }

    @Test
    void theHoldIsSavedBeforeTheConfirmation() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new CatedraUnavailableException("timeout", null));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraUnavailableException.class);

        // Con los ids guardados, el vencimiento o la reconciliación pueden resolverlo
        ReservationProcess stored = only();
        assertThat(stored.getHoldId()).isEqualTo("hold-1");
        assertThat(stored.getReservationProcessId()).isEqualTo("process-1");
        assertThat(stored.getExpiresAt()).isEqualTo(HOLD_EXPIRES);
    }

    // El hold existe y la cátedra pudo haberlo confirmado: no se da por perdido ni se pide otro
    @Test
    void aLostConfirmationLeavesTheProcessHeldAndDoesNotAskForAnotherHold() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez"))
                .thenThrow(new CatedraUnavailableException("timeout", null));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraUnavailableException.class);

        assertThat(only().getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(only().getFailureReason()).isNull();
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void anExpiredHoldAtConfirmationIsRetriedWithANewHold() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"), hold("2"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez"))
                .thenThrow(rejected(CatedraRejection.HOLD_EXPIRED));
        when(catedraApiClient.confirmHold("hold-2", "42", "Juan", "Perez")).thenReturn(confirmation("2"));

        ReservationProcess result = useCase.startReservation(REQUEST).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(result.getHoldId()).isEqualTo("hold-2");
        assertThat(statuses()).containsExactly(ReservationStatus.EXPIRED, ReservationStatus.WAITING_PHONE);
        assertThat(store.processes.get(0).getFailureReason()).isEqualTo("HOLD_EXPIRED");
        // No hace falta mirar las ocupaciones: la cátedra confirmó que el hold ya no existe
        verify(catedraApiClient, never()).findOccupancies(any(), any(), any());
    }

    @Test
    void aHoldThatDisappearedAtConfirmationIsRetriedToo() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"), hold("2"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez"))
                .thenThrow(rejected(CatedraRejection.HOLD_NOT_FOUND));
        when(catedraApiClient.confirmHold("hold-2", "42", "Juan", "Perez")).thenReturn(confirmation("2"));

        assertThat(useCase.startReservation(REQUEST).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(statuses()).containsExactly(ReservationStatus.FAILED, ReservationStatus.WAITING_PHONE);
        assertThat(store.processes.get(0).getFailureReason()).isEqualTo("HOLD_NOT_FOUND");
    }

    @Test
    void ifTheNewHoldIsTakenByOthersTheSlotIsUnavailable() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenReturn(hold("1")).thenThrow(rejected(CatedraRejection.SLOT_ALREADY_HELD));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez"))
                .thenThrow(rejected(CatedraRejection.HOLD_EXPIRED));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(SlotUnavailableException.class);

        assertThat(statuses()).containsExactly(ReservationStatus.EXPIRED, ReservationStatus.FAILED);
    }

    @Test
    void whenTheHoldKeepsExpiringTheAttemptsRunOut() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"), hold("2"));
        when(catedraApiClient.confirmHold(anyString(), eq("42"), eq("Juan"), eq("Perez")))
                .thenThrow(rejected(CatedraRejection.HOLD_EXPIRED));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(ReservationExpiredException.class);

        assertThat(statuses()).containsExactly(ReservationStatus.EXPIRED, ReservationStatus.EXPIRED);
        verify(catedraApiClient, times(2)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void oneAttemptDisablesTheRetryAfterAnExpiredHold() {
        useCase = useCaseWith(1);
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(rejected(CatedraRejection.HOLD_EXPIRED));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(ReservationExpiredException.class);

        assertThat(statuses()).containsExactly(ReservationStatus.EXPIRED);
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    // Una repetición interna de la confirmación que ya había salido bien: es la misma reserva
    @Test
    void anAlreadyConfirmedHoldIsTheSameReservationWithTheHoldExpiry() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold("hold-1", "42", "Juan", "Perez"))
                .thenThrow(rejected(CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED));

        ReservationProcess result = useCase.startReservation(REQUEST).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(result.getExpiresAt()).isEqualTo(HOLD_EXPIRES);
        assertThat(store.processes).hasSize(1);
    }

    @Test
    void anUnexpectedRejectionAtConfirmationFailsTheProcess() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        when(catedraApiClient.confirmHold(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(rejected(CatedraRejection.INVALID_SLOT));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(CatedraResponseException.class);

        assertThat(only().getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(only().getFailureReason()).isEqualTo("UNEXPECTED_ERROR");
        verify(catedraApiClient, times(1)).createHold(any(), any(), any(), anyString());
    }

    @Test
    void anUnexpectedErrorAtConfirmationFailsTheProcessAndIsRethrown() {
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));
        CatedraResponseException unexpected = new CatedraResponseException("cuerpo ilegible", null);
        when(catedraApiClient.confirmHold(anyString(), anyString(), anyString(), anyString())).thenThrow(unexpected);

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isSameAs(unexpected);

        assertThat(only().getStatus()).isEqualTo(ReservationStatus.FAILED);
    }

    // Si ni siquiera se puede registrar el cierre, el error que ve quien llama sigue siendo el original
    @Test
    void aFailureRecordingTheClosureDoesNotHideTheOriginalError() {
        ApplyReservationTransitionUseCaseImpl brokenTransitions = new ApplyReservationTransitionUseCaseImpl(store,
                new ConflictRetryPolicy(1), Clock.fixed(NOW, ZoneId.of("UTC"))) {
            @Override
            public Optional<TransitionResult> applyTransition(Long processId, ReservationTransition transition) {
                throw new IllegalStateException("la base se cayó");
            }
        };
        useCase = new StartReservationUseCaseImpl(catalogClient, catedraApiClient, store, brokenTransitions,
                new ProfessionalGuard(), new HoldRetryPolicy(2), Clock.fixed(NOW, ZoneId.of("America/Argentina/Mendoza")));
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42"))
                .thenThrow(rejected(CatedraRejection.SLOT_ALREADY_HELD));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(SlotUnavailableException.class)
                .satisfies(error -> assertThat(error.getSuppressed()).singleElement()
                        .isInstanceOf(IllegalStateException.class));
    }

    // Si el proceso no está donde este caso de uso cree, seguir sería trabajar sobre un estado imposible
    @Test
    void aStepThatIsNotAppliedIsAnInconsistencyAndStopsTheReservation() {
        ApplyReservationTransitionUseCaseImpl stuck = new ApplyReservationTransitionUseCaseImpl(store,
                new ConflictRetryPolicy(1), Clock.fixed(NOW, ZoneId.of("UTC"))) {
            @Override
            public Optional<TransitionResult> applyTransition(Long processId, ReservationTransition transition) {
                return Optional.of(new TransitionResult(TransitionOutcome.REJECTED, ReservationStatus.EXPIRED,
                        transition.target(), null));
            }
        };
        useCase = new StartReservationUseCaseImpl(catalogClient, catedraApiClient, store, stuck,
                new ProfessionalGuard(), new HoldRetryPolicy(2), Clock.fixed(NOW, ZoneId.of("America/Argentina/Mendoza")));
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRED").hasMessageContaining("HELD");
        verify(catedraApiClient, never()).confirmHold(anyString(), anyString(), anyString(), anyString());
    }

    // El proceso desapareció entre un paso y el siguiente: no se sigue como si nada
    @Test
    void aProcessThatDisappearsBetweenStepsStopsTheReservation() {
        ApplyReservationTransitionUseCaseImpl vanishing = new ApplyReservationTransitionUseCaseImpl(store,
                new ConflictRetryPolicy(1), Clock.fixed(NOW, ZoneId.of("UTC"))) {
            @Override
            public Optional<TransitionResult> applyTransition(Long processId, ReservationTransition transition) {
                return Optional.empty();
            }
        };
        useCase = new StartReservationUseCaseImpl(catalogClient, catedraApiClient, store, vanishing,
                new ProfessionalGuard(), new HoldRetryPolicy(2), Clock.fixed(NOW, ZoneId.of("America/Argentina/Mendoza")));
        catalogKnowsTheProfessionalAndTheAccount();
        when(catedraApiClient.createHold(15L, DATE, START, "42")).thenReturn(hold("1"));

        assertThatThrownBy(() -> useCase.startReservation(REQUEST))
                .isInstanceOf(ReservationProcessNotFoundException.class);
    }
}
