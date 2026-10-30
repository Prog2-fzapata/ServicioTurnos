package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler.ReservaExceptionHandlerTest.CatalogProbeController;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.ProblemDetailsSecurityHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.security.SecurityConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.web.GlobalExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import lombok.RequiredArgsConstructor;

// El controller de prueba vive en el package del slice reserva, que es el que cubre el handler
@WebMvcTest(CatalogProbeController.class)
@Import({CatalogProbeController.class, ReservaExceptionHandler.class, GlobalExceptionHandler.class,
        ProblemDetailsFactory.class, SecurityConfiguration.class, JwtConfiguration.class,
        ProblemDetailsSecurityHandler.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservaExceptionHandlerTest {

    @RestController
    static class CatalogProbeController {

        @GetMapping("/api/probe/catalog-down")
        String down() {
            throw new CatalogUnavailableException("Catálogo no está disponible (503)", null);
        }

        @GetMapping("/api/probe/catalog-unexpected")
        String unexpected() {
            throw new CatalogResponseException("Catálogo respondió 401", null);
        }
    }

    private final MockMvc mockMvc;

    @Test
    void anUnavailableCatalogIsA503WithItsOwnCode() throws Exception {
        mockMvc.perform(get("/api/probe/catalog-down").with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value("/api/probe/catalog-down"))
                .andExpect(jsonPath("$.detail", not(containsString("503"))));
    }

    // Un desajuste entre servicios no es una caída: sale como error interno y sin detalles
    @Test
    void anUnexpectedAnswerFromCatalogIsAGenericInternalError() throws Exception {
        mockMvc.perform(get("/api/probe/catalog-unexpected").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail", not(containsString("401"))));
    }
}
