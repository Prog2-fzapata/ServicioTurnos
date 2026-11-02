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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PatientNameRequiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
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
}
