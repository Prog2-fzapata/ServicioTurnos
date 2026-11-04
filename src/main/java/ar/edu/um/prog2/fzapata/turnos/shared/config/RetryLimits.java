package ar.edu.um.prog2.fzapata.turnos.shared.config;

// Techos de los parámetros de reintentos: sin ellos una variable de entorno mal escrita (CATALOGO_MAX_ATTEMPTS=1000000)
// sería un bucle casi infinito. El servicio no arranca con un valor mayor y el error nombra el parámetro
public final class RetryLimits {

    // Intentos de los clientes HTTP, del choque de versión y reintentos del consumer de Kafka
    public static final int MAX_ATTEMPTS = 10;
    // Cada intento de hold es un hold más su confirmación, y cada una reintenta por su cuenta
    public static final int MAX_HOLD_ATTEMPTS = 5;

    private RetryLimits() {
    }
}
