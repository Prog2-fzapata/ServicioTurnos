# 014. Enviar el teléfono con un outbox transaccional y un relay

**Estado:** aceptada

## Contexto

el teléfono que pide la cátedra se envía por Kafka, y el endpoint no puede escribir en la base y publicar como una sola operación: son dos sistemas sin transacción compartida. Además, la app necesita saber cuándo habilitar el formulario, porque el pedido de la cátedra llega un instante después de iniciar la reserva.

## Decisión

- **Outbox transaccional.** `POST /api/reservations/{id}/phone` pasa la reserva a `PHONE_QUEUED` con el teléfono y guarda el mensaje en `outbox_events` en la misma transacción; el reintento por choque de versión va por fuera, como con los eventos ([decisión 013](013-consumer-de-eventos-de-la-catedra.md)). Un **relay** (tarea programada, `OUTBOX_RELAY_INTERVAL`) toma los pendientes del más viejo al más nuevo (`OUTBOX_RELAY_BATCH_SIZE`), publica, espera el ack (`OUTBOX_PUBLISH_TIMEOUT`, productor con `acks=all` e idempotencia) y en una segunda transacción marca `SENT` y pasa el proceso a `PHONE_SUBMITTED`.
- **Reenvío con el mismo `eventId` y contenido.** Un mensaje sin ack queda `PENDING` con un intento más; si el relay cae entre el ack y el marcado, se reenvía el mismo mensaje y la cátedra lo descarta como duplicado. Un conflicto de versión al marcar repite solo la actualización: el mensaje ya está publicado.
- **Si la cátedra contesta antes del ack**, el paso a `PHONE_SUBMITTED` lo rechaza el diagrama y el proceso no retrocede, pero el mensaje igual queda `SENT`.
- **Un proceso que ya terminó no frena el mensaje.** Si la reserva venció o se canceló mientras el mensaje esperaba (Kafka caído un rato), se publica igual: la cátedra decide qué hacer y nuestro consumer ignora lo que llegue después de un estado final. La alternativa, un estado `DISCARDED` en el outbox, requería una migración y más piezas para un caso muy raro.
- **`canSubmitPhone`.** `Reservation` expone un booleano calculado: `true` solo con la reserva en `WAITING_PHONE` y el `requestEventId` ya guardado; vuelve a ser `true` si la cátedra rechaza el número. El nombre dice qué hace la app con él (habilitar el formulario), a diferencia de `phoneRequested`, que se leía como «el usuario ya lo pidió». No agrega un estado a la máquina.
- **Formato del teléfono.** Se aplica la regla de la cátedra: se quitan espacios, guiones, paréntesis y puntos, y debe quedar `^\+?[0-9]{7,15}$`. Se envía **tal cual**, sin completar el código de país ni quitar el `0` o el `15`: `+549...` y `+54...` son números distintos y adivinar uno sería inventar el dato; ser más estrictos que la cátedra rechazaría números que ella acepta. Un texto de más de 40 caracteres se rechaza para acotar la entrada.
- **Errores.** `409 INVALID_RESERVATION_STATE` si no espera el teléfono, la cátedra no lo pidió o ya se envió uno; `409 RESERVATION_EXPIRED` si pasó `expiresAt`, con un mensaje propio (habla del teléfono, no del bloqueo del horario). El endpoint no mueve el proceso a `EXPIRED`: eso lo hace el vencimiento o el evento de la cátedra.
- **Configuración:** `CATEDRA_KAFKA_APPOINTMENT_PHONE_TOPIC` y `CATEDRA_GROUP_ID` (el `groupId` de la cuenta, campo obligatorio del mensaje) son obligatorias y sin valor por defecto; los parámetros del relay tienen valor por defecto y el entorno gana.

## Alternativas descartadas

- Publicar en Kafka dentro del endpoint: si Kafka falla después de guardar el estado se pierde el teléfono, y si se publica primero la cátedra puede responder antes de que exista `PHONE_QUEUED`.
- Un campo `phoneRequested`: se confundía con «el usuario ya lo envió».
- Un estado propio para «pedido recibido»: cambia la máquina de estados y su diagrama por un dato que ya existe.
- Descartar el mensaje si el proceso terminó: más estados y una migración para un caso raro e inofensivo.
- Completar el número con `+54` o quitar el `0` y el `15`: es adivinar, y `+549...` y `+54...` son números distintos.

## Consecuencias

hay una tarea de fondo más, siempre activa; su consulta es barata (índice parcial sobre los `PENDING`). Un mensaje que Kafka nunca confirme queda `PENDING` y se sigue reintentando; el vencimiento ([decisión 016](016-vencimiento-de-procesos.md)) cierra la reserva, pero no descarta el mensaje. El relay no está pensado para varias instancias a la vez: dos podrían publicar el mismo mensaje, que la cátedra descartaría por su `eventId`. El topic de teléfono existe pero nunca recibió un mensaje: lo real se prueba al final con una reserva de verdad, y mientras tanto con Kafka en Testcontainers.
