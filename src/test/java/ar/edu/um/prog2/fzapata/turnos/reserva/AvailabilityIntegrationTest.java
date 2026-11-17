package ar.edu.um.prog2.fzapata.turnos.reserva;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.cutConnection;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer;
import lombok.RequiredArgsConstructor;

// El flujo completo (seguridad, controller, caso de uso, clientes HTTP y manejo de errores) contra un catálogo y una
// cátedra simulados que son servidores HTTP reales. "Hoy" es el lunes 12/10/2026 a las 10:10 en Mendoza
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AvailabilityIntegrationTest.FixedClock.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AvailabilityIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-12T13:10:00Z"), ZoneId.of("America/Argentina/Mendoza"));
        }
    }

    private static final FakeHttpServer CATALOG = start();
    private static final FakeHttpServer CATEDRA = start();

    private static final String PROFESSIONAL = """
            {"id": 15, "categoryId": 1, "categoryName": "Clinica medica", "categoryEnabled": true,
             "firstName": "Ana", "lastName": "Gomez", "enabled": true,
             "weeklySchedules": [
               {"id": 1, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
                "slotDurationMinutes": 30, "enabled": true}]}""";

    private static final String OCCUPANCIES = """
            {"professionalId": 15, "from": "2026-10-19", "to": "2026-10-19", "occupancies": [
              {"date": "2026-10-19", "startTime": "09:30:00", "status": "HELD", "expiresAt": "2026-10-19T12:44:50Z"},
              {"date": "2026-10-19", "startTime": "10:00:00", "status": "CONFIRMED", "expiresAt": null}]}""";

    private final MockMvc mockMvc;

    private static FakeHttpServer start() {
        try {
            return new FakeHttpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void pointTheClientsToTheFakeServers(DynamicPropertyRegistry registry) {
        registry.add("catalogo.base-url", CATALOG::baseUrl);
        registry.add("catedra.base-url", CATEDRA::baseUrl);
        // Un solo intento: los tests de caída no esperan reintentos
        registry.add("catalogo.max-attempts", () -> "1");
        registry.add("catedra.max-attempts", () -> "1");
    }

    @AfterAll
    static void stopTheFakeServers() {
        CATALOG.close();
        CATEDRA.close();
    }

    @BeforeEach
    void resetTheFakeServers() {
        CATALOG.reset();
        CATEDRA.reset();
    }

    private ResultActions ask(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/availability").with(jwt()).param("professionalId", "15")
                .param("from", from).param("to", to));
    }

    @Test
    void offersTheAgendaMinusTheOccupanciesOfTheCatedra() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL));
        CATEDRA.thenRespond(status(200, OCCUPANCIES));

        ask("2026-10-19", "2026-10-19")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.slots.length()").value(4))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("09:30:00"))
                .andExpect(jsonPath("$.slots[1].startTime").value("10:30:00"))
                .andExpect(jsonPath("$.slots[2].startTime").value("11:00:00"))
                .andExpect(jsonPath("$.slots[3].startTime").value("11:30:00"));
        assertThat(CATALOG.paths()).containsExactly("/api/professionals/15");
        assertThat(CATEDRA.paths()).containsExactly("/api/appointment-occupancies");
        assertThat(CATEDRA.firstRequest().rawQuery()).contains("professionalId=15").contains("from=2026-10-19")
                .contains("to=2026-10-19");
    }

    // Catálogo recibe el JWT del usuario y la cátedra el técnico: mezclarlos filtraría uno hacia el otro servicio
    @Test
    void forwardsTheUserJwtToCatalogAndUsesTheTechnicalOneWithTheCatedra() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL));
        CATEDRA.thenRespond(status(200, OCCUPANCIES));

        ask("2026-10-19", "2026-10-19").andExpect(status().isOk());

        assertThat(CATALOG.authorizations()).containsExactly("Bearer token");
        assertThat(CATEDRA.authorizations()).containsExactly("Bearer jwt-tecnico-solo-para-tests");
    }

    @Test
    void doesNotOfferTheSlotsThatAlreadyStartedToday() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL));
        CATEDRA.thenRespond(status(200, """
                {"professionalId": 15, "from": "2026-10-12", "to": "2026-10-12", "occupancies": []}"""));

        ask("2026-10-12", "2026-10-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].startTime").value("10:30:00"));
    }

    @Test
    void aMissingProfessionalIsA404AndTheCatedraIsNotAsked() throws Exception {
        CATALOG.thenRespond(status(404, "{\"code\":\"PROFESSIONAL_NOT_FOUND\"}"));

        ask("2026-10-19", "2026-10-19")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aProfessionalWhoseCategoryIsDisabledIsBlockedAndTheCatedraIsNotAsked() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL.replace("\"categoryEnabled\": true", "\"categoryEnabled\": false")));

        ask("2026-10-19", "2026-10-19")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aDisabledProfessionalIsBlockedAndTheCatedraIsNotAsked() throws Exception {
        CATALOG.thenRespond(status(200,
                PROFESSIONAL.replace("\"lastName\": \"Gomez\", \"enabled\": true", "\"lastName\": \"Gomez\", \"enabled\": false")));

        ask("2026-10-19", "2026-10-19")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void anInvalidRangeIsRejectedBeforeCallingAnyone() throws Exception {
        ask("2026-10-20", "2026-10-19")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        assertThat(CATALOG.requestCount()).isZero();
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aCatalogThatDoesNotAnswerIsA503() throws Exception {
        CATALOG.thenRespond(cutConnection());

        ask("2026-10-19", "2026-10-19")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        assertThat(CATEDRA.requestCount()).isZero();
    }

    @Test
    void aCatedraThatDoesNotAnswerIsA503AndNoPartialAvailabilityIsGiven() throws Exception {
        CATALOG.thenRespond(status(200, PROFESSIONAL));
        CATEDRA.thenRespond(status(503, ""));

        ask("2026-10-19", "2026-10-19")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"))
                .andExpect(jsonPath("$.slots").doesNotExist());
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(get("/api/availability").param("professionalId", "15").param("from", "2026-10-19")
                .param("to", "2026-10-19"))
                .andExpect(status().isUnauthorized());
        assertThat(CATALOG.requestCount()).isZero();
    }
}
