package ar.edu.um.prog2.fzapata.turnos.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import lombok.RequiredArgsConstructor;

// Prueba el esquema de la migración V1 directamente en SQL: todavía no hay clases Java del dominio de turnos
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class TurnosSchemaTest {

    private static final List<String> REQUIRED_COLUMNS = List.of("user_id", "professional_id",
            "professional_first_name", "professional_last_name", "appointment_date", "start_time", "end_time",
            "status", "patient_first_name", "patient_last_name", "created_at", "updated_at");

    private final JdbcClient jdbc;

    // Una reserva recién iniciada: sin ids de la cátedra, teléfono ni vencimiento
    private Map<String, Object> startedProcess() {
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("user_id", "42");
        columns.put("professional_id", 15L);
        columns.put("professional_first_name", "Ana");
        columns.put("professional_last_name", "Gomez");
        columns.put("appointment_date", "2026-11-02");
        columns.put("start_time", "09:30");
        columns.put("end_time", "10:00");
        columns.put("status", "HOLD_REQUESTED");
        columns.put("patient_first_name", "Juan");
        columns.put("patient_last_name", "Perez");
        return columns;
    }

    private void insertProcess(Map<String, Object> columns) {
        Map<String, Object> all = new LinkedHashMap<>(columns);
        // containsKey y no putIfAbsent: un null explícito debe llegar a la base
        for (String audit : List.of("created_at", "updated_at")) {
            if (!all.containsKey(audit)) {
                all.put(audit, "2026-11-01T10:00:00Z");
            }
        }
        String names = String.join(", ", all.keySet());
        String values = all.keySet().stream().map(name -> switch (name) {
            case "appointment_date" -> "CAST(:" + name + " AS date)";
            case "start_time", "end_time" -> "CAST(:" + name + " AS time)";
            case "created_at", "updated_at", "expires_at", "cancelled_at" -> "CAST(:" + name + " AS timestamptz)";
            default -> ":" + name;
        }).reduce((a, b) -> a + ", " + b).orElseThrow();
        JdbcClient.StatementSpec statement = jdbc.sql("INSERT INTO reservation_processes (" + names + ") VALUES ("
                + values + ")");
        all.forEach(statement::param);
        statement.update();
    }

    private void insertProcess() {
        insertProcess(startedProcess());
    }

    private void insertProcessWith(String column, Object value) {
        Map<String, Object> columns = startedProcess();
        columns.put(column, value);
        insertProcess(columns);
    }

    private void insertOutbox(String eventId, String processId) {
        jdbc.sql("""
                INSERT INTO outbox_events (event_id, reservation_process_id, topic, message_key, payload)
                VALUES (:eventId, :processId, 'catedra.turnos.telefono.fzapata', :processId, '{}')""")
                .param("eventId", eventId).param("processId", processId).update();
    }

    private void insertProcessedEvent(String eventId) {
        jdbc.sql("INSERT INTO processed_events (event_id, event_type) VALUES (:id, 'AppointmentConfirmed')")
                .param("id", eventId).update();
    }

    private List<String> indexNames() {
        return jdbc.sql("SELECT indexname FROM pg_indexes WHERE schemaname = current_schema()")
                .query(String.class).list();
    }

    @Test
    void aStartedProcessIsAcceptedWithoutTheIdsOfTheCatedra() {
        insertProcess();

        Map<String, Object> process = jdbc.sql("SELECT * FROM reservation_processes").query().singleRow();

        assertThat(process).containsEntry("status", "HOLD_REQUESTED").containsEntry("version", 0L)
                .containsEntry("reservation_process_id", null).containsEntry("hold_id", null)
                .containsEntry("expires_at", null).containsEntry("patient_phone", null)
                .containsEntry("request_event_id", null).containsEntry("reservation_id", null);
        assertThat(process.get("id")).isNotNull();
    }

    @Test
    void aProcessWithEveryFieldCompleteIsAccepted() {
        Map<String, Object> columns = startedProcess();
        columns.put("reservation_process_id", "process-a92b47d0");
        columns.put("hold_id", "hold-6c0c2f41");
        columns.put("status", "CANCELLED");
        columns.put("expires_at", "2026-11-01T10:15:00Z");
        columns.put("patient_phone", "+5492615551234");
        columns.put("request_event_id", "request-event-8a31");
        columns.put("reservation_id", 8251L);
        columns.put("failure_reason", "motivo");
        columns.put("cancellation_reason", "Cambio solicitado por el usuario");
        columns.put("cancelled_at", "2026-11-01T11:00:00Z");

        insertProcess(columns);

        assertThat(jdbc.sql("SELECT count(*) FROM reservation_processes").query(Long.class).single()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOLD_REQUESTED", "HELD", "WAITING_PHONE", "PHONE_QUEUED", "PHONE_SUBMITTED",
            "CONFIRMED", "EXPIRED", "FAILED", "CANCELLED"})
    void everyStatusOfTheStateMachineIsAccepted(String status) {
        insertProcessWith("status", status);

        assertThat(jdbc.sql("SELECT status FROM reservation_processes").query(String.class).single())
                .isEqualTo(status);
    }

    @Test
    void anUnknownStatusIsRejected() {
        assertThatThrownBy(() -> insertProcessWith("status", "CONFIRMD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservation_processes_status");
    }

    @ParameterizedTest
    @FieldSource("REQUIRED_COLUMNS")
    void theMandatoryDataOfTheProcessCannotBeMissing(String column) {
        assertThatThrownBy(() -> insertProcessWith(column, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(column);
    }

    @Test
    void aSlotThatEndsBeforeItStartsIsRejected() {
        Map<String, Object> columns = startedProcess();
        columns.put("start_time", "10:00");
        columns.put("end_time", "09:30");

        assertThatThrownBy(() -> insertProcess(columns)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservation_processes_time_range");
    }

    // Garantiza que no haya dos reservas para el mismo proceso de la cátedra
    @Test
    void theReservationProcessIdOfTheCatedraCannotBeRepeated() {
        insertProcessWith("reservation_process_id", "process-a92b47d0");

        assertThatThrownBy(() -> insertProcessWith("reservation_process_id", "process-a92b47d0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_reservation_processes_process_id");
    }

    // Varios procesos pueden estar en HOLD_REQUESTED a la vez, antes de que la cátedra les dé un id
    @Test
    void severalProcessesWithoutTheIdOfTheCatedraCanCoexist() {
        insertProcess();
        insertProcess();

        assertThat(jdbc.sql("SELECT count(*) FROM reservation_processes").query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void theCancellationReasonIsLimitedToFiveHundredCharacters() {
        insertProcessWith("cancellation_reason", "a".repeat(500));

        assertThatThrownBy(() -> insertProcessWith("cancellation_reason", "a".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anOutboxEventStartsPendingWithoutAttemptsNorSendTime() {
        insertProcessWith("reservation_process_id", "process-a92b47d0");
        insertOutbox("phone-submitted-b91e", "process-a92b47d0");

        Map<String, Object> event = jdbc.sql("SELECT * FROM outbox_events").query().singleRow();

        assertThat(event).containsEntry("status", "PENDING").containsEntry("attempts", 0)
                .containsEntry("sent_at", null).containsEntry("message_key", "process-a92b47d0");
        assertThat(event.get("created_at")).isNotNull();
    }

    @Test
    void anOutboxEventOfAnUnknownProcessIsRejected() {
        assertThatThrownBy(() -> insertOutbox("phone-submitted-b91e", "process-inexistente"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_outbox_events_process");
    }

    // Un evento publicado dos veces llegaría duplicado a la cátedra
    @Test
    void theEventIdOfTheOutboxCannotBeRepeated() {
        insertProcessWith("reservation_process_id", "process-a92b47d0");
        insertOutbox("phone-submitted-b91e", "process-a92b47d0");

        assertThatThrownBy(() -> insertOutbox("phone-submitted-b91e", "process-a92b47d0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_outbox_events_event_id");
    }

    @Test
    void anOutboxEventOnlyAcceptsPendingOrSent() {
        insertProcessWith("reservation_process_id", "process-a92b47d0");
        insertOutbox("phone-submitted-b91e", "process-a92b47d0");

        assertThat(jdbc.sql("UPDATE outbox_events SET status = 'SENT', sent_at = now()").update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("UPDATE outbox_events SET status = 'FAILED'").update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_outbox_events_status");
    }

    @Test
    void processedEventIsStoredWithItsProcessingTime() {
        insertProcessedEvent("confirmation-event-7d10");

        Object processedAt = jdbc.sql("SELECT processed_at FROM processed_events WHERE event_id = 'confirmation-event-7d10'")
                .query().singleValue();

        assertThat(processedAt).isNotNull();
    }

    // Base de la deduplicación: Kafka entrega al menos una vez, así que el mismo evento puede llegar dos veces
    @Test
    void duplicatedProcessedEventIsRejected() {
        insertProcessedEvent("confirmation-event-7d10");

        assertThatThrownBy(() -> insertProcessedEvent("confirmation-event-7d10"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("processed_events_pkey");
    }

    @Test
    void theIndexesOfTheQueriesExist() {
        assertThat(indexNames()).contains("idx_reservation_processes_user_id",
                "idx_reservation_processes_status_expires_at", "idx_outbox_events_process_id",
                "idx_outbox_events_pending");
    }

    // El relay lee solo las pendientes: el índice parcial deja afuera las ya enviadas
    @Test
    void thePendingIndexOnlyCoversThePendingEvents() {
        String definition = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_outbox_events_pending'")
                .query(String.class).single();

        assertThat(definition).contains("created_at").contains("WHERE").contains("'PENDING'");
    }
}
