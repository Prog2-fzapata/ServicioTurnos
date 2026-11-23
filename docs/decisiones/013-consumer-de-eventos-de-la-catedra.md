# 013. Procesar cada evento de la cátedra en una transacción, con el reintento por fuera

**Estado:** aceptada

## Contexto

la cátedra publica en Kafka el avance de cada reserva (pedido de teléfono, confirmación, rechazo, vencimiento, invalidez y cancelación). La entrega es «al menos una vez», con posibles repetidos y fuera de orden. Guardar el estado y registrar el `eventId` son dos escrituras que deben quedar juntas, y el reintento por choque de versión de la [decisión 010](010-maquina-de-estados.md) no vale dentro de una transacción abierta.

## Decisión

- **Un caso de uso transaccional por evento** (`ProcessCatedraEventUseCase`): comprueba el `eventId` en `processed_events`, busca el proceso por `reservationProcessId`, aplica la transición y registra el `eventId`, todo en una transacción. Aplica la transición con `applyTransitionOnce`, un solo intento sin releer. El adapter guarda con `saveAndFlush`: dentro de una transacción abierta el guardado normal recién escribe al hacer `COMMIT`, y ahí el choque de versión sale como una excepción de Spring sin traducir; con `saveAndFlush` salta dentro del caso de uso como `ReservationConflictException` y el servicio lo reintenta.
- **El reintento va por fuera.** `ReservaService.processEvent` repite el evento entero (transacción nueva) hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS` si hubo un choque de versión; es seguro porque el registro del `eventId` se deshizo con el resto.
- **Datos sin cambio de estado.** El pedido de teléfono llega con el proceso ya en `WAITING_PHONE`, y su `eventId` es el `requestEventId` que hay que devolver con el teléfono. La transición ahora completa los datos que faltan aunque el estado no cambie, sin pisar los que ya hay y sin tocar nunca un proceso final (nuevo resultado `DATA_COMPLETED`). Con esto el proceso sabe que ya se puede pedir el teléfono cuando tiene `requestEventId`.
- **Qué se ignora, y se registra:** un evento repetido (`DUPLICATE`), de un proceso que no existe, de un tipo que no se trata, o un paso que el diagrama no permite desde el estado actual. Reentregarlos no los arregla y no deben trabar la lectura de los que siguen. Un mensaje que no se puede interpretar (JSON inválido, sin `eventId`, `eventType` o `reservationProcessId`) se descarta sin reintentos.
- **Offset y errores:** commit manual, después del `COMMIT`; reintentos acotados con espera creciente (`CATEDRA_KAFKA_MAX_RETRIES`, `CATEDRA_KAFKA_INITIAL_BACKOFF`, con valor por defecto y el entorno gana); agotados, el mensaje se descarta con un log de error, sin cola de eventos muertos. Sin offset guardado se empieza por el principio del topic (`earliest`).
- **Configuración:** variables obligatorias y sin valor por defecto `CATEDRA_KAFKA_BOOTSTRAP_SERVERS`, `CATEDRA_KAFKA_GROUP_ID` y `CATEDRA_KAFKA_APPOINTMENT_ACTIONS_TOPIC`. El código está en `infrastructure/kafka`, igual que en catálogo.
- **La carrera con «iniciar reserva».** La cátedra publica el pedido de teléfono justo después del `202` de la confirmación, y su evento puede procesarse antes de que `POST /api/reservations` guarde `WAITING_PHONE`. Ese paso trata ahora «ya estaba en el estado pedido» como correcto; solo un `REJECTED` es un error.

## Alternativas descartadas

- Usar `applyTransition` (con su reintento) dentro de la transacción: un fallo de versión la marca para deshacer y releer no sirve.
- Registrar el `eventId` en una transacción aparte: un fallo en el medio dejaría el estado cambiado sin registro (se reaplicaría) o el registro sin el estado (se perdería el efecto).
- No registrar los eventos que se ignoran: el consumidor los reabriría una y otra vez sin ningún efecto.
- Cola de eventos muertos: más piezas sin un beneficio claro; una reserva que se quede sin su evento la recupera la reconciliación con la cátedra.
- Guardar el `requestEventId` en una tabla o transición aparte: es un dato del proceso y viaja con la misma transición.

## Consecuencias

el grupo de consumidores de la cuenta es uno solo, así que catálogo y turnos lo comparten con topics distintos: cada partición se asigna al servicio que está suscripto a ese topic. El topic de acciones de la cuenta existe pero está vacío: lo real se prueba al final con una reserva de verdad, y mientras tanto con Kafka en Testcontainers. Un evento que se agotó en los reintentos deja la reserva sin actualizar hasta la reconciliación ([decisión 017](017-recuperacion-al-arrancar.md)).
