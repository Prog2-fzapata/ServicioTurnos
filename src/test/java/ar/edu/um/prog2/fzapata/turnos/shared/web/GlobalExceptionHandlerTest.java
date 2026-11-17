package ar.edu.um.prog2.fzapata.turnos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

        @GetMapping("/api/probe/database-down")
        String databaseDown() {
            throw new DataAccessResourceFailureException("no hay conexión: jdbc:postgresql://secreto:5432/turnos");
        }

        @GetMapping("/api/probe/transaction-down")
        String transactionDown() {
            throw new CannotCreateTransactionException("no se pudo abrir la transacción");
        }

        @GetMapping("/api/probe/constraint")
        String constraint() {
            throw new DuplicateKeyException("clave duplicada en la tabla reservation_processes");
        }

        @GetMapping("/api/probe/boom")
        String boom() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }
    }

    private final MockMvc mockMvc;

    private final Logger loggedClass = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        loggedClass.setLevel(Level.DEBUG);
        appender.start();
        loggedClass.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        loggedClass.detachAppender(appender);
        appender.stop();
    }

    // Los campos de una línea de log (los que salen como campos del JSON)
    private static Map<String, Object> fieldsOf(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        return fields;
    }

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

    // Sin base el cliente debe poder distinguir un fallo que se arregla esperando de un error propio
    @Test
    void aDatabaseThatIsDownIsA503WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/probe/database-down").with(loggedIn()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secreto"))))
                .andExpect(jsonPath("$.path").value("/api/probe/database-down"));
    }

    @Test
    void aTransactionThatCannotBeOpenedIsAlsoA503() throws Exception {
        mockMvc.perform(get("/api/probe/transaction-down").with(loggedIn()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
    }

    // Una restricción violada es un error propio, no una base caída
    @Test
    void anyOtherDataErrorIsStillA500() throws Exception {
        mockMvc.perform(get("/api/probe/constraint").with(loggedIn()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Sin base el log dice qué falló y dónde, con la clase de la excepción y no su mensaje
    @Test
    void aDatabaseThatIsDownIsLoggedWithThePathAndTheClassOfTheException() throws Exception {
        mockMvc.perform(get("/api/probe/database-down").with(loggedIn())).andExpect(status().isServiceUnavailable());

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("La base de datos no está disponible");
            assertThat(fieldsOf(event)).isEqualTo(Map.of("path", "/api/probe/database-down",
                    "exception", "DataAccessResourceFailureException"));
            assertThat(event.getFormattedMessage() + fieldsOf(event) + event.getThrowableProxy())
                    .doesNotContain("secreto");
        });
    }

    // Los errores inesperados sí llevan la excepción entera: son los que hay que diagnosticar
    @Test
    void anUnexpectedErrorIsLoggedWithThePathAndTheWholeException() throws Exception {
        mockMvc.perform(get("/api/probe/boom").with(loggedIn())).andExpect(status().isInternalServerError());

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Error inesperado");
            assertThat(fieldsOf(event)).isEqualTo(Map.of("path", "/api/probe/boom"));
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo("java.lang.IllegalStateException");
        });
    }
}
