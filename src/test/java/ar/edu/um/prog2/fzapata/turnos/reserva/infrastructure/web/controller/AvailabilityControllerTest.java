package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Slot;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler.ReservaExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.AvailabilityDtoMapper;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.AvailabilityQueryMapper;
import ar.edu.um.prog2.fzapata.turnos.shared.config.AvailabilityConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.ProblemDetailsSecurityHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.security.SecurityConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.web.GlobalExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import lombok.RequiredArgsConstructor;

@WebMvcTest(AvailabilityController.class)
@Import({AvailabilityDtoMapper.class, AvailabilityQueryMapper.class, AvailabilityConfiguration.class,
        ReservaExceptionHandler.class, GlobalExceptionHandler.class, ProblemDetailsFactory.class,
        SecurityConfiguration.class, JwtConfiguration.class, ProblemDetailsSecurityHandler.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AvailabilityControllerTest {

    private static final String URL = "/api/availability";

    private final MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    private Availability availability() {
        return Availability.builder().professionalId(15L).from(LocalDate.of(2026, 10, 12))
                .to(LocalDate.of(2026, 10, 25))
                .slots(List.of(
                        Slot.builder().date(LocalDate.of(2026, 10, 12)).startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(9, 30)).build(),
                        Slot.builder().date(LocalDate.of(2026, 10, 12)).startTime(LocalTime.of(10, 0))
                                .endTime(LocalTime.of(10, 30)).build()))
                .build();
    }

    private ResultActions ask(String professionalId, String from, String to)
            throws Exception {
        return mockMvc.perform(get(URL).with(jwt()).param("professionalId", professionalId).param("from", from)
                .param("to", to));
    }

    @Test
    void answersWithTheSlotsInTheFormatOfTheContract() throws Exception {
        when(reservaService.getAvailability(any())).thenReturn(availability());

        ask("15", "2026-10-12", "2026-10-25")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.from").value("2026-10-12"))
                .andExpect(jsonPath("$.to").value("2026-10-25"))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].date").value("2026-10-12"))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("09:30:00"))
                .andExpect(jsonPath("$.slots[1].startTime").value("10:00:00"));
        verify(reservaService).getAvailability(
                new AvailabilityQuery(15L, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 25)));
    }

    @Test
    void anEmptyAvailabilityIsAnEmptyListAndNotAnError() throws Exception {
        when(reservaService.getAvailability(any())).thenReturn(Availability.builder().professionalId(15L)
                .from(LocalDate.of(2026, 10, 12)).to(LocalDate.of(2026, 10, 12)).slots(List.of()).build());

        ask("15", "2026-10-12", "2026-10-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(get(URL).param("professionalId", "15").param("from", "2026-10-12")
                .param("to", "2026-10-12"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservaService);
    }

    @Test
    void aMissingParameterIsA400() throws Exception {
        mockMvc.perform(get(URL).with(jwt()).param("from", "2026-10-12").param("to", "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(get(URL).with(jwt()).param("professionalId", "15").param("to", "2026-10-12"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).with(jwt()).param("professionalId", "15").param("from", "2026-10-12"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservaService);
    }

    @Test
    void aParameterOfTheWrongTypeIsA400() throws Exception {
        ask("abc", "2026-10-12", "2026-10-12").andExpect(status().isBadRequest());
        ask("15", "12/10/2026", "2026-10-12").andExpect(status().isBadRequest());
        ask("15", "2026-10-12", "mañana").andExpect(status().isBadRequest());
        verifyNoInteractions(reservaService);
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRejectedWithoutCallingTheService() throws Exception {
        ask("15", "2026-10-13", "2026-10-12")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.path").value(URL));
        verifyNoInteractions(reservaService);
    }

    @Test
    void aRangeLongerThanTheMaximumIsRejectedWithoutCallingTheService() throws Exception {
        ask("15", "2026-10-01", "2026-10-31").andExpect(status().isOk());
        ask("15", "2026-10-01", "2026-11-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.detail").value("El rango no puede superar los 31 días"));
    }

    @Test
    void aMissingProfessionalIsA404() throws Exception {
        when(reservaService.getAvailability(any())).thenThrow(new ProfessionalNotFoundException(99L));

        ask("99", "2026-10-12", "2026-10-12")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
    }

    @Test
    void aDisabledProfessionalOrCategoryIsA400WithTheSameCode() throws Exception {
        when(reservaService.getAvailability(any()))
                .thenThrow(new ProfessionalDisabledException("La categoría del profesional 15 está deshabilitada"));

        ask("15", "2026-10-12", "2026-10-12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"))
                .andExpect(jsonPath("$.detail").value("El profesional no está habilitado"));
    }

    @Test
    void anUnavailableCatalogOrCatedraIsA503() throws Exception {
        when(reservaService.getAvailability(any())).thenThrow(new CatalogUnavailableException("caído", null))
                .thenThrow(new CatedraUnavailableException("caída", null));

        ask("15", "2026-10-12", "2026-10-12")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        ask("15", "2026-10-12", "2026-10-12")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));
    }
}
