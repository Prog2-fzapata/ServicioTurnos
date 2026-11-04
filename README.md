<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-sin%20c%C3%B3digo-lightgrey) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey)

**Calidad (7 dimensiones):** sin código en `main` (no evaluable).
<!-- calidad:fin -->

# ServicioTurnos

Servicio de turnos y reservas.

## Requisitos

- Docker y Docker Compose v2.
- Java 25, solo si se ejecuta el servicio fuera de Docker.

## Configuración

```bash
cp .env.example .env
```

Completar la clave de la base y el secreto del JWT en `.env` (el mismo valor que `JWT_SECRET` en el `.env` de catálogo). El archivo no se versiona.

| Variable | Descripción | Por defecto |
| --- | --- | --- |
| `TURNOS_DB_NAME` | Nombre de la base de datos | `turnos_db` |
| `TURNOS_DB_USER` | Usuario de la base | `turnos_user` |
| `TURNOS_DB_PASSWORD` | Clave del usuario | |
| `JWT_SECRET` | Clave con la que catálogo firma los tokens (HS512): Base64 de al menos 512 bits. Si falta o es más corta, el servicio no arranca | |
| `CATEDRA_BASE_URL` | URL base de la API de la cátedra, sin `/` final. Obligatoria: si falta, el servicio no arranca | |
| `CATEDRA_JWT` | JWT técnico de la cátedra (el `id_token` de su `POST /api/authenticate`). Es el mismo que usa catálogo: la cuenta técnica es una sola para los dos servicios. Obligatorio; no se escribe nunca en logs ni en mensajes de error | |
| `CATEDRA_KAFKA_BOOTSTRAP_SERVERS` | Servidor de Kafka de la cátedra. Es la IP de ZeroTier, no el host interno que informa la API. Obligatoria: si falta, el servicio no arranca. Es el mismo valor que en catálogo | |
| `CATEDRA_KAFKA_GROUP_ID` | Consumer group de la cuenta (`kafkaConsumerGroupId` de `integration`). Obligatoria. Es el mismo valor que en catálogo | |
| `CATEDRA_KAFKA_APPOINTMENT_ACTIONS_TOPIC` | Topic con los eventos de reserva de la cátedra (`kafkaAppointmentActionsTopic`). Obligatoria | |
| `CATEDRA_KAFKA_APPOINTMENT_PHONE_TOPIC` | Topic al que se publica el teléfono (`kafkaAppointmentPhoneTopic`). Obligatoria | |
| `CATEDRA_GROUP_ID` | `groupId` de la cuenta (`integration.groupId`): el mensaje del teléfono lo lleva como campo obligatorio. Obligatoria | |
| `CATALOGO_BASE_URL` | URL del servicio de catálogo, sin `/` final. Obligatoria: si falta, el servicio no arranca. Desde el contenedor se llega por el host | `http://host.docker.internal:8081` |
| `POSTGRES_PORT` | Puerto del host donde se publica PostgreSQL | `5433` |
| `TURNOS_PORT` | Puerto del host donde se publica el servicio | `8082` |

PostgreSQL y el servicio se publican solo en `127.0.0.1`.

Parámetros de ajuste opcionales de los clientes de catálogo y de la cátedra y de la disponibilidad (tienen valor por defecto; si la variable está definida en el entorno, esa gana):

| Variable | Descripción | Por defecto |
| --- | --- | --- |
| `CATALOGO_CONNECT_TIMEOUT` | Espera máxima para conectar con catálogo | `2s` |
| `CATALOGO_READ_TIMEOUT` | Espera máxima por la respuesta de catálogo | `3s` |
| `CATALOGO_MAX_ATTEMPTS` | Intentos en total, el primero incluido. `1` desactiva los reintentos. Mínimo 1 | `3` |
| `CATALOGO_INITIAL_BACKOFF` | Espera antes del primer reintento; cada uno espera el doble del anterior | `200ms` |
| `CATEDRA_CONNECT_TIMEOUT` | Espera máxima para conectar con la cátedra | `2s` |
| `CATEDRA_READ_TIMEOUT` | Espera máxima por la respuesta de la cátedra | `5s` |
| `CATEDRA_MAX_ATTEMPTS` | Intentos en total, el primero incluido, para las operaciones que se pueden repetir. `1` desactiva los reintentos. Mínimo 1 | `3` |
| `CATEDRA_INITIAL_BACKOFF` | Espera antes del primer reintento; cada uno espera el doble del anterior | `200ms` |
| `AVAILABILITY_ZONE` | Zona horaria de atención: en ella se decide qué horarios ya pasaron (la cátedra expresa las horas en hora local) | `America/Argentina/Mendoza` |
| `AVAILABILITY_MAX_RANGE_DAYS` | Días máximos entre `from` y `to` de la disponibilidad, ambos incluidos. Mínimo 1 | `31` |
| `RESERVATION_CONFLICT_MAX_ATTEMPTS` | Intentos en total, el primero incluido, cuando dos actualizaciones del mismo proceso de reserva chocan por la versión (bloqueo optimista). `1` desactiva el reintento. Mínimo 1 | `3` |
| `RESERVATION_HOLD_MAX_ATTEMPTS` | Intentos en total, el primero incluido, para conseguir un hold confirmado: cada intento es un hold más su confirmación. Se repite si se perdió la respuesta del hold y el horario figura libre, o si el hold venció antes de confirmarse. `1` desactiva el reintento. Mínimo 1 | `2` |
| `CATEDRA_KAFKA_MAX_RETRIES` | Reintentos después del primer intento cuando falla el procesamiento de un evento de Kafka. `0` los desactiva | `3` |
| `CATEDRA_KAFKA_INITIAL_BACKOFF` | Espera antes del primer reintento; cada uno espera el doble del anterior | `1s` |
| `OUTBOX_RELAY_INTERVAL` | Cada cuánto mira el relay del outbox los mensajes pendientes de publicar (el teléfono) | `1s` |
| `OUTBOX_RELAY_BATCH_SIZE` | Mensajes que toma el relay por vuelta. Mínimo 1 | `50` |
| `OUTBOX_PUBLISH_TIMEOUT` | Espera máxima por la confirmación de Kafka de un mensaje; si se agota queda pendiente y se reintenta | `10s` |
| `STREAM_HEARTBEAT_INTERVAL` | Cada cuánto el stream de cambios manda el comentario de latido; también es la precisión con que se cierra una conexión cuyo JWT venció | `25s` |
| `STREAM_MAX_CONNECTIONS_PER_USER` | Conexiones del stream abiertas a la vez por usuario; al superarlo se cierra la más vieja. Mínimo 1 | `5` |
| `RESERVATION_EXPIRATION_INTERVAL` | Cada cuánto corre la tarea que expira las reservas vencidas (espera entre el fin de una vuelta y el comienzo de la siguiente) | `30s` |
| `RESERVATION_EXPIRATION_GRACE` | Cuánto después de su vencimiento se expira una reserva: deja llegar el evento de la cátedra que viene en camino. `0s` la expira en cuanto vence | `60s` |
| `RESERVATION_EXPIRATION_BATCH_SIZE` | Reservas que toma la tarea de vencimiento por vuelta; lo que sobre sigue en la vuelta siguiente. Mínimo 1 | `100` |
| `SCHEDULING_POOL_SIZE` | Hilos del planificador, uno por tarea programada (relay del outbox, latido del stream y vencimiento); con uno solo, una vuelta lenta retrasaría a las otras | `3` |
| `RECOVERY_BATCH_SIZE` | Procesos de cada clase (pedidos de hold sin resultado y reservas con el teléfono ya enviado) que revisa la recuperación al levantar el servicio. Mínimo 1 | `100` |

## Levantar con Docker

```bash
docker compose up -d --build
```

Levanta una instancia de PostgreSQL propia del servicio y el servicio, que arranca cuando la base está lista. Estado: `http://localhost:8082/actuator/health`.

| Acción | Comando |
| --- | --- |
| Ver estado | `docker compose ps` |
| Ver logs | `docker compose logs -f turnos` |
| Detener (conserva los datos) | `docker compose down` |
| Borrar todo, incluidos los datos | `docker compose down -v` |

## Ejecutar fuera de Docker

Levantar solo la base y correr el servicio con Maven, con las variables de `.env` cargadas:

```bash
docker compose up -d --wait postgres
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

El servicio se conecta a `localhost` en el puerto `POSTGRES_PORT`. Fuera de Docker, catálogo se alcanza en `localhost`, así que hay que pisar la URL del `.env`:

```bash
CATALOGO_BASE_URL=http://localhost:8081 ./mvnw spring-boot:run
```

## Seguridad

Turnos no emite tokens: valida el JWT que emite catálogo (`POST /api/authenticate`) con el mismo secreto. Todo exige `Authorization: Bearer <jwt>`, salvo `/actuator/health` y la documentación. Un token ausente, mal firmado, vencido o sin `uid`/`auth` recibe `401`; la falta de rol recibe `403`; ambos en `application/problem+json`. En Swagger UI el botón «Authorize» recibe el token. El `uid` del usuario sale siempre del token.

## Cliente de catálogo

Turnos le pide a catálogo la agenda vigente de un profesional (`GET /api/professionals/{id}`) antes de iniciar una operación, **con el JWT del propio usuario**: cada pedido reenvía el `Authorization` del usuario que lo originó, y catálogo lo valida con el mismo secreto.

| Respuesta de catálogo | Qué hace turnos |
| --- | --- |
| `200` | Devuelve el profesional con todos sus horarios, también los deshabilitados (lo decide quien los usa) |
| `404` | El profesional no existe; no se reintenta |
| `5xx`, timeout o conexión caída | Reintenta hasta `CATALOGO_MAX_ATTEMPTS` intentos en total, con una espera que se duplica (200 ms, 400 ms). Si se agotan, responde `503` con el código `CATALOG_UNAVAILABLE`. Con los valores por defecto la espera máxima ronda los 10 segundos |
| Otro `4xx` (por ejemplo `401` por un secreto distinto) o un cuerpo ilegible | No se reintenta: es un desajuste entre servicios, no una caída. Responde `500` genérico y el log registra solo el status, nunca el cuerpo ni el JWT |

Mientras catálogo no responda no se pueden iniciar operaciones que necesiten información vigente; lo que depende solo de datos propios sigue funcionando.

## Cliente de la cátedra

Turnos habla con el servicio central de la cátedra con el **JWT técnico del proyecto** (`CATEDRA_JWT`), no con el del usuario. Las operaciones son: ocupaciones de un profesional, crear un hold, confirmarlo, listar reservas y cancelar.

Los errores de la cátedra se traducen por `status` y `code`, nunca por el texto:

| Respuesta de la cátedra | Qué hace turnos |
| --- | --- |
| Rechazo funcional (`SLOT_ALREADY_HELD`, `SLOT_ALREADY_RESERVED`, `PROFESSIONAL_NOT_FOUND`, `PROFESSIONAL_DISABLED`, `INVALID_SLOT`, `HOLD_NOT_FOUND`, `HOLD_EXPIRED`, `RESERVATION_PROCESS_ALREADY_CONFIRMED`, `RESERVATION_NOT_FOUND`, `RESERVATION_NOT_CANCELLABLE`) | Cada caso de uso decide qué responder; no se reintenta |
| `401` o `403` (por ejemplo, un `CATEDRA_JWT` vencido o de otra cuenta) | Error de configuración: `500` genérico, y el log lo señala sin mostrar el token |
| Otro `4xx` o un cuerpo ilegible | Desajuste con el contrato: `500` genérico |
| `5xx`, timeout o conexión caída | Reintenta si la operación se puede repetir; si se agotan los intentos, responde `503` con el código `CATEDRA_UNAVAILABLE` |

**Qué se reintenta y qué no.** Las lecturas (ocupaciones y listado), confirmar un hold y cancelar se pueden repetir sin riesgo: se reintentan hasta `CATEDRA_MAX_ATTEMPTS` intentos en total, con una espera que se duplica. **Crear un hold no se reintenta** si el pedido pudo haber llegado: la cátedra no tiene idempotencia ni permite recuperar los ids de un hold, así que repetir el POST tras un timeout podría chocar con nuestro propio hold sin poder reconocerlo. Solo se repite si la conexión ni siquiera se estableció (nada se envió). Cuando el resultado queda desconocido, el proceso se abandona y antes de crear otro hold se consultan las ocupaciones del profesional: si el horario aparece `HELD`, se espera hasta su `expiresAt` sin asumir que el hold es nuestro (el TTL es de 10 minutos desde la creación en la cátedra). Por eso, tras una respuesta perdida, el horario puede quedar bloqueado hasta unos 10 minutos, también para el mismo usuario.

Un `409` de horario ocupado nunca se toma como «ese hold es mío»: se informa como horario no disponible.

## Disponibilidad

`GET /api/availability?professionalId=15&from=2026-10-12&to=2026-10-25` devuelve los horarios libres de un profesional: su agenda semanal (catálogo) menos lo que la cátedra informa como ocupado, ordenados por fecha y hora y sin los que ya empezaron.

```bash
curl "localhost:8082/api/availability?professionalId=15&from=2026-10-12&to=2026-10-25" \
  -H "Authorization: Bearer <id_token>"
```

```json
{
  "professionalId": 15,
  "from": "2026-10-12",
  "to": "2026-10-25",
  "slots": [
    { "date": "2026-10-12", "startTime": "09:00:00", "endTime": "09:30:00" },
    { "date": "2026-10-12", "startTime": "10:00:00", "endTime": "10:30:00" }
  ]
}
```

- **Cómo se arman los horarios.** Por cada día del rango y cada horario semanal habilitado de ese día de la semana, se parte de la hora de inicio y se avanza de a `slotDurationMinutes` mientras el horario termine en o antes del cierre (con turnos de 45 minutos entre las 9:00 y las 10:00 entra solo el de las 9:00). Si dos horarios se superponen no se repite el mismo inicio.
- **Qué se resta.** Las ocupaciones de la cátedra, `CONFIRMED` y también `HELD` (un hold puede vencer, pero ofrecerlo terminaría en un `409` al reservar).
- **Horarios pasados.** Se comparan con la hora actual en la zona de atención (`AVAILABILITY_ZONE`), porque la cátedra expresa las horas en hora local.
- **Es una foto.** Entre la consulta y la reserva otro usuario puede tomar el horario: el veredicto real lo da el hold al reservar.
- **Una lista vacía** (`"slots": []`) es una respuesta válida: no hay horarios libres en el rango.

| Respuesta | Cuándo |
| --- | --- |
| `200` | Siempre que se pudo calcular, aunque no haya horarios |
| `400` `INVALID_DATE_RANGE` | `to` es anterior a `from`, o el rango supera `AVAILABILITY_MAX_RANGE_DAYS` días (ambos extremos incluidos). No se llama a nadie |
| `400` `BAD_REQUEST` | Falta un parámetro o no tiene el tipo esperado (`yyyy-MM-dd` para las fechas) |
| `400` `PROFESSIONAL_DISABLED` | El profesional **o su categoría** están deshabilitados. No se consulta a la cátedra |
| `404` `PROFESSIONAL_NOT_FOUND` | Catálogo no conoce al profesional |
| `503` `CATALOG_UNAVAILABLE` / `CATEDRA_UNAVAILABLE` | Alguno de los dos servicios no responde: nunca se devuelve una disponibilidad parcial |

Primero se consulta a catálogo y, solo si el profesional se puede reservar, a la cátedra.

## Iniciar una reserva

`POST /api/reservations` con `{ "professionalId": 15, "date": "2026-10-12", "startTime": "09:30" }` inicia una reserva del usuario del token y la deja esperando el teléfono. Requiere un JWT válido: el usuario sale siempre del token, nunca del cuerpo.

```bash
curl -X POST localhost:8082/api/reservations -H "Authorization: Bearer <id_token>" \
  -H 'Content-Type: application/json' \
  -d '{"professionalId": 15, "date": "2026-10-12", "startTime": "09:30"}'
```

Responde `201` con la reserva en `WAITING_PHONE` (`id`, `status`, el profesional, la fecha y la hora, y `expiresAt`). Nunca expone el usuario dueño ni los ids internos de la cátedra.

1. Valida con catálogo que el profesional exista y esté habilitado (él y su categoría) y que el horario sea un turno de su agenda que todavía no pasó.
2. Pide a catálogo el nombre y el apellido de la cuenta, con el mismo JWT del usuario. Si falta alguno, o tiene menos de 2 caracteres, responde `PATIENT_NAME_REQUIRED` y **no se crea nada**: la app los pide y reintenta.
3. Crea el proceso local, pide el bloqueo temporal del horario (hold) a la cátedra y lo confirma.

Si se pierde la respuesta del hold, no se repite ese pedido (la cátedra no tiene idempotencia): se consultan las ocupaciones y, si el horario figura libre, se pide un hold nuevo. Si el hold vence antes de confirmarse, también se pide uno nuevo. Ambos casos se limitan a `RESERVATION_HOLD_MAX_ATTEMPTS` intentos. Si lo que se pierde es la respuesta de la confirmación, el proceso queda en `HELD` y se responde `503`, porque la cátedra pudo haberlo confirmado.

| Respuesta | Cuándo |
| --- | --- |
| `201` | La reserva quedó esperando el teléfono |
| `400` `VALIDATION_ERROR` | Faltan campos o tienen un formato inválido |
| `400` `PATIENT_NAME_REQUIRED` | La cuenta no tiene nombre o apellido (o tienen menos de 2 caracteres) |
| `400` `INVALID_SLOT` | El horario no está en la agenda del profesional, o ya pasó |
| `400` `PROFESSIONAL_DISABLED` | El profesional o su categoría están deshabilitados |
| `404` `PROFESSIONAL_NOT_FOUND` | Catálogo no conoce al profesional |
| `409` `SLOT_UNAVAILABLE` | El horario está ocupado (nunca se toma como «es mi hold») |
| `409` `RESERVATION_EXPIRED` | El hold venció antes de confirmarse y no se consiguió otro dentro de los intentos |
| `503` `CATALOG_UNAVAILABLE` / `CATEDRA_UNAVAILABLE` | Alguno de los dos servicios no responde |

## Consultar y cancelar reservas

Los tres endpoints requieren un JWT válido y trabajan **solo con las reservas del usuario del token**: una ajena responde `404 RESERVATION_NOT_FOUND`, igual que una inexistente.

```bash
curl "localhost:8082/api/reservations?status=CONFIRMED&page=0&size=20" -H "Authorization: Bearer <id_token>"
curl localhost:8082/api/reservations/101 -H "Authorization: Bearer <id_token>"
curl -X POST localhost:8082/api/reservations/101/cancel -H "Authorization: Bearer <id_token>" \
  -H 'Content-Type: application/json' -d '{"reason": "no puedo asistir"}'
```

- **`GET /api/reservations`** devuelve un arreglo, de la reserva más reciente a la más antigua por fecha y hora del turno (el orden es fijo), con `X-Total-Count` y `Link`. `size` vale 20 por defecto y se reduce al tope `RESERVATION_PAGE_MAX_SIZE` (100). Sin `status` no incluye las reservas `EXPIRED` ni `FAILED` (intentos que no llegaron a ser una reserva); con `status` devuelve exactamente las de ese estado, también `FAILED` o `EXPIRED`.
- **`POST /api/reservations/{id}/cancel`** (el cuerpo y el motivo son opcionales, hasta 500 caracteres) cancela primero en la cátedra y recién entonces en el servicio, de modo que si la cátedra falla nada cambia y se puede repetir. Cancelar una reserva ya cancelada devuelve su estado actual sin llamar a la cátedra.

| Respuesta | Cuándo |
| --- | --- |
| `200` | La lista, la reserva o la reserva ya cancelada |
| `400` `VALIDATION_ERROR` | `status` desconocido, o motivo de más de 500 caracteres |
| `404` `RESERVATION_NOT_FOUND` | No existe o es de otro usuario |
| `409` `RESERVATION_NOT_CANCELLABLE` | La reserva no está confirmada, o la cátedra no permite cancelarla |
| `503` `CATEDRA_UNAVAILABLE` | La cátedra no responde al cancelar |

## Máquina de estados de la reserva

Cada reserva es un proceso local (`reservation_processes`) que avanza por estados mientras REST y Kafka le informan lo que pasa en la cátedra:

```
HOLD_REQUESTED → HELD → WAITING_PHONE → PHONE_QUEUED → PHONE_SUBMITTED → CONFIRMED → CANCELLED
```

Además, los estados intermedios pueden pasar a `EXPIRED` o `FAILED`, y la cátedra puede rechazar el teléfono (`PHONE_SUBMITTED → WAITING_PHONE`). El diagrama completo de transiciones, con sus reglas, está en [docs/maquina-de-estados.md](docs/maquina-de-estados.md).

- **Estados finales** (`EXPIRED`, `FAILED`, `CANCELLED`): inmutables. Un evento tardío o duplicado no los reabre. `CONFIRMED` solo puede pasar a `CANCELLED`.
- **Todo cambio de estado pasa por un solo caso de uso.** Responde `APPLIED` (cambió), `DATA_COMPLETED` (ya estaba en ese estado pero le faltaban datos, por ejemplo el `requestEventId` del pedido de teléfono: se completan sin pisar los que tiene), `ALREADY_IN_STATE` (ya estaba en ese estado y no hay nada que completar: repetir un paso es inocuo) o `REJECTED` (el diagrama no lo permite: se ignora y se registra, sin error). Un proceso final nunca se modifica.
- **Bloqueo optimista.** Si dos operaciones modifican el mismo proceso a la vez, la segunda relee el proceso y decide de nuevo con el estado vigente, hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS` intentos. Es seguro porque los estados solo avanzan; si se agotan los intentos, la operación falla con un conflicto.

## Enviar el teléfono

La cátedra pide un teléfono para confirmar la reserva. Cuando lo pide, la reserva muestra `canSubmitPhone: true` y la app puede habilitar el formulario. El pedido llega por Kafka un instante después de `POST /api/reservations`, por eso hay que esperar esa señal.

```bash
curl -X POST localhost:8082/api/reservations/101/phone -H "Authorization: Bearer <id_token>" \
  -H 'Content-Type: application/json' -d '{"phoneNumber": "+54 9 261 615-9550"}'
```

Responde `202` con la reserva en `PHONE_QUEUED`. El resultado final (confirmada o rechazada) llega después por Kafka y lo aplica el consumer.

- **Formato:** se quitan espacios, guiones, paréntesis y puntos, y queda un `+` opcional y de 7 a 15 dígitos (la regla de la cátedra). Se aceptan `+5492616159550`, `+542616159550`, `2616159550` o `(0261) 615-9550`. El número **se guarda y se envía tal cual queda**, sin completar el código de país ni quitar el `0` o el `15`: `+549...` y `+54...` son números distintos.
- **Outbox:** `PHONE_QUEUED` y el mensaje a publicar se guardan en **una sola transacción** (`outbox_events`), así que o quedan los dos o ninguno. Un **relay** interno, una tarea que corre cada `OUTBOX_RELAY_INTERVAL`, publica los pendientes del más viejo al más nuevo, espera la confirmación de Kafka (`acks=all`, productor idempotente) y recién entonces marca el mensaje como enviado y pasa la reserva a `PHONE_SUBMITTED`. `PHONE_SUBMITTED` significa «el broker lo recibió», no que la cátedra lo aceptó.
- **Fallos:** si Kafka no confirma, el mensaje queda pendiente con un intento más y se reintenta en la vuelta siguiente. Si el relay se cae entre la confirmación y el marcado, el mensaje se reenvía con el **mismo `eventId` y contenido** y la cátedra descarta el duplicado. Si la cátedra contesta antes de que el relay anote la confirmación (la reserva ya está `CONFIRMED`), ese paso se ignora y nada retrocede. Si la reserva ya terminó cuando le toca el turno al mensaje, igual se publica: la cátedra decide qué hacer.
- **Privacidad:** el teléfono nunca se escribe en los logs.

| Respuesta | Cuándo |
| --- | --- |
| `202` | El teléfono quedó en cola |
| `400` `VALIDATION_ERROR` | Falta, está vacío, tiene más de 40 caracteres o no cumple el formato |
| `404` `RESERVATION_NOT_FOUND` | No existe o es de otro usuario |
| `409` `INVALID_RESERVATION_STATE` | La reserva no espera el teléfono, la cátedra todavía no lo pidió, o ya se envió uno |
| `409` `RESERVATION_EXPIRED` | Pasó el vencimiento de la reserva |

## Eventos de la cátedra (Kafka)

La cátedra publica lo que pasa con cada reserva en el topic `CATEDRA_KAFKA_APPOINTMENT_ACTIONS_TOPIC` (la clave del mensaje es el `reservationProcessId`). El servicio los lee y los aplica a la máquina de estados:

| Evento | Efecto en el proceso |
| --- | --- |
| `AdditionalInformationRequested` | Guarda su `eventId` como `requestEventId` (hay que devolverlo con el teléfono) y deja el proceso en `WAITING_PHONE` |
| `AdditionalInformationRejected` | Vuelve a `WAITING_PHONE` (teléfono rechazado) |
| `AppointmentConfirmed` | `CONFIRMED`, con el `reservationId` de la cátedra |
| `AppointmentProcessExpired` | `EXPIRED`, con el motivo |
| `AppointmentProcessInvalid` | `FAILED`, con el motivo |
| `AppointmentCancelled` | `CANCELLED`, con el motivo |

- **Idempotencia:** cada `eventId` procesado se guarda en `processed_events` en la misma transacción que el cambio de estado, así que o quedan las dos cosas o ninguna. La entrega es «al menos una vez»: un evento repetido se ignora.
- **El offset se confirma después del `COMMIT`.** Si el procesamiento falla, no se confirma y se reintenta (`CATEDRA_KAFKA_MAX_RETRIES`, con espera creciente). Agotados los reintentos el mensaje se descarta con un log de error y se sigue; no hay cola de eventos muertos.
- **Choque de versión:** si otra operación modifica el mismo proceso a la vez, se deshace toda la transacción (también el registro del `eventId`) y el evento se repite entero, hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS` intentos.
- **No retroceden estados:** un evento que el diagrama no permite desde el estado actual (uno tardío, o fuera de orden) se ignora, se registra en el log y no es un error. Los estados finales no cambian.
- **Se ignoran y se registran** (para no trabar la lectura): un evento de un proceso que no existe, uno de un tipo que no se conoce y uno repetido. Un mensaje que no es un JSON válido, o al que le falta `eventId`, `eventType` o `reservationProcessId`, se descarta sin reintentos.
- Sin offset guardado para el grupo se empieza por el principio del topic, para no perder lo publicado antes de que el grupo exista.

## Vencimiento de reservas

Una reserva abierta tiene un `expiresAt` (el que informa la cátedra). Si el evento `AppointmentProcessExpired` llega, la reserva pasa a `EXPIRED` por el consumer. Para cuando ese evento se pierde o se atrasa, una **tarea programada** cada `RESERVATION_EXPIRATION_INTERVAL` expira las reservas en `HELD`, `WAITING_PHONE`, `PHONE_QUEUED` o `PHONE_SUBMITTED` cuyo vencimiento pasó hace más que `RESERVATION_EXPIRATION_GRACE`, con el motivo `HOLD_EXPIRED` (el mismo código del evento). El evento y la tarea llevan al mismo estado final y el que llega segundo no cambia nada; el cambio avisa por el stream como cualquier otro.

- **Margen y consulta previa.** Un `EXPIRED` es final: si la confirmación de la cátedra venía en camino, quedaría descartada aunque el turno exista. Por eso hay un margen (60 s) y, antes de expirar una reserva en `PHONE_QUEUED` o `PHONE_SUBMITTED` (las únicas donde el teléfono pudo llegar a la cátedra), se le pregunta con `GET /api/appointments` si ese proceso figura `CONFIRMED`: si es así se repara a `CONFIRMED` con su `reservationId` en vez de expirarla. En `HELD` y `WAITING_PHONE` no se pregunta, porque sin teléfono la cátedra no puede haber confirmado.
- **Ante la duda no se expira.** Si la cátedra no responde, o devuelve más resultados que la página y el proceso no aparece, la reserva queda para la vuelta siguiente. Tras el primer fallo de la cátedra no se vuelve a consultar en esa vuelta; las reservas que no necesitan consulta se expiran igual.
- **Acotada.** Cada vuelta toma como máximo `RESERVATION_EXPIRATION_BATCH_SIZE`, de la más vieja a la más nueva, y cada reserva se cierra en su propia operación: un fallo no frena a las demás. No hay una ejecución especial al arrancar: la primera vuelta ya cubre los vencidos que dejó una caída.
- **Para la app:** entre el `expiresAt` y el cierre pueden pasar de 60 a 90 s en los que la reserva sigue en su estado; la app compara `expiresAt` con la hora. `POST /api/reservations/{id}/phone` ya responde `409 RESERVATION_EXPIRED` en ese lapso.
- **Hilos.** El planificador de Spring Boot tiene un solo hilo por defecto y lo compartirían el relay, el latido del stream y esta tarea; por eso `SCHEDULING_POOL_SIZE` vale 3. El consumer de Kafka procesa de a un evento, Tomcat atiende un hilo por pedido y no hay tareas asincrónicas propias.

## Recuperación al arrancar

Si el servicio se cae a mitad de una reserva, pueden quedar procesos dudosos que ningún evento va a resolver. Al terminar de arrancar (evento `ApplicationReadyEvent`) corre **una vez** la recuperación, en el hilo de arranque y con el servicio ya atendiendo pedidos. Revisa como máximo `RECOVERY_BATCH_SIZE` procesos de cada clase y cierra cada uno en su propia operación: un fallo no frena a los demás.

| Proceso | Qué pudo pasar | Qué hace la recuperación |
| --- | --- | --- |
| `HOLD_REQUESTED` sin `holdId` ni `reservationProcessId` | Se cayó el servicio o se perdió la respuesta del hold: nunca se recibieron los ids. | Pasa a `FAILED` con el motivo `HOLD_RESULT_UNKNOWN` (el mismo de iniciar una reserva). El hold que la cátedra haya creado vence solo. |
| `PHONE_QUEUED` y `PHONE_SUBMITTED` | El `AppointmentConfirmed` no llegó. | Le pregunta a la cátedra con `GET /api/appointments` (`CONFIRMED`, la fecha del turno y el paciente): si el proceso figura confirmado, se repara a `CONFIRMED` con su `reservationId`; si no, se deja como está. |

- **La guarda del arranque.** El servidor web acepta pedidos antes de que se dispare el evento de arranque, así que un `HOLD_REQUESTED` de un pedido en curso se parecería a uno huérfano. Solo se fallan los creados **antes de que arrancara el contexto de esta instancia**.
- **Solo con evidencia.** No se cierra nada porque la cátedra no lo tenga confirmado. Si la cátedra no responde, o devuelve más resultados que la página (100) sin que aparezca el proceso, ese proceso no se toca; tras el primer fallo no se vuelve a consultar. Los huérfanos no necesitan a la cátedra y se fallan igual.
- **Lo que no toca.** `HELD` (el evento `AdditionalInformationRequested` que Kafka reentrega lo pasa a `WAITING_PHONE`, y si no lo expira la tarea de vencimiento), `WAITING_PHONE` (espera al usuario) y `CONFIRMED`.
- **Con el consumer de Kafka.** El consumer repasa su atraso mientras corre la recuperación. Es seguro: todo pasa por el mismo caso de uso de transiciones, con bloqueo optimista; el que llega segundo ve que ya está hecho.
- **Si la cátedra está caída al arrancar,** esos procesos quedan como están (no hay reintentos en bucle). La tarea de vencimiento no los expira por error, porque hace la misma consulta antes, y el próximo arranque lo vuelve a intentar.
- **Mismo mecanismo que el vencimiento.** La consulta «¿la cátedra lo tiene confirmado?» es un componente compartido con la tarea de vencimiento.

## Cambios en tiempo real (SSE)

`GET /api/reservations/stream` abre una conexión que el servidor mantiene abierta y por la que avisa cada cambio de las reservas **del usuario del JWT** (nunca las de otro). Es una optimización: no es la fuente de verdad, así que al conectar o reconectar la app consulta `GET /api/reservations` y refresca.

```bash
curl -N localhost:8082/api/reservations/stream -H "Authorization: Bearer <id_token>" -H "Accept: text/event-stream"
```

```
:ping

event:reservation-updated
id:101-5
data:{"id":101,"status":"WAITING_PHONE","expiresAt":"2026-11-04T13:10:00Z","canSubmitPhone":true,"updatedAt":"2026-11-04T13:00:05Z"}
```

- **Qué avisa:** cada cambio de estado (también los de `HELD` a `WAITING_PHONE`, la confirmación, el vencimiento y la cancelación) y la llegada del pedido de teléfono, que no cambia el estado pero pasa `canSubmitPhone` a `true`. Un evento repetido o un paso que el diagrama rechaza no cambian nada y no avisan.
- **`data`:** un resumen (`id`, `status`, `expiresAt`, `canSubmitPhone`, `updatedAt`); `expiresAt` es `null` si la reserva terminó. El detalle se pide con `GET /api/reservations/{id}`. El `id` del evento es `{reserva}-{versión}` y crece con cada cambio.
- **Después del COMMIT:** el aviso sale cuando el cambio ya quedó guardado, así que al recibirlo la consulta lo ve; un cambio que se deshace no avisa.
- **Sin historia:** un aviso perdido (la app estaba desconectada) no se reenvía y `Last-Event-ID` se ignora; la app reconsulta al reconectar.
- **Latido:** un comentario `:ping` al conectar (para que el cliente reciba las cabeceras) y cada `STREAM_HEARTBEAT_INTERVAL`. El cliente lo ignora.
- **JWT vencido:** el servidor cierra el stream en el primer latido posterior al `exp`. Como la cabecera `200` ya se envió, el cierre no puede ser un `401`: la app lo nota porque el stream termina y debe renovar el token antes de reconectar (el servidor tolera 60 s de desfase de reloj, valor por defecto de Spring: con un token vencido hace menos que eso la conexión se acepta y se cierra de nuevo en el siguiente latido).
- **Límites:** hasta `STREAM_MAX_CONNECTIONS_PER_USER` conexiones por usuario; al abrir una más se cierra la más vieja. Las conexiones viven en memoria de una sola instancia, y al apagar el servicio se cierran todas.

| Respuesta | Cuándo |
| --- | --- |
| `200` `text/event-stream` | La conexión quedó abierta |
| `401` | Falta el token o no es válido |

## Tests

```bash
./mvnw verify
```

Los tests de integración levantan su propio PostgreSQL 18 en un contenedor descartable (Testcontainers), así que solo requieren Docker en ejecución; no usan el `.env` ni la base del compose.

## Decisiones de diseño

El registro de decisiones técnicas, con alternativas descartadas y justificación, está en [docs/decisiones.md](docs/decisiones.md).
