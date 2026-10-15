package ar.edu.um.prog2.fzapata.turnos.shared.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.ProblemDetailsSecurityHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.security.SecurityConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.web.GlobalExceptionHandlerTest.ErrorProbeController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@WebMvcTest(ErrorProbeController.class)
@Import({ErrorProbeController.class, SecurityConfiguration.class, JwtConfiguration.class,
        ProblemDetailsSecurityHandler.class, ProblemDetailsFactory.class, GlobalExceptionHandler.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class GlobalExceptionHandlerTest {

    record Payload(@NotBlank(message = "El nombre es obligatorio") String name) {
    }

    @RestController
    static class ErrorProbeController {

        @PostMapping("/api/probe/validated")
        String validated(@Valid @RequestBody Payload payload) {
            return payload.name();
        }

        @GetMapping("/api/probe/boom")
        String boom() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }
    }

    private final MockMvc mockMvc;

    private RequestPostProcessor loggedIn() {
        return jwt();
    }

    @Test
    void invalidBodyIsReportedWithTheFieldViolations() throws Exception {
        mockMvc.perform(post("/api/probe/validated").with(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("El nombre es obligatorio"));
    }

    @Test
    void unreadableBodyIsReportedAsValidationError() throws Exception {
        mockMvc.perform(post("/api/probe/validated").with(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("El cuerpo de la solicitud no es un JSON válido"));
    }

    @Test
    void unexpectedErrorHidesTheInternalMessage() throws Exception {
        mockMvc.perform(get("/api/probe/boom").with(loggedIn()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Error interno del servidor"))
                .andExpect(jsonPath("$.path").value("/api/probe/boom"));
    }

    @Test
    void unknownPathGetsTheStandardNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/no-existe").with(loggedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/no-existe"));
    }

    @Test
    void wrongMethodGetsTheStandardMethodNotAllowedProblem() throws Exception {
        mockMvc.perform(get("/api/probe/validated").with(loggedIn()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
