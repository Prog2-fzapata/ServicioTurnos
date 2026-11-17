package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.ReservationActionLogger;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse.ReservationStreamRegistry;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler.ReservaExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.ReservationDtoMapper;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.ProblemDetailsSecurityHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.security.SecurityConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.web.GlobalExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import lombok.RequiredArgsConstructor;

@WebMvcTest({ReservationStreamController.class, ReservationController.class})
@Import({ReservationActionLogger.class, ReservationDtoMapper.class, ReservaExceptionHandler.class, GlobalExceptionHandler.class, ProblemDetailsFactory.class, SecurityConfiguration.class,
        JwtConfiguration.class, ProblemDetailsSecurityHandler.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationStreamControllerTest {

    private static final String URL = "/api/reservations/stream";
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-04T14:00:00Z");

    private final MockMvc mockMvc;

    @MockitoBean
    private ReservationStreamRegistry registry;

    @MockitoBean
    private ReservaService reservaService;

    @Test
    void opensTheStreamForTheUserOfTheTokenUntilItExpires() throws Exception {
        when(registry.register("42", EXPIRES_AT)).thenReturn(new SseEmitter());

        mockMvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM)
                .with(jwt().jwt(token -> token.claim("uid", "42").expiresAt(EXPIRES_AT))))
                .andExpect(request().asyncStarted());

        verify(registry).register("42", EXPIRES_AT);
    }

    @Test
    void withoutATokenItIsUnauthorized() throws Exception {
        mockMvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());

        verifyNoInteractions(registry);
    }

    // Con ReservationController en el mismo contexto: /stream no debe tomarse por el id de una reserva
    @Test
    void theFixedPathIsNotTakenForAReservationId() throws Exception {
        when(registry.register("42", EXPIRES_AT)).thenReturn(new SseEmitter());

        mockMvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM)
                .with(jwt().jwt(token -> token.claim("uid", "42").expiresAt(EXPIRES_AT))))
                .andExpect(request().asyncStarted());

        verifyNoInteractions(reservaService);
    }
}
