package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidReservationStateException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhoneWindowExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PatientNameRequiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationNotCancellableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.ReservationActionLogger;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler.ReservaExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.ReservationDtoMapper;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.ProblemDetailsSecurityHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.security.SecurityConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.web.GlobalExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import lombok.RequiredArgsConstructor;

@WebMvcTest(ReservationController.class)
@Import({ReservationDtoMapper.class, ReservaExceptionHandler.class, GlobalExceptionHandler.class,
        ProblemDetailsFactory.class, SecurityConfiguration.class, JwtConfiguration.class,
        ProblemDetailsSecurityHandler.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationControllerTest {

    private static final String URL = "/api/reservations";
    private static final String BODY = """
            {"professionalId": 15, "date": "2026-10-12", "startTime": "09:30"}""";

    private final MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private ReservationActionLogger reservationActionLogger;

    private ReservationProcess waitingForPhone() {
        return ReservationProcess.builder().id(101L).reservationProcessId("process-a92b47d0").holdId("hold-1")
                .userId("42").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 10, 12)).startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 0)).status(ReservationStatus.WAITING_PHONE)
                .expiresAt(Instant.parse("2026-10-07T14:44:50Z")).patientFirstName("Juan").patientLastName("Perez")
                .createdAt(Instant.parse("2026-10-07T14:35:00Z")).updatedAt(Instant.parse("2026-10-07T14:35:10Z"))
                .version(2L).build();
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post(URL).with(jwt().jwt(token -> token.claim("uid", "42")))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // Cada acción exitosa deja su línea de log (#50); una que falla no deja nada, porque no pasó
    @Test
    void startingAReservationIsLoggedAsAnAction() throws Exception {
        ReservationProcess started = waitingForPhone();
        when(reservaService.startReservation(any())).thenReturn(started);

        send(BODY).andExpect(status().isCreated());

        verify(reservationActionLogger).started(started);
    }

    @Test
    void aStartThatFailsIsNotLoggedAsAnAction() throws Exception {
        when(reservaService.startReservation(any())).thenThrow(new SlotUnavailableException());

        send(BODY).andExpect(status().isConflict());

        verifyNoInteractions(reservationActionLogger);
    }

    @Test
    void aVersionConflictThatExhaustsTheRetriesWhileStartingIsAConflict() throws Exception {
        when(reservaService.startReservation(any()))
                .thenThrow(new ReservationConflictException(101L, new RuntimeException("versión")));

        send(BODY)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
    }

    @Test
    void createsTheReservationAndAnswersWithTheObjectOfTheContract() throws Exception {
        when(reservaService.startReservation(any())).thenReturn(waitingForPhone());

        send(BODY)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.reservationProcessId").value("process-a92b47d0"))
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.professionalFirstName").value("Ana"))
                .andExpect(jsonPath("$.professionalLastName").value("Gomez"))
                .andExpect(jsonPath("$.appointmentDate").value("2026-10-12"))
                .andExpect(jsonPath("$.startTime").value("09:30:00"))
                .andExpect(jsonPath("$.endTime").value("10:00:00"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-07T14:44:50Z"))
                .andExpect(jsonPath("$.patientPhone").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-10-07T14:35:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-07T14:35:10Z"))
                // Nunca se expone el dueño ni los ids internos de la cátedra
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.holdId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void theUserComesFromTheTokenAndNeverFromTheBody() throws Exception {
        when(reservaService.startReservation(any())).thenReturn(waitingForPhone());

        send("""
                {"professionalId": 15, "date": "2026-10-12", "startTime": "09:30", "userId": "99", "uid": "99"}""")
                .andExpect(status().isCreated());

        verify(reservaService).startReservation(
                new ReservationRequest("42", 15L, LocalDate.of(2026, 10, 12), LocalTime.of(9, 30)));
    }

    @ParameterizedTest
    @CsvSource({"09:30", "09:30:00"})
    void acceptsTheTimeWithOrWithoutSeconds(String startTime) throws Exception {
        when(reservaService.startReservation(any())).thenReturn(waitingForPhone());

        send("{\"professionalId\": 15, \"date\": \"2026-10-12\", \"startTime\": \"" + startTime + "\"}")
                .andExpect(status().isCreated());

        verify(reservaService).startReservation(
                new ReservationRequest("42", 15L, LocalDate.of(2026, 10, 12), LocalTime.of(9, 30)));
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservaService);
    }

    @Test
    void missingFieldsAreAValidationErrorWithTheNameOfEachField() throws Exception {
        send("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(
                        containsInAnyOrder("professionalId", "date", "startTime")));
        verifyNoInteractions(reservaService);
    }

    @Test
    void aNonPositiveProfessionalIdIsAValidationError() throws Exception {
        send("{\"professionalId\": 0, \"date\": \"2026-10-12\", \"startTime\": \"09:30\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("professionalId"));
        verifyNoInteractions(reservaService);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"professionalId": "abc", "date": "2026-10-12", "startTime": "09:30"}
            {"professionalId": 15, "date": "12/10/2026", "startTime": "09:30"}
            {"professionalId": 15, "date": "2026-10-12", "startTime": "9 y media"}
            not-json
            """)
    void aBodyThatCannotBeReadIsAValidationError(String body) throws Exception {
        send(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(reservaService);
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource(delimiter = '|', textBlock = """
            PROFESSIONAL_NOT_FOUND | 404 | PROFESSIONAL_NOT_FOUND
            PROFESSIONAL_DISABLED | 400 | PROFESSIONAL_DISABLED
            PATIENT_NAME_REQUIRED | 400 | PATIENT_NAME_REQUIRED
            INVALID_SLOT | 400 | INVALID_SLOT
            SLOT_UNAVAILABLE | 409 | SLOT_UNAVAILABLE
            RESERVATION_EXPIRED | 409 | RESERVATION_EXPIRED
            CATALOG_UNAVAILABLE | 503 | CATALOG_UNAVAILABLE
            CATEDRA_UNAVAILABLE | 503 | CATEDRA_UNAVAILABLE
            """)
    void everyBusinessErrorHasItsOwnStatusAndCode(String failure, int expectedStatus, String expectedCode)
            throws Exception {
        RuntimeException error = switch (failure) {
            case "PROFESSIONAL_NOT_FOUND" -> new ProfessionalNotFoundException(15L);
            case "PROFESSIONAL_DISABLED" -> new ProfessionalDisabledException("deshabilitado");
            case "PATIENT_NAME_REQUIRED" -> new PatientNameRequiredException();
            case "INVALID_SLOT" -> new InvalidSlotException("fuera de agenda");
            case "SLOT_UNAVAILABLE" -> new SlotUnavailableException();
            case "RESERVATION_EXPIRED" -> new ReservationExpiredException();
            case "CATALOG_UNAVAILABLE" -> new CatalogUnavailableException("caído", null);
            default -> new CatedraUnavailableException("caída", null);
        };
        when(reservaService.startReservation(any())).thenThrow(error);

        send(BODY)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.path").value(URL));
    }

    @Test
    void aMismatchWithTheCatedraIsAGenericInternalErrorWithoutDetails() throws Exception {
        when(reservaService.startReservation(any()))
                .thenThrow(new CatedraResponseException("La cátedra rechazó la confirmación con INVALID_SLOT", null));

        send(BODY)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail", not(containsString("INVALID_SLOT"))));
    }

    @Test
    void theErrorsDoNotRevealWhoOrWhat() throws Exception {
        when(reservaService.startReservation(any())).thenThrow(new InvalidSlotException("El horario ya pasó"));

        send(BODY)
                .andExpect(jsonPath("$.detail", not(containsString("42"))))
                .andExpect(jsonPath("$.detail", not(containsString("hold"))));
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get(URL + query).with(jwt().jwt(token -> token.claim("uid", "42"))));
    }

    @Test
    void listsTheReservationsOfTheUserWithTheTotalAndTheLinks() throws Exception {
        PageQuery page = new PageQuery(1, 2);
        when(reservaService.listReservations("42", ReservationStatus.WAITING_PHONE, page))
                .thenReturn(new PagedResult<>(List.of(waitingForPhone()), 5, page));

        list("?status=WAITING_PHONE&page=1&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("Link", containsString("page=2&size=2>; rel=\"next\"")))
                .andExpect(header().string("Link", containsString("status=WAITING_PHONE")));
    }

    @Test
    void withoutParametersItAsksForTheFirstPageOf20AndNoStatus() throws Exception {
        PageQuery page = new PageQuery(0, 20);
        when(reservaService.listReservations("42", null, page)).thenReturn(new PagedResult<>(List.of(), 0, page));

        list("").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)))
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void theSortOfTheRequestDoesNotChangeTheFixedOrder() throws Exception {
        PageQuery page = new PageQuery(0, 20);
        when(reservaService.listReservations("42", null, page)).thenReturn(new PagedResult<>(List.of(), 0, page));

        list("?sort=status,asc").andExpect(status().isOk());

        verify(reservaService).listReservations("42", null, page);
    }

    @Test
    void aStatusThatDoesNotExistIsAValidationError() throws Exception {
        list("?status=NADA").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(reservaService);
    }

    @Test
    void theListRequiresAToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservaService);
    }

    @Test
    void getsOneReservationOfTheUser() throws Exception {
        when(reservaService.getReservation(101L, "42")).thenReturn(waitingForPhone());

        list("/101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.holdId").doesNotExist());
    }

    @Test
    void aMissingOrForeignReservationIsAnIndistinguishableNotFound() throws Exception {
        when(reservaService.getReservation(101L, "42")).thenThrow(new ReservationProcessNotFoundException(101L));

        list("/101")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(URL + "/101"))
                .andExpect(jsonPath("$.detail", not(containsString("101"))));
    }

    @Test
    void aReservationIdThatIsNotANumberIsABadRequest() throws Exception {
        list("/abc").andExpect(status().isBadRequest());
        verifyNoInteractions(reservaService);
    }

    @Test
    void getRequiresAToken() throws Exception {
        mockMvc.perform(get(URL + "/101")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservaService);
    }

    private ResultActions cancel(String body) throws Exception {
        var request = post(URL + "/101/cancel").with(jwt().jwt(token -> token.claim("uid", "42")));
        return mockMvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void cancelsWithTheReasonOfTheBody() throws Exception {
        ReservationProcess cancelled = waitingForPhone();
        cancelled.setStatus(ReservationStatus.CANCELLED);
        cancelled.setCancellationReason("no puedo ir");
        when(reservaService.cancelReservation(101L, "42", "no puedo ir")).thenReturn(cancelled);

        cancel("{\"reason\": \"no puedo ir\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("no puedo ir"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void cancelsWithoutABody() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null)).thenReturn(waitingForPhone());

        cancel(null).andExpect(status().isOk());

        verify(reservaService).cancelReservation(101L, "42", null);
    }

    @Test
    void cancelsWithAnEmptyObject() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null)).thenReturn(waitingForPhone());

        cancel("{}").andExpect(status().isOk());

        verify(reservaService).cancelReservation(101L, "42", null);
    }

    @Test
    void aReasonOver500CharactersIsAValidationError() throws Exception {
        cancel("{\"reason\": \"" + "a".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        verifyNoInteractions(reservaService);
    }

    @Test
    void aReasonOf500CharactersIsAccepted() throws Exception {
        String reason = "a".repeat(500);
        when(reservaService.cancelReservation(101L, "42", reason)).thenReturn(waitingForPhone());

        cancel("{\"reason\": \"" + reason + "\"}").andExpect(status().isOk());
    }

    @Test
    void cancelRequiresAToken() throws Exception {
        mockMvc.perform(post(URL + "/101/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservaService);
    }

    @Test
    void cancellingAMissingOrForeignReservationIsANotFound() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null)).thenThrow(new ReservationProcessNotFoundException(101L));

        cancel(null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void aReservationThatCannotBeCancelledIsAConflict() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null))
                .thenThrow(new ReservationNotCancellableException(101L, ReservationStatus.WAITING_PHONE));

        cancel(null)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"))
                .andExpect(jsonPath("$.detail", not(containsString("WAITING_PHONE"))));
    }

    // A33: el choque de versión que agota los reintentos es un 409 con su propio código y no un 500
    @Test
    void cancellingIsLoggedAsAnAction() throws Exception {
        ReservationProcess cancelled = waitingForPhone();
        when(reservaService.cancelReservation(101L, "42", null)).thenReturn(cancelled);

        cancel(null).andExpect(status().isOk());

        verify(reservationActionLogger).cancelled(cancelled);
    }

    @Test
    void aCancellationThatFailsIsNotLoggedAsAnAction() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null))
                .thenThrow(new ReservationNotCancellableException(101L, ReservationStatus.WAITING_PHONE));

        cancel(null).andExpect(status().isConflict());

        verifyNoInteractions(reservationActionLogger);
    }

    @Test
    void aVersionConflictThatExhaustsTheRetriesWhileCancellingIsAConflict() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null))
                .thenThrow(new ReservationConflictException(101L, new RuntimeException("versión")));

        cancel(null)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"))
                .andExpect(jsonPath("$.detail", not(containsString("versión"))));
    }

    @Test
    void ifTheCatedraIsDownCancellingIsServiceUnavailable() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null))
                .thenThrow(new CatedraUnavailableException("caída", null));

        cancel(null).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));
    }

    @Test
    void aMismatchWithTheCatedraWhileCancellingIsAGenericInternalError() throws Exception {
        when(reservaService.cancelReservation(101L, "42", null))
                .thenThrow(new CatedraResponseException("La cátedra rechazó la cancelación con RESERVATION_NOT_FOUND", null));

        cancel(null).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail", not(containsString("RESERVATION_NOT_FOUND"))));
    }

    private ResultActions submitPhone(String body) throws Exception {
        var request = post(URL + "/101/phone").with(jwt().jwt(token -> token.claim("uid", "42")));
        return mockMvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ReservationProcess queued() {
        ReservationProcess process = waitingForPhone();
        process.setStatus(ReservationStatus.PHONE_QUEUED);
        process.setRequestEventId("request-event-8a31");
        process.setPatientPhone("+5492615551234");
        return process;
    }

    @Test
    void receivingThePhoneIsLoggedAsAnAction() throws Exception {
        ReservationProcess queued = queued();
        when(reservaService.submitPhone(101L, "42", "+5492615551234")).thenReturn(queued);

        submitPhone("{\"phoneNumber\": \"+5492615551234\"}").andExpect(status().isAccepted());

        verify(reservationActionLogger).phoneReceived(queued);
    }

    @Test
    void aPhoneThatIsRejectedIsNotLoggedAsAnAction() throws Exception {
        when(reservaService.submitPhone(101L, "42", "+5492615551234"))
                .thenThrow(new InvalidReservationStateException(101L, ReservationStatus.PHONE_QUEUED));

        submitPhone("{\"phoneNumber\": \"+5492615551234\"}").andExpect(status().isConflict());

        verifyNoInteractions(reservationActionLogger);
    }

    @Test
    void aVersionConflictThatExhaustsTheRetriesWhileSubmittingThePhoneIsAConflict() throws Exception {
        when(reservaService.submitPhone(101L, "42", "+5492615551234"))
                .thenThrow(new ReservationConflictException(101L, new RuntimeException("versión")));

        submitPhone("{\"phoneNumber\": \"+5492615551234\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
    }

    @Test
    void submittingThePhoneAnswersAcceptedWithTheReservationQueued() throws Exception {
        when(reservaService.submitPhone(101L, "42", "+5492615551234")).thenReturn(queued());

        submitPhone("{\"phoneNumber\": \"+5492615551234\"}")
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.status").value("PHONE_QUEUED"))
                .andExpect(jsonPath("$.patientPhone").value("+5492615551234"))
                .andExpect(jsonPath("$.canSubmitPhone").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.requestEventId").doesNotExist());
    }

    // El usuario sale del token y el teléfono viaja tal cual lo escribió: lo normaliza el caso de uso
    @Test
    void thePhoneTravelsAsTypedAndTheUserComesFromTheToken() throws Exception {
        when(reservaService.submitPhone(101L, "42", "+54 9 261 615-9550")).thenReturn(queued());

        submitPhone("{\"phoneNumber\": \"+54 9 261 615-9550\", \"userId\": \"99\"}")
                .andExpect(status().isAccepted());

        verify(reservaService).submitPhone(101L, "42", "+54 9 261 615-9550");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            +5492616159550
            +542616159550
            2616159550
            (0261) 615.9550
            +54 9 261 615-9550
            1234567
            123456789012345
            """)
    void acceptsTheFormatsPeopleUse(String phone) throws Exception {
        when(reservaService.submitPhone(101L, "42", phone)).thenReturn(queued());

        submitPhone("{\"phoneNumber\": \"" + phone + "\"}").andExpect(status().isAccepted());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            123456
            1234567890123456
            261-ABC-9550
            54+92616159550
            261/615/9550
            """)
    void anInvalidPhoneIsAValidationErrorOnThatField(String phone) throws Exception {
        submitPhone("{\"phoneNumber\": \"" + phone + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phoneNumber"));
        verifyNoInteractions(reservaService);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}
            {"phoneNumber": null}
            {"phoneNumber": ""}
            {"phoneNumber": "   "}
            """)
    void aMissingOrBlankPhoneIsAValidationError(String body) throws Exception {
        submitPhone(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("phoneNumber"))));
        verifyNoInteractions(reservaService);
    }

    @Test
    void aPhoneOverTheInputLimitIsAValidationError() throws Exception {
        submitPhone("{\"phoneNumber\": \"" + "1".repeat(41) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'phoneNumber')]").exists());
        verifyNoInteractions(reservaService);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"phoneNumber": ["2616159550"]}
            not-json
            """)
    void aBodyThatCannotBeReadIsAValidationErrorWhenSubmittingThePhone(String body) throws Exception {
        submitPhone(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(reservaService);
    }

    @Test
    void submittingThePhoneWithoutABodyIsABadRequest() throws Exception {
        submitPhone(null).andExpect(status().isBadRequest());
        verifyNoInteractions(reservaService);
    }

    @Test
    void submittingThePhoneRequiresAToken() throws Exception {
        mockMvc.perform(post(URL + "/101/phone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"2616159550\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservaService);
    }

    @Test
    void submittingThePhoneOfAMissingOrForeignReservationIsANotFound() throws Exception {
        when(reservaService.submitPhone(101L, "42", "2616159550"))
                .thenThrow(new ReservationProcessNotFoundException(101L));

        submitPhone("{\"phoneNumber\": \"2616159550\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    // Incluye que la cátedra todavía no pidió el teléfono
    @Test
    void aReservationThatCannotReceiveThePhoneIsAConflictThatDoesNotRevealTheStatus() throws Exception {
        when(reservaService.submitPhone(101L, "42", "2616159550"))
                .thenThrow(new InvalidReservationStateException(101L, ReservationStatus.CONFIRMED));

        submitPhone("{\"phoneNumber\": \"2616159550\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"))
                .andExpect(jsonPath("$.detail", not(containsString("CONFIRMED"))));
    }

    // Mismo código que el hold vencido, con su propio mensaje: acá lo que venció es el tiempo para el teléfono
    @Test
    void anExpiredReservationIsAConflictWithItsOwnMessage() throws Exception {
        when(reservaService.submitPhone(101L, "42", "2616159550")).thenThrow(new PhoneWindowExpiredException(101L));

        submitPhone("{\"phoneNumber\": \"2616159550\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"))
                .andExpect(jsonPath("$.detail", containsString("teléfono")))
                .andExpect(jsonPath("$.detail", not(containsString("bloqueo"))));
    }

    @Test
    void theReservationTellsTheAppWhenThePhoneCanBeSubmitted() throws Exception {
        ReservationProcess asked = waitingForPhone();
        asked.setRequestEventId("request-event-8a31");
        when(reservaService.getReservation(101L, "42")).thenReturn(asked);

        mockMvc.perform(get(URL + "/101").with(jwt().jwt(token -> token.claim("uid", "42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.canSubmitPhone").value(true));
    }

    @Test
    void theReservationTellsTheAppWhenThePhoneCannotBeSubmittedYet() throws Exception {
        when(reservaService.getReservation(101L, "42")).thenReturn(waitingForPhone());

        mockMvc.perform(get(URL + "/101").with(jwt().jwt(token -> token.claim("uid", "42"))))
                .andExpect(jsonPath("$.status").value("WAITING_PHONE"))
                .andExpect(jsonPath("$.canSubmitPhone").value(false));
    }
}
