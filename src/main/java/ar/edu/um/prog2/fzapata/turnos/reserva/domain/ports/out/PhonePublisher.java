package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

// Publica un mensaje en Kafka y espera la confirmación del broker. Lanza PhonePublishException si no la consigue
public interface PhonePublisher {

    void publish(String topic, String key, String payload);
}
