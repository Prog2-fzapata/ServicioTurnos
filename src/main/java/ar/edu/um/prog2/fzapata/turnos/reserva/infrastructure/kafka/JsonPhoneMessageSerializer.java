package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneMessageSettings;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmission;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhoneMessageSerializer;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka.dto.PhoneSubmittedMessage;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonPhoneMessageSerializer implements PhoneMessageSerializer {

    private final JsonMapper jsonMapper;
    private final PhoneMessageSettings phoneMessageSettings;

    public JsonPhoneMessageSerializer(JsonMapper jsonMapper, PhoneMessageSettings phoneMessageSettings) {
        this.jsonMapper = jsonMapper;
        this.phoneMessageSettings = phoneMessageSettings;
    }

    @Override
    public String toPayload(PhoneSubmission submission) {
        return jsonMapper.writeValueAsString(new PhoneSubmittedMessage(submission.eventId(),
                PhoneSubmittedMessage.EVENT_TYPE, phoneMessageSettings.accountGroupId(),
                submission.reservationProcessId(), submission.holdId(), submission.requestEventId(),
                new PhoneSubmittedMessage.Information(submission.phoneNumber()), submission.occurredAt(),
                PhoneSubmittedMessage.SCHEMA_VERSION));
    }
}
