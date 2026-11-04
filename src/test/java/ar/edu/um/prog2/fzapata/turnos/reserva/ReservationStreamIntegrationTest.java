package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// El stream de cambios contra PostgreSQL real, con el recorrido completo de cada cambio: pedido HTTP o evento de la
// cátedra, caso de uso, transacción y aviso. MockMvc deja leer lo que el servidor escribe en cada conexión abierta
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReservationStreamIntegrationTest {

    private static final String STREAM = "/api/reservations/stream";
    private static final Instant T0 = Instant.parse("2026-11-04T13:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final ReservaService reservaService;
    private final ReservationProcessRepository repository;
    private final PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM outbox_events").update();
        jdbc.sql("DELETE FROM processed_events").update();
        jdbc.sql("DELETE FROM reservation_processes").update();
    }

    private ReservationProcess seed(String userId, String processId, ReservationStatus status,
            String requestEventId) {
        return repository.save(ReservationProcess.builder().userId(userId).reservationProcessId(processId)
                .holdId("hold-1").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 11, 9)).startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 0)).status(status).requestEventId(requestEventId)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10))).patientFirstName("Juan")
                .patientLastName("Perez").createdAt(T0).updatedAt(T0).build());
    }

    private MvcResult openStream(String userId) throws Exception {
        return mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM).with(jwt().jwt(token -> token
                .claim("uid", userId).expiresAt(Instant.now().plus(Duration.ofHours(1))))))
                .andExpect(request().asyncStarted()).andReturn();
    }

    private static String received(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    private long version(Long id) {
        return jdbc.sql("SELECT version FROM reservation_processes WHERE id = :id").param("id", id)
                .query(Long.class).single();
    }

    @Test
    void aChangeOfStateReachesTheStreamOfItsOwnerWithTheSavedVersion() throws Exception {
        ReservationProcess held = seed("42", "process-1", ReservationStatus.HELD, null);
        MvcResult mine = openStream("42");
        MvcResult someoneElses = openStream("99");

        reservaService.applyTransition(held.getId(), ReservationTransition.to(ReservationStatus.WAITING_PHONE));

        assertThat(received(mine)).contains("event:reservation-updated", "id:" + held.getId() + "-"
                + version(held.getId()), "\"status\":\"WAITING_PHONE\"", "\"canSubmitPhone\":false");
        assertThat(received(someoneElses)).doesNotContain("reservation-updated");
    }

    // El pedido de teléfono llega con el proceso ya en WAITING_PHONE: el estado no cambia, canSubmitPhone sí
    @Test
    void thePhoneRequestOfTheCatedraFlipsCanSubmitPhoneAfterTheCommit() throws Exception {
        ReservationProcess waiting = seed("42", "process-1", ReservationStatus.WAITING_PHONE, null);
        MvcResult stream = openStream("42");

        reservaService.processEvent(new CatedraEvent("request-event-8a31", "AdditionalInformationRequested",
                "process-1", null, null, null));

        assertThat(received(stream)).contains("id:" + waiting.getId() + "-" + version(waiting.getId()),
                "\"status\":\"WAITING_PHONE\"", "\"canSubmitPhone\":true");
    }

    // Dentro de la transacción del evento el cambio todavía no se ve desde afuera: avisar antes sería adelantarse
    @Test
    void noEventGoesOutUntilTheTransactionCommits() throws Exception {
        seed("42", "process-1", ReservationStatus.WAITING_PHONE, null);
        MvcResult stream = openStream("42");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        StringBuilder insideTheTransaction = new StringBuilder();

        outer.executeWithoutResult(status -> {
            reservaService.processEvent(new CatedraEvent("request-event-8a31", "AdditionalInformationRequested",
                    "process-1", null, null, null));
            try {
                insideTheTransaction.append(received(stream));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(insideTheTransaction.toString()).doesNotContain("reservation-updated");
        assertThat(received(stream)).contains("reservation-updated");
    }

    @Test
    void aRolledBackChangeNeverReachesTheStream() throws Exception {
        ReservationProcess waiting = seed("42", "process-1", ReservationStatus.WAITING_PHONE, null);
        MvcResult stream = openStream("42");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            reservaService.processEvent(new CatedraEvent("request-event-8a31", "AdditionalInformationRequested",
                    "process-1", null, null, null));
            status.setRollbackOnly();
        });

        assertThat(received(stream)).doesNotContain("reservation-updated");
        assertThat(repository.findById(waiting.getId()).orElseThrow().getRequestEventId()).isNull();
    }

    // Un pedido HTTP que cambia el estado también avisa: lo único que sabe la app es que el servidor lo aceptó
    @Test
    void sendingThePhoneThroughHttpShowsUpAsPhoneQueued() throws Exception {
        ReservationProcess waiting = seed("42", "process-1", ReservationStatus.WAITING_PHONE, "request-event-8a31");
        MvcResult stream = openStream("42");

        mockMvc.perform(post("/api/reservations/" + waiting.getId() + "/phone")
                .with(jwt().jwt(token -> token.claim("uid", "42"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"+5492615551234\"}")).andExpect(status().isAccepted());

        assertThat(received(stream)).contains("\"status\":\"PHONE_QUEUED\"", "\"canSubmitPhone\":false");
    }

    // Repetir un evento no cambia nada, así que no hay nada que avisar
    @Test
    void aDuplicatedEventDoesNotNotifyTwice() throws Exception {
        seed("42", "process-1", ReservationStatus.WAITING_PHONE, null);
        MvcResult stream = openStream("42");
        CatedraEvent event = new CatedraEvent("request-event-8a31", "AdditionalInformationRequested", "process-1",
                null, null, null);

        reservaService.processEvent(event);
        reservaService.processEvent(event);

        assertThat(received(stream).lines().filter(line -> line.startsWith("event:reservation-updated")).count())
                .isEqualTo(1);
    }

    @Test
    void theStreamNeedsAToken() throws Exception {
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
    }
}
