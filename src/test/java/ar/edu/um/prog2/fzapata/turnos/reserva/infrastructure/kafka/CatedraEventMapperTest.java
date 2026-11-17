package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import tools.jackson.databind.json.JsonMapper;

class CatedraEventMapperTest {

    private final CatedraEventMapper mapper = new CatedraEventMapper(JsonMapper.builder().build());

    // Con un campo que no está en el contrato a propósito: se ignora
    private static final String CONFIRMED = """
            {"eventId": "confirmation-event-7d10", "eventType": "AppointmentConfirmed", "groupId": "fzapata",
             "reservationProcessId": "process-a92b47d0", "holdId": "hold-6c0c2f41",
             "requestEventId": "request-event-8a31", "reservationId": 8251, "professionalId": 15,
             "date": "2026-09-14", "startTime": "09:30", "status": "CONFIRMED",
             "occurredAt": "2026-09-10T14:36:01Z", "schemaVersion": 1, "campoNuevo": {"a": 1}}""";

    @Test
    void mapsAConfirmationAndIgnoresWhatItDoesNotUse() {
        CatedraEvent event = mapper.toDomainModel(CONFIRMED);

        assertThat(event.eventId()).isEqualTo("confirmation-event-7d10");
        assertThat(event.eventType()).isEqualTo("AppointmentConfirmed");
        assertThat(event.reservationProcessId()).isEqualTo("process-a92b47d0");
        assertThat(event.reservationId()).isEqualTo(8251L);
        assertThat(event.reason()).isNull();
        assertThat(event.expiresAt()).isNull();
    }

    @Test
    void mapsThePhoneRequestWithItsExpiry() {
        CatedraEvent event = mapper.toDomainModel("""
                {"eventId": "request-event-8a31", "eventType": "AdditionalInformationRequested",
                 "reservationProcessId": "process-a92b47d0", "holdId": "hold-6c0c2f41",
                 "requiredField": "PHONE_NUMBER", "message": "Ingrese un numero de telefono para continuar.",
                 "expiresAt": "2026-09-10T14:44:50Z", "occurredAt": "2026-09-10T14:35:10Z", "schemaVersion": 1}""");

        assertThat(event.expiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
        assertThat(event.reason()).isNull();
    }

    @Test
    void mapsTheReasonOfACancellation() {
        CatedraEvent event = mapper.toDomainModel("""
                {"eventId": "cancellation-event-7d14", "eventType": "AppointmentCancelled",
                 "reservationProcessId": "process-a92b47d0", "reservationId": 8251,
                 "reason": "Cambio solicitado por el usuario"}""");

        assertThat(event.reason()).isEqualTo("Cambio solicitado por el usuario");
    }

    // Un tipo que no se conoce no es un error de formato: se registra y se ignora más adelante
    @Test
    void anUnknownTypeIsStillAValidMessage() {
        CatedraEvent event = mapper.toDomainModel(
                "{\"eventId\": \"e-1\", \"eventType\": \"AlgoNuevo\", \"reservationProcessId\": \"p-1\"}");

        assertThat(event.eventType()).isEqualTo("AlgoNuevo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"esto no es json", "[1, 2]", "{\"eventId\": ", "\"texto\""})
    void aMessageThatIsNotAnEventObjectIsInvalid(String payload) {
        assertThatThrownBy(() -> mapper.toDomainModel(payload)).isInstanceOf(InvalidCatedraEventException.class);
    }

    @Test
    void aJsonNullIsInvalid() {
        assertThatThrownBy(() -> mapper.toDomainModel("null")).isInstanceOf(InvalidCatedraEventException.class)
                .hasMessageContaining("vacío");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aMissingOrBlankEventIdIsInvalid(String eventId) {
        String json = "{\"eventId\": " + quote(eventId) + ", \"eventType\": \"AppointmentConfirmed\", "
                + "\"reservationProcessId\": \"p-1\"}";

        assertThatThrownBy(() -> mapper.toDomainModel(json)).isInstanceOf(InvalidCatedraEventException.class)
                .hasMessageContaining("eventId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aMissingOrBlankEventTypeIsInvalid(String eventType) {
        String json = "{\"eventId\": \"e-1\", \"eventType\": " + quote(eventType)
                + ", \"reservationProcessId\": \"p-1\"}";

        assertThatThrownBy(() -> mapper.toDomainModel(json)).isInstanceOf(InvalidCatedraEventException.class)
                .hasMessageContaining("eventType").hasMessageContaining("e-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aMissingOrBlankReservationProcessIdIsInvalid(String processId) {
        String json = "{\"eventId\": \"e-1\", \"eventType\": \"AppointmentConfirmed\", \"reservationProcessId\": "
                + quote(processId) + "}";

        assertThatThrownBy(() -> mapper.toDomainModel(json)).isInstanceOf(InvalidCatedraEventException.class)
                .hasMessageContaining("reservationProcessId").hasMessageContaining("e-1");
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
