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

## Máquina de estados de la reserva

Cada reserva es un proceso local (`reservation_processes`) que avanza por estados mientras REST y Kafka le informan lo que pasa en la cátedra:

```
HOLD_REQUESTED → HELD → WAITING_PHONE → PHONE_QUEUED → PHONE_SUBMITTED → CONFIRMED → CANCELLED
```

Además, los estados intermedios pueden pasar a `EXPIRED` o `FAILED`, y la cátedra puede rechazar el teléfono (`PHONE_SUBMITTED → WAITING_PHONE`). El diagrama completo de transiciones, con sus reglas, está en [docs/maquina-de-estados.md](docs/maquina-de-estados.md).

- **Estados finales** (`EXPIRED`, `FAILED`, `CANCELLED`): inmutables. Un evento tardío o duplicado no los reabre. `CONFIRMED` solo puede pasar a `CANCELLED`.
- **Todo cambio de estado pasa por un solo caso de uso.** Responde `APPLIED` (cambió), `ALREADY_IN_STATE` (ya estaba en ese estado: repetir un paso es inocuo) o `REJECTED` (el diagrama no lo permite: se ignora y se registra, sin error).
- **Bloqueo optimista.** Si dos operaciones modifican el mismo proceso a la vez, la segunda relee el proceso y decide de nuevo con el estado vigente, hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS` intentos. Es seguro porque los estados solo avanzan; si se agotan los intentos, la operación falla con un conflicto.

## Tests

```bash
./mvnw verify
```

Los tests de integración levantan su propio PostgreSQL 18 en un contenedor descartable (Testcontainers), así que solo requieren Docker en ejecución; no usan el `.env` ni la base del compose.

## Decisiones de diseño

El registro de decisiones técnicas, con alternativas descartadas y justificación, está en [docs/decisiones.md](docs/decisiones.md).
