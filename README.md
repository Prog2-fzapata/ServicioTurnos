<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-sin%20c%C3%B3digo-lightgrey) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey)

**Calidad (7 dimensiones):** sin código en `main` (no evaluable).
<!-- calidad:fin -->

# ServicioTurnos

Servicio de turnos y reservas. Guarda el proceso de cada reserva y lo lleva desde que el usuario elige un horario hasta que la cátedra lo confirma, lo rechaza o vence: consulta a catálogo la agenda de los profesionales, le pide a la cátedra los bloqueos y las confirmaciones, publica el teléfono del paciente y le avisa a la aplicación Android cada cambio.

## Documentos

| Documento | Qué explica |
| --- | --- |
| [Arquitectura](docs/arquitectura.md) | El sistema completo (aplicación Android, los dos servicios, sus bases y la cátedra) y la estructura interna del servicio |
| [Modelo de datos](docs/modelo-de-datos.md) | Las tablas, sus restricciones y la propiedad de los datos |
| [Contrato](docs/contrato.md) | Operaciones, DTO, autenticación, errores y estados de la reserva, y lo que turnos consume de catálogo |
| [Máquina de estados](docs/maquina-de-estados.md) | Las transiciones de una reserva y sus reglas |
| [Idempotencia y recuperación](docs/idempotencia-y-recuperacion.md) | Qué pasa con los mensajes duplicados, perdidos y fuera de orden, y la recuperación tras un reinicio |
| [Resiliencia](docs/resiliencia.md) | Reintentos, timeouts y qué funciona con cada servicio caído |
| [Seguridad](docs/seguridad.md) | Autenticación, autorización y protección de datos |
| [Decisiones de diseño](docs/decisiones/README.md) | Cada decisión técnica, con sus alternativas descartadas y consecuencias |

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
| `SPRING_PROFILES_ACTIVE` | Entorno de ejecución: `dev` o `prod`. Con `prod` se desactiva la documentación interactiva de la API (Swagger UI y `/v3/api-docs`) | `dev` |
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
| `CATALOGO_MAX_ATTEMPTS` | Intentos en total, el primero incluido. `1` desactiva los reintentos. Entre 1 y 10 | `3` |
| `CATALOGO_INITIAL_BACKOFF` | Espera antes del primer reintento; cada uno espera el doble del anterior | `200ms` |
| `CATEDRA_CONNECT_TIMEOUT` | Espera máxima para conectar con la cátedra | `2s` |
| `CATEDRA_READ_TIMEOUT` | Espera máxima por la respuesta de la cátedra | `5s` |
| `CATEDRA_MAX_ATTEMPTS` | Intentos en total, el primero incluido, para las operaciones que se pueden repetir. `1` desactiva los reintentos. Entre 1 y 10 | `3` |
| `CATEDRA_INITIAL_BACKOFF` | Espera antes del primer reintento; cada uno espera el doble del anterior | `200ms` |
| `AVAILABILITY_ZONE` | Zona horaria de atención: en ella se decide qué horarios ya pasaron (la cátedra expresa las horas en hora local) | `America/Argentina/Mendoza` |
| `AVAILABILITY_MAX_RANGE_DAYS` | Días máximos entre `from` y `to` de la disponibilidad, ambos incluidos. Mínimo 1 | `31` |
| `RESERVATION_CONFLICT_MAX_ATTEMPTS` | Intentos en total, el primero incluido, cuando dos actualizaciones del mismo proceso de reserva chocan por la versión (bloqueo optimista). `1` desactiva el reintento. Entre 1 y 10 | `3` |
| `RESERVATION_HOLD_MAX_ATTEMPTS` | Intentos en total, el primero incluido, para conseguir un hold confirmado: cada intento es un hold más su confirmación. Se repite si se perdió la respuesta del hold y el horario figura libre, o si el hold venció antes de confirmarse. `1` desactiva el reintento. Entre 1 y 5 | `2` |
| `CATEDRA_KAFKA_MAX_RETRIES` | Reintentos después del primer intento cuando falla el procesamiento de un evento de Kafka. `0` los desactiva. Entre 0 y 10 | `3` |
| `CATEDRA_KAFKA_INITIAL_BACKOFF` | Espera antes del primer reintento; cada uno espera el doble del anterior | `1s` |
| `OUTBOX_RELAY_INTERVAL` | Cada cuánto mira el relay del outbox los mensajes pendientes de publicar (el teléfono) | `1s` |
| `OUTBOX_RELAY_BATCH_SIZE` | Mensajes que toma el relay por vuelta. Mínimo 1 | `50` |
| `OUTBOX_PUBLISH_TIMEOUT` | Espera máxima por la confirmación de Kafka de un mensaje; si se agota queda pendiente y se reintenta | `10s` |
| `OUTBOX_PUBLISH_BLOCK_TIMEOUT` | Milisegundos que una publicación puede bloquearse esperando los metadatos del broker (`max.block.ms` del productor); sin broker falla en ese tiempo y el mensaje queda pendiente. Se suma a `OUTBOX_PUBLISH_TIMEOUT` | `5000` |
| `DB_CONNECTION_TIMEOUT` | Milisegundos que un pedido espera una conexión del pool de la base antes de fallar con `503` | `5000` |
| `LOG_FORMAT` | Formato de los logs: `ecs` (JSON, por defecto), `logstash` (otro JSON) o vacío para el texto de Spring Boot, que no muestra los campos de cada línea (ver «Logs») | `ecs` |
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

## Documentación de la API

Con el servicio en ejecución, la documentación interactiva está en `http://localhost:8082/swagger-ui.html` y el documento OpenAPI en `http://localhost:8082/v3/api-docs`. Con `SPRING_PROFILES_ACTIVE=prod` ambos quedan desactivados.

Para probar los endpoints desde Swagger UI: pedir un token en catálogo (`POST /api/authenticate`), copiar el `id_token` de la respuesta, pulsar el botón **Authorize** (candado, arriba a la derecha), pegarlo y confirmar. Desde ahí todos los pedidos de la pantalla lo envían.

## Autenticación

Turnos no emite tokens: valida el JWT que emite catálogo (`POST /api/authenticate`) con el mismo secreto. Todos los endpoints, salvo `/actuator/health` y la documentación, exigen `Authorization: Bearer <jwt>`.

```bash
curl -X POST localhost:8081/api/authenticate -H 'Content-Type: application/json' \
  -d '{"username":"maria","password":"clave123","rememberMe":false}'
curl localhost:8082/api/reservations -H "Authorization: Bearer <id_token>"
```

Sin token, o con uno vencido, sin `exp`, `uid` o `auth` (un token sin `exp` no vencería nunca, así que no se acepta), mal formado o firmado con otra clave, la respuesta es `401` en `application/problem+json` con `code` `UNAUTHORIZED` y la cabecera `WWW-Authenticate: Bearer`; no se informa el motivo. Un usuario autenticado sin el rol necesario recibe `403` con `code` `FORBIDDEN`. El usuario de cada pedido sale siempre del token: una reserva ajena responde como si no existiera.

## Endpoints

Todos exigen `Authorization: Bearer <id_token>`, salvo el estado del servicio. Los parámetros, los DTO y los errores de cada uno están en [docs/contrato.md](docs/contrato.md).

| Método y ruta | Acceso | Qué hace |
| --- | --- | --- |
| `GET /api/availability` | JWT | Horarios libres de un profesional entre dos fechas |
| `POST /api/reservations` | JWT | Inicia una reserva: bloquea el horario y lo confirma con la cátedra |
| `GET /api/reservations` | JWT | Lista las reservas del usuario, paginadas |
| `GET /api/reservations/{id}` | JWT | Devuelve una reserva del usuario |
| `POST /api/reservations/{id}/phone` | JWT | Envía el teléfono del paciente |
| `POST /api/reservations/{id}/cancel` | JWT | Cancela una reserva confirmada |
| `GET /api/reservations/stream` | JWT | Stream (SSE) con los cambios de las reservas del usuario |
| `GET /actuator/health` | público | Estado del servicio |

Algunos ejemplos, con el `id_token` que devuelve catálogo:

```bash
# Horarios libres del profesional 15 durante una semana
curl "localhost:8082/api/availability?professionalId=15&from=2026-10-19&to=2026-10-25" \
  -H "Authorization: Bearer <id_token>"
# Iniciar una reserva
curl -X POST localhost:8082/api/reservations -H "Authorization: Bearer <id_token>" \
  -H 'Content-Type: application/json' -d '{"professionalId":15,"date":"2026-10-19","startTime":"09:30"}'
# Enviar el teléfono (cuando canSubmitPhone es true) y cancelar una reserva confirmada
curl -X POST localhost:8082/api/reservations/1/phone -H "Authorization: Bearer <id_token>" \
  -H 'Content-Type: application/json' -d '{"phoneNumber":"+54 9 261 555-1234"}'
curl -X POST localhost:8082/api/reservations/1/cancel -H "Authorization: Bearer <id_token>" \
  -H 'Content-Type: application/json' -d '{"reason":"No puedo asistir"}'
# Seguir los cambios en tiempo real
curl -N localhost:8082/api/reservations/stream -H "Authorization: Bearer <id_token>" -H "Accept: text/event-stream"
```

La lista devuelve un arreglo JSON, con el total en la cabecera `X-Total-Count` y los enlaces a las otras páginas en `Link`.

## Cómo avanza una reserva

1. La app consulta los horarios libres (`GET /api/availability`): la agenda del profesional en catálogo, menos lo que la cátedra informa como ocupado.
2. `POST /api/reservations` valida el horario, pide a la cátedra que lo bloquee, lo confirma y devuelve la reserva en `WAITING_PHONE`.
3. La cátedra avisa por Kafka que necesita el teléfono: `canSubmitPhone` pasa a `true` y la app lo ve en el stream o consultando.
4. `POST /api/reservations/{id}/phone` guarda el teléfono y el mensaje del outbox en la misma transacción (`PHONE_QUEUED`); un relay lo publica en Kafka (`PHONE_SUBMITTED`).
5. La cátedra confirma por Kafka (`CONFIRMED`) o rechaza el teléfono (vuelve a `WAITING_PHONE`). Una reserva confirmada se puede cancelar (`CANCELLED`).
6. Si algo no llega a tiempo, la reserva vence (`EXPIRED`) o falla (`FAILED`).

El estado solo avanza: un evento tardío o repetido nunca lo hace retroceder. Los estados y sus transiciones están en [docs/maquina-de-estados.md](docs/maquina-de-estados.md) y en [docs/contrato.md](docs/contrato.md#estados); qué pasa con los mensajes duplicados, perdidos y fuera de orden y cómo se recupera el servicio tras un reinicio, en [docs/idempotencia-y-recuperacion.md](docs/idempotencia-y-recuperacion.md).

Si catálogo, la cátedra, Kafka o la base no responden, lo que depende solo de datos propios sigue funcionando (por ejemplo, listar las reservas). Cada reintento y cada tiempo de espera tiene su tope, y lo que funciona con cada servicio caído está en [docs/resiliencia.md](docs/resiliencia.md).

## Logs

El servicio escribe en la salida estándar (`docker compose logs -f turnos`) **una línea de JSON por evento** (formato ECS de Spring Boot), a nivel `INFO`. Cada línea de negocio tiene un mensaje corto y estable, y sus datos como **campos**, así se filtra por campo sin depender de la redacción:

```json
{"@timestamp":"2026-11-07T14:56:27Z","log":{"level":"INFO","logger":"…ReservationActionLogger"},"service":{"name":"turnos"},
 "message":"Reserva iniciada","reservationId":12,"userId":"42","status":"WAITING_PHONE"}
```

| Campo | Qué es |
| --- | --- |
| `reservationId` | El id local de la reserva, el mismo `id` que devuelve la API |
| `userId` | El id interno del usuario (nunca su nombre ni su teléfono) |
| `status`, `from`, `to` | El estado en que quedó una reserva, o de cuál a cuál pasó |
| `eventType`, `eventId` | El evento de la cátedra (o el mensaje del teléfono) que cambió la reserva |
| `holdId`, `reservationProcessId` | Los ids de la cátedra: el bloqueo del horario y su proceso |
| `attempt` | El número de intento de una publicación en Kafka |
| `status`, `code` | En un error de catálogo o de la cátedra: el status HTTP y, si lo hay, el `code` del error |
| `path`, `exception` | En un error propio: la ruta del pedido y el nombre de la clase de la excepción |

Para filtrar: `docker compose logs --no-log-prefix turnos | jq -c 'select(.reservationId == 12)'`. Para leerlas a ojo, este filtro deja una línea corta por evento (salta lo que no es JSON):

```bash
docker compose logs -f --no-log-prefix turnos | jq -R -r 'fromjson? | [.["@timestamp"][11:19], .log.level, .message,
  (del(.["@timestamp"], .log, .process, .service, .ecs, .message, .tags, .error) | to_entries | map("\(.key)=\(.value)") | join(" ")),
  (if .error then "(\(.error.type): \(.error.message))" else "" end)] | map(select(. != "")) | join("  ")'
```

Todas las líneas de log se escriben igual (mensaje fijo y datos como campos), y `LogStyleTest` falla si alguien escribe una de otra forma. `LOG_FORMAT` cambia el formato: `logstash` es otro JSON, y vacío vuelve al texto de Spring Boot, que **no muestra los campos** (solo el mensaje).

| Se registra | Mensaje y campos |
| --- | --- |
| Lo que hace el usuario por HTTP: iniciar, enviar el teléfono y cancelar | `Reserva iniciada`, `Teléfono recibido`, `Reserva cancelada`: `reservationId`, `userId`, `status` |
| Lo que la cátedra aceptó (hold creado, confirmación y cancelación; las consultas no dejan línea) | `La cátedra creó el hold`, `La cátedra aceptó confirmar el hold`, `La cátedra canceló la reserva`: `holdId`, `reservationProcessId` |
| Cada transición por un evento o por el relay, y por qué se ignoró | `Transición aplicada` (`from`, `to`), `Sin cambios, ya estaba en ese estado`, `Transición ignorada, el diagrama no la permite`: `eventType`, `eventId`, `reservationId`, `userId` |
| El pedido de teléfono de la cátedra, que no cambia el estado | `Pedido de teléfono registrado`: `eventType`, `eventId`, `reservationId`, `userId` |
| Eventos duplicados, de una reserva desconocida o de un tipo que no se trata | `Evento ya procesado, se ignora`, `Evento de una reserva desconocida, se ignora`, `Tipo de evento que no se trata, se ignora` |
| El teléfono que Kafka no confirmó (solo ids y el intento, nunca el número) | `Kafka no confirmó el teléfono, queda pendiente`: `eventId`, `reservationProcessId`, `attempt` |
| Cada vuelta del vencimiento y de la recuperación, con lo que dudó | `Vencimiento: …` y `Recuperación: …`: `reservationId`, `status` |
| Las respuestas inesperadas de catálogo y de la cátedra, **solo con el status y el código** | `La cátedra respondió con un error del servidor` (`status`), `La cátedra rechazó el JWT técnico, revisar CATEDRA_JWT` (`status`), `La cátedra respondió con un error que turnos no esperaba` (`status`, `code`), `La cátedra no respondió` (`exception`), y los equivalentes de catálogo |
| La base de datos caída | `La base de datos no está disponible`: `path`, `exception` |
| Los errores inesperados, con su traza | `Error inesperado`: `path`, y la excepción en `error` |
| Una tarea que falla, o la recuperación al arrancar | `El relay del outbox falló; se reintenta en la próxima vuelta`, y los equivalentes (mensaje fijo y la excepción en `error`) |

**Nunca se escribe** el JWT del usuario, el JWT técnico de la cátedra, el secreto de firma, la contraseña de la base, el teléfono, el nombre del paciente ni el motivo de una cancelación. Lo comprueba `SecretsNotInLogsIntegrationTest`, que corre los flujos reales con PostgreSQL y Kafka en contenedores y un catálogo y una cátedra simulados que repiten los secretos en sus errores, y `DatabaseDownIntegrationTest`, con la base caída. Cómo se garantiza está en [docs/seguridad.md](docs/seguridad.md).

No hay contexto por pedido (MDC) ni métricas propias: el id de la reserva va en cada línea y alcanza para seguir un flujo. Las líneas del vencimiento y de la recuperación llevan la reserva pero no el usuario, porque sus resultados no lo traen. El estado del servicio es `/actuator/health`, y es lo único de `actuator` que se expone.

## Tests

```bash
./mvnw verify
```

Corre las pruebas unitarias y de integración y genera el informe de cobertura (JaCoCo) en `target/site/jacoco/index.html`. Los tests de integración levantan su propio PostgreSQL 18 y, según el caso, un Kafka en contenedores descartables (Testcontainers), así que solo requieren Docker en ejecución; no usan el `.env` ni la base del compose. La primera vez se descargan las imágenes.

Para correr una clase o un método:

```bash
./mvnw test -Dtest=StartReservationIntegrationTest
./mvnw test -Dtest='ReservationsQueryIntegrationTest#listsOnlyTheReservationsOfTheUserNewestFirst'
```

Qué clases cubren cada comportamiento que pide el enunciado:

| Comportamiento | Clases de test principales |
| --- | --- |
| Autenticación y autorización | `SecurityRejectionTest`, `SecurityIntegrationTest`, `JwtConfigurationTest`, `RequiredClaimsValidatorTest` |
| Disponibilidad | `AvailabilityIntegrationTest`, `GetAvailabilityUseCaseImplTest`, `AvailabilityControllerTest` |
| Iniciar una reserva | `StartReservationIntegrationTest`, `StartReservationUseCaseImplTest`, `ReservationControllerTest` |
| Máquina de estados y bloqueo optimista | `ReservationStatusTest`, `ApplyReservationTransitionUseCaseImplTest` |
| Consulta y cancelación, sin acceso cruzado entre usuarios | `ReservationsQueryIntegrationTest`, `GetReservationUseCaseImplTest`, `ListReservationsUseCaseImplTest`, `CancelReservationUseCaseImplTest` |
| Eventos de la cátedra e idempotencia | `ReservationFlowIntegrationTest`, `CatedraEventListenerTest`, `ProcessCatedraEventUseCaseImplTest`, `CatedraEventMapperTest` |
| Teléfono y outbox | `PhoneOutboxIntegrationTest`, `PhoneRelaySchedulerIntegrationTest`, `SubmitPhoneUseCaseImplTest`, `PublishPendingPhonesUseCaseImplTest`, `MarkPhoneSentUseCaseImplTest` |
| Vencimiento y recuperación | `ReservationExpirationIntegrationTest`, `ExpirationSchedulerIntegrationTest`, `ReservationRecoveryIntegrationTest`, `RecoveryStartupIntegrationTest`, `CatedraReconciliationOverHttpIntegrationTest` |
| Cambios en tiempo real (SSE) | `ReservationStreamHttpIntegrationTest`, `ReservationStreamIntegrationTest`, `ReservationStreamRegistryTest` |
| Clientes de catálogo y de la cátedra | `CatalogClientAdapterTest`, `CatedraApiClientAdapterTest` |
| Degradación con servicios caídos | `CatalogDownIntegrationTest`, `ServicesDownIntegrationTest`, `DatabaseDownIntegrationTest` |
| Esquema de la base | `TurnosSchemaTest` |
| Logs sin secretos | `SecretsNotInLogsIntegrationTest`, `LogStyleTest` |

El catálogo de profesionales, el registro de usuarios y el JWT son del servicio de catálogo.
