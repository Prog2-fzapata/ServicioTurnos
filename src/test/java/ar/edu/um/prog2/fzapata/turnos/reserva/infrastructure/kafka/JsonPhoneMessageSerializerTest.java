package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneMessageSettings;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmission;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonPhoneMessageSerializerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final JsonPhoneMessageSerializer serializer = new JsonPhoneMessageSerializer(jsonMapper,
            new PhoneMessageSettings("catedra.turnos.telefono.fzapata", "fzapata"));

    private final PhoneSubmission submission = new PhoneSubmission("phone-submitted-b91e", "process-a92b47d0",
            "hold-6c0c2f41", "request-event-8a31", "+5492615551234", Instant.parse("2026-09-10T14:36:00Z"));

    // Exactamente la forma del anexo §15.5: todos los campos, con eventType y schemaVersion fijos
    @Test
    void buildsTheMessageOfTheContract() {
        JsonNode json = jsonMapper.readTree(serializer.toPayload(submission));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("eventId", "eventType", "groupId",
                "reservationProcessId", "holdId", "requestEventId", "information", "occurredAt", "schemaVersion");
        assertThat(json.get("eventId").asString()).isEqualTo("phone-submitted-b91e");
        assertThat(json.get("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
        assertThat(json.get("groupId").asString()).isEqualTo("fzapata");
        assertThat(json.get("reservationProcessId").asString()).isEqualTo("process-a92b47d0");
        assertThat(json.get("holdId").asString()).isEqualTo("hold-6c0c2f41");
        assertThat(json.get("requestEventId").asString()).isEqualTo("request-event-8a31");
        assertThat(json.get("information").propertyNames()).containsExactly("phoneNumber");
        assertThat(json.get("information").get("phoneNumber").asString()).isEqualTo("+5492615551234");
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-09-10T14:36:00Z");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    void theSameSubmissionAlwaysGivesTheSamePayload() {
        assertThat(serializer.toPayload(submission)).isEqualTo(serializer.toPayload(submission));
    }
}
