package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.event.KeyValuePair;

import ch.qos.logback.classic.spi.ILoggingEvent;

// Los campos de una línea de log (los que salen como campos del JSON), para comprobarlos en los tests
final class LoggedFields {

    private LoggedFields() {
    }

    static Map<String, Object> of(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> fields.put(pair.key, pair.value));
        }
        return fields;
    }

    // Todo lo que una línea escribe: el mensaje y los campos. Sirve para comprobar que no se filtra nada
    static String everything(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + of(event);
    }
}
