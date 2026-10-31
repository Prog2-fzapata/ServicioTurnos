package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.cutConnection;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.slow;
import static ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.FakeHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogSchedule;
import ar.edu.um.prog2.fzapata.turnos.shared.config.CatalogClientConfiguration;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtPropagationInterceptor;

// El adapter con su configuración real (propiedades, timeouts, reintentos e interceptor) contra un catálogo simulado
class CatalogClientAdapterTest {

    private static final String USER_TOKEN = "jwt-del-usuario-que-no-debe-filtrarse";
    private static final String PROFESSIONAL_JSON = """
            {
              "id": 15, "categoryId": 1, "categoryName": "Clinica medica", "firstName": "Ana", "lastName": "Gomez",
              "enabled": true,
              "weeklySchedules": [
                {"id": 101, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
                 "slotDurationMinutes": 30, "enabled": true},
                {"id": 102, "dayOfWeek": "TUESDAY", "startTime": "14:00:00", "endTime": "18:30:00",
                 "slotDurationMinutes": 20, "enabled": false}
              ]
            }""";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogClientConfiguration.class, JwtPropagationInterceptor.class,
                    CatalogClientAdapter.class, CatalogProfessionalMapper.class);

    private FakeHttpServer server;

    @BeforeEach
    void startFakeCatalogAndLogIn() throws Exception {
        server = new FakeHttpServer();
        Jwt jwt = Jwt.withTokenValue(USER_TOKEN).header("alg", "HS512").claim("uid", "42").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void stopFakeCatalogAndLogOut() {
        server.close();
        SecurityContextHolder.clearContext();
    }

    // Tiempos cortos para que los tests no esperen: 3 intentos, 50 ms antes del primer reintento (luego 100 ms)
    private void withAdapter(String maxAttempts, Consumer<CatalogClientAdapter> test) {
        runner.withPropertyValues("catalogo.base-url=" + server.baseUrl(), "catalogo.connect-timeout=500ms",
                "catalogo.read-timeout=300ms", "catalogo.max-attempts=" + maxAttempts, "catalogo.initial-backoff=50ms")
                .run(context -> test.accept(context.getBean(CatalogClientAdapter.class)));
    }

    private void withAdapter(Consumer<CatalogClientAdapter> test) {
        withAdapter("3", test);
    }

    @Test
    void returnsTheProfessionalWithEveryScheduleIncludingTheDisabledOnes() {
        server.thenRespond(status(200, PROFESSIONAL_JSON));

        withAdapter(adapter -> {
            CatalogProfessional professional = adapter.findProfessional(15L).orElseThrow();

            assertThat(professional.getId()).isEqualTo(15L);
            assertThat(professional.getCategoryId()).isEqualTo(1L);
            assertThat(professional.getCategoryName()).isEqualTo("Clinica medica");
            assertThat(professional.getFirstName()).isEqualTo("Ana");
            assertThat(professional.getLastName()).isEqualTo("Gomez");
            assertThat(professional.isEnabled()).isTrue();
            assertThat(professional.getWeeklySchedules()).hasSize(2);
            CatalogSchedule monday = professional.getWeeklySchedules().get(0);
            assertThat(monday.getId()).isEqualTo(101L);
            assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(monday.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(monday.getEndTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(monday.getSlotDurationMinutes()).isEqualTo(30);
            assertThat(monday.isEnabled()).isTrue();
            CatalogSchedule tuesday = professional.getWeeklySchedules().get(1);
            assertThat(tuesday.getEndTime()).isEqualTo(LocalTime.of(18, 30));
            assertThat(tuesday.isEnabled()).isFalse();
            assertThat(server.paths()).containsExactly("/api/professionals/15");
        });
    }

    @Test
    void ignoresTheFieldsThatTheContractDoesNotDefine() {
        server.thenRespond(status(200, """
                {"id": 15, "categoryId": 1, "categoryName": "C", "firstName": "Ana", "lastName": "G",
                 "enabled": true, "campoNuevo": {"x": 1}, "weeklySchedules": [
                 {"id": 1, "dayOfWeek": "FRIDAY", "startTime": "08:00:00", "endTime": "09:00:00",
                  "slotDurationMinutes": 15, "enabled": true, "otroCampo": 7}]}"""));

        withAdapter(adapter -> assertThat(adapter.findProfessional(15L)).isPresent());
    }

    @Test
    void aProfessionalWithoutSchedulesHasAnEmptyList() {
        server.thenRespond(status(200, """
                {"id": 15, "categoryId": 1, "categoryName": "C", "firstName": "Ana", "lastName": "G",
                 "enabled": false, "weeklySchedules": []}"""));

        withAdapter(adapter -> {
            CatalogProfessional professional = adapter.findProfessional(15L).orElseThrow();

            assertThat(professional.isEnabled()).isFalse();
            assertThat(professional.getWeeklySchedules()).isEmpty();
        });
    }

    @Test
    void forwardsTheJwtOfTheCurrentUser() {
        server.thenRespond(status(200, PROFESSIONAL_JSON));

        withAdapter(adapter -> {
            adapter.findProfessional(15L);

            assertThat(server.authorizations()).containsExactly("Bearer " + USER_TOKEN);
        });
    }

    @Test
    void aMissingProfessionalIsEmptyAndIsNotRetried() {
        server.thenRespond(status(404, "{\"code\":\"PROFESSIONAL_NOT_FOUND\"}"));

        withAdapter(adapter -> {
            assertThat(adapter.findProfessional(99L)).isEqualTo(Optional.empty());
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void recoversWhenCatalogFailsWithA5xxAndThenAnswers() {
        server.thenRespond(status(503, ""), status(500, ""), status(200, PROFESSIONAL_JSON));

        withAdapter(adapter -> {
            assertThat(adapter.findProfessional(15L)).isPresent();
            assertThat(server.requestCount()).isEqualTo(3);
        });
    }

    @Test
    void recoversWhenTheConnectionIsCutOnce() {
        server.thenRespond(cutConnection(), status(200, PROFESSIONAL_JSON));

        withAdapter(adapter -> {
            assertThat(adapter.findProfessional(15L)).isPresent();
            assertThat(server.requestCount()).isEqualTo(2);
        });
    }

    @Test
    void givesUpAfterExactlyTheConfiguredAttempts() {
        server.thenRespond(status(503, ""));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(CatalogUnavailableException.class)
                    .hasMessageContaining("503");
            assertThat(server.requestCount()).isEqualTo(3);
        });
    }

    @Test
    void aTimeoutIsUnavailableAfterTheAttempts() {
        server.thenRespond(slow(1_000));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(CatalogUnavailableException.class);
            assertThat(server.requestCount()).isEqualTo(3);
        });
    }

    @Test
    void aServerThatIsDownIsUnavailable() {
        String baseUrl = server.baseUrl();
        server.close();

        runner.withPropertyValues("catalogo.base-url=" + baseUrl, "catalogo.connect-timeout=500ms",
                "catalogo.read-timeout=300ms", "catalogo.max-attempts=2", "catalogo.initial-backoff=10ms")
                .run(context -> assertThatThrownBy(() -> context.getBean(CatalogClientAdapter.class)
                        .findProfessional(15L)).isInstanceOf(CatalogUnavailableException.class));
    }

    // Esperas de 50 ms y luego 100 ms: si la espera no se duplicara, serían 100 ms en total
    @Test
    void waitsLongerBeforeEachRetry() {
        server.thenRespond(status(500, ""));

        withAdapter(adapter -> {
            long start = System.nanoTime();
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(CatalogUnavailableException.class);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertThat(elapsedMillis).isGreaterThanOrEqualTo(145);
        });
    }

    @Test
    void oneAttemptDisablesTheRetries() {
        server.thenRespond(status(500, ""));

        withAdapter("1", adapter -> {
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(CatalogUnavailableException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    // Un 4xx no mejora repitiendo el pedido, y delata un desajuste (por ejemplo, un secreto distinto)
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409})
    void anUnexpected4xxIsAResponseErrorAndIsNotRetried(int httpStatus) {
        server.thenRespond(status(httpStatus, "{\"detail\":\"cuerpo-secreto-de-catalogo\"}"));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(CatalogResponseException.class)
                    .hasMessageContaining(String.valueOf(httpStatus))
                    .hasMessageNotContaining("cuerpo-secreto").hasMessageNotContaining(USER_TOKEN);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void anUnreadableBodyIsAResponseErrorAndIsNotRetried() {
        server.thenRespond(status(200, "esto no es json"));

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(CatalogResponseException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        });
    }

    @Test
    void anEmptyBodyIsAResponseError() {
        server.thenRespond(status(200, ""));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findProfessional(15L))
                .isInstanceOf(CatalogResponseException.class));
    }

    @Test
    void withoutAnAuthenticatedUserNoRequestIsMade() {
        SecurityContextHolder.clearContext();

        withAdapter(adapter -> {
            assertThatThrownBy(() -> adapter.findProfessional(15L)).isInstanceOf(IllegalStateException.class);
            assertThat(server.requestCount()).isZero();
        });
    }

    // El cuerpo de un 5xx puede traer cualquier cosa (trazas, datos internos): no llega al mensaje ni al cliente
    @Test
    void theExceptionOfAnUnavailableCatalogCarriesNeitherTheBodyNorTheJwt() {
        server.thenRespond(status(500, "{\"detail\":\"cuerpo-secreto " + USER_TOKEN + "\"}"));

        withAdapter(adapter -> assertThatThrownBy(() -> adapter.findProfessional(15L))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageNotContaining("cuerpo-secreto").hasMessageNotContaining(USER_TOKEN));
    }
}
