package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import lombok.RequiredArgsConstructor;

// Una base de datos que de verdad no responde (un puerto cerrado), sin Testcontainers: el servicio arranca igual y
// cada pedido que la necesita falla rápido con 503, no con un 500 genérico ni a los 30 s por defecto del pool
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:9/turnos",
        "spring.datasource.username=nadie",
        "spring.datasource.password=nada",
        // Que el contexto arranque sin base: sin migraciones, sin leer el esquema y sin exigir conexión al crear el pool
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        // Corto para no alargar la prueba; el valor por defecto lo cubre otro test
        "spring.datasource.hikari.connection-timeout=500"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class DatabaseDownIntegrationTest {

    private static final Duration FAST = Duration.ofSeconds(5);

    private final MockMvc mockMvc;

    private ResultActions asUser(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.claim("uid", "42"))));
    }

    private void expectDatabaseUnavailable(ResultActions result) throws Exception {
        result.andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
    }

    @Test
    void listingReservationsAnswers503() throws Exception {
        expectDatabaseUnavailable(asUser(get("/api/reservations")));
    }

    @Test
    void readingOneReservationAnswers503() throws Exception {
        expectDatabaseUnavailable(asUser(get("/api/reservations/1")));
    }

    @Test
    void sendingThePhoneAnswers503() throws Exception {
        expectDatabaseUnavailable(asUser(post("/api/reservations/1/phone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"+5492615551234\"}")));
    }

    // Sin conexión disponible falla en el tiempo del pool (500 ms acá) y no a los 30 s por defecto
    @Test
    void itFailsFastInsteadOfWaitingForTheDefaultPoolTimeout() throws Exception {
        long start = System.nanoTime();

        expectDatabaseUnavailable(asUser(get("/api/reservations")));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(FAST);
    }

    // El stream no consulta la base y el JWT se valida sin consultar a nadie: abre aunque no haya nada que avisar
    @Test
    void theChangeStreamStillOpens() throws Exception {
        asUser(get("/api/reservations/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    // El estado del servicio también dice la verdad
    @Test
    void theHealthEndpointReportsDown() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
