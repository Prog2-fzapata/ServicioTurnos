# 006. Definir el esquema de la base de turnos en la migración V1

**Estado:** aceptada

## Contexto

el servicio guarda el proceso de cada reserva, los mensajes que debe publicar en Kafka y los eventos que ya procesó. Hay que decidir nombres, qué datos son opcionales, qué reglas impone la base y qué índices llevan las consultas conocidas.

## Decisión

- **Tres tablas, en plural** (convención de la cátedra): `reservation_processes`, `outbox_events` y `processed_events`. Sin claves foráneas hacia otras bases: `user_id`, `professional_id` y los nombres del profesional se guardan **por valor**, como datos históricos para mostrar la reserva.
- **Los ids de la cátedra son opcionales.** El proceso local se crea en `HOLD_REQUESTED` *antes* de llamar a la cátedra, así que `reservation_process_id` y `hold_id` aceptan `NULL` hasta que el hold responde. El `UNIQUE` sobre `reservation_process_id` sigue evitando reservas duplicadas, porque PostgreSQL permite varios `NULL` en una columna única. La clave primaria es un `id` propio generado por la base.
- **Obligatorio y opcional.** Son obligatorios el dueño, el profesional y su nombre, el horario, el estado, el nombre y apellido del paciente, `version` (por defecto 0) y las fechas de auditoría. Son opcionales el teléfono, `expires_at`, `request_event_id`, `reservation_id`, `failure_reason`, `cancellation_reason` y `cancelled_at`.
- **Reglas en la base** (`CHECK`): `status` solo admite los nueve estados de la máquina de estados, el estado del outbox solo `PENDING` o `SENT`, y un turno termina después de empezar.
- **Largos** tomados del contrato de la cátedra: 100 para `user_id` (el `externalPatientId`) y los ids de la cátedra, 100 para nombre y apellido del paciente, 20 para el teléfono y 500 para el motivo de cancelación.
- **Outbox.** `outbox_events.reservation_process_id` es una clave foránea hacia el id de la cátedra de `reservation_processes`, y `message_key` guarda el mismo valor (la key que exige la cátedra). `event_id` es único.
- **Índices.** `user_id` (toda consulta y cancelación filtra por dueño), `(status, expires_at)` (vencimiento y recuperación al arrancar), `outbox_events(reservation_process_id)` (PostgreSQL no indexa las claves foráneas) y un índice **parcial** sobre `created_at` solo para las filas `PENDING`, que es lo único que lee el relay.

## Alternativas descartadas

- Estado libre, validado solo en Java: un error de tipeo (`CONFIRMD`) quedaría guardado en silencio.
- Clave foránea del outbox hacia el `id` propio: la fila no traería su message key y habría que copiarla.
- Índice común sobre `outbox_events(status)`: crece con todas las filas ya enviadas, que el relay nunca lee.
- Un `UNIQUE` local sobre profesional, fecha y hora: la cátedra ya responde `409` ante un horario ocupado, y una restricción local bloquearía los reintentos tras un `FAILED`.

## Consecuencias

agregar un estado nuevo exige una migración que cambie el `CHECK` (y el largo de la columna, si el nombre es más largo que el actual, de 15 caracteres). `TurnosSchemaTest` prueba el esquema con PostgreSQL real, y se verificó rompiendo la migración a propósito en nueve puntos.
