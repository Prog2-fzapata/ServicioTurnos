# Decisiones de diseño: servicio de turnos

Registro de las decisiones técnicas de este servicio: contexto, decisión, alternativas descartadas y justificación. Cada entrada se agrega cuando la decisión queda implementada en el código.

## 1. Pruebas de integración con Testcontainers

- **Contexto:** `TurnosApplicationTests` y los tests que levantan el contexto completo necesitan una base PostgreSQL. Hasta ahora dependían del `.env` o del servicio de base del workflow de CI.
- **Decisión:** cada ejecución de pruebas levanta un PostgreSQL 18 en un contenedor descartable (Testcontainers), conectado con `@ServiceConnection`. Es la misma imagen que usa el compose.
- **Alternativas descartadas:**
  - Usar la base del compose: los tests compartirían datos con el desarrollo manual.
  - Una base en memoria (H2): su SQL difiere de PostgreSQL y un test podría pasar con un comportamiento que en producción falla.
- **Consecuencias:** los tests solo requieren Docker en ejecución y no usan el `.env`. El workflow de CI conserva su servicio de base, que ya no se usa para los tests y se podrá quitar.

## 2. Validación del JWT con el secreto compartido (HS512)

- **Contexto:** el JWT lo emite el servicio de catálogo y turnos debe confiar en él sin consultarle a catálogo en cada pedido.
- **Decisión:** turnos valida la firma con el mismo secreto HMAC que catálogo (`JWT_SECRET`, Base64, al menos 512 bits). El `JwtDecoder` solo acepta **HS512**, así que un token firmado con otro algoritmo se rechaza aunque la clave sea la correcta. Si la variable falta, no es Base64 o es corta, el servicio no arranca. Además de la firma, Spring controla la vigencia (`exp`, `nbf`). Turnos no tiene encoder: no emite tokens.
- **Alternativas descartadas:**
  - Clave pública (RSA): turnos no podría fabricar tokens, pero hay que generar y distribuir archivos PEM; con dos servicios propios en el mismo entorno ese riesgo no justifica la complejidad.
  - Consultar a catálogo en cada pedido: acopla la disponibilidad de turnos a la de catálogo.
- **Consecuencias:** quien conozca la clave puede fabricar tokens, así que los dos `.env` deben cuidarse igual. Un token no se puede revocar antes de que venza.

## 3. Claims obligatorios: `uid` y `auth`

- **Contexto:** un token con firma válida pero sin `uid` (o sin autoridades) no puede venir de catálogo; dejarlo pasar terminaría en un error `500` más adelante, cuando un caso de uso busque el usuario.
- **Decisión:** `RequiredClaimsValidator` rechaza con `401` los tokens sin `uid` o sin `auth`. Los casos de uso reciben el `uid` como parámetro, que el controller lee del JWT (`@AuthenticationPrincipal Jwt`); nunca sale del cuerpo ni de la URL. No se exige un rol concreto: hoy todos los usuarios tienen `ROLE_USER` y lo que se aísla es el acceso por usuario, con el `uid`.
- **Alternativa descartada:** un puerto `out` que devuelva «el usuario actual»: oculta de dónde sale el dato y obliga a que el dominio dependa del contexto de seguridad.

## 4. La seguridad se copia de catálogo en lugar de compartirse

- **Contexto:** `SecurityConfiguration`, `JwtConfiguration`, `ProblemDetailsSecurityHandler` y los errores compartidos son prácticamente iguales en los dos servicios.
- **Decisión:** se copian a `shared.security` y `shared.web` de turnos, adaptando el paquete y quitando lo que turnos no usa (encoder, vigencias, endpoints públicos). La repetición es a propósito.
- **Alternativa descartada:** una librería compartida. Los servicios tienen repositorios y despliegues independientes, y una librería obliga a publicar un artefacto y a versionarlo en dos repos para unas pocas clases.
- **Consecuencias:** un cambio de seguridad hay que aplicarlo en los dos servicios.

## 5. Respuestas 401 y 403, seguridad por método y botón «Authorize»

- **Decisión:** igual que en catálogo (entrada 17 de su `docs/decisiones.md`): `ProblemDetailsSecurityHandler` escribe el `401` y el `403` de los filtros en `application/problem+json` con `code` `UNAUTHORIZED` y `FORBIDDEN`, sin revelar si el token venció, está mal firmado o falta (`WWW-Authenticate: Bearer`). `@EnableMethodSecurity` permite `@PreAuthorize`, y `GlobalExceptionHandler` traduce `AccessDeniedException` a `403` (sin eso daba `500`). `OpenApiConfiguration` declara el esquema Bearer JWT para que Swagger UI muestre «Authorize»; el token se obtiene en catálogo (`POST /api/authenticate`).
- **Rutas públicas:** solo `/actuator/health` y la documentación. Todo lo demás exige token, también las rutas que no existen.
- **CSRF desactivado:** la API no usa sesión ni cookies, cada pedido trae su JWT en `Authorization`, así que no hay nada que falsificar entre sitios. Sonar (`java:S4502`) lo marca igual, por eso la línea lleva `NOSONAR` con su motivo.
- **Pruebas:** `SecurityRejectionTest` usa un controller de prueba (turnos todavía no tiene endpoints) para el `401` (sin token, mal formado, vencido, otra clave, otro algoritmo, cuerpo alterado, sin `uid`, sin `auth`), el `403` por rol y la lectura del `uid` desde el token. Se comprobó que falla al quitar el validador de claims, al aceptar HS256, al quitar `@EnableMethodSecurity` y al abrir `anyRequest()`.

## 6. Esquema de la base de turnos (migración `V1`)

- **Contexto:** el servicio guarda el proceso de cada reserva, los mensajes que debe publicar en Kafka y los eventos que ya procesó. Hay que decidir nombres, qué datos son opcionales, qué reglas impone la base y qué índices llevan las consultas conocidas.
- **Decisión:**
  - **Tres tablas, en plural** (convención de la cátedra): `reservation_processes`, `outbox_events` y `processed_events`. Sin claves foráneas hacia otras bases: `user_id`, `professional_id` y los nombres del profesional se guardan **por valor**, como datos históricos para mostrar la reserva.
  - **Los ids de la cátedra son opcionales.** El proceso local se crea en `HOLD_REQUESTED` *antes* de llamar a la cátedra, así que `reservation_process_id` y `hold_id` aceptan `NULL` hasta que el hold responde. El `UNIQUE` sobre `reservation_process_id` sigue evitando reservas duplicadas, porque PostgreSQL permite varios `NULL` en una columna única. La clave primaria es un `id` propio generado por la base.
  - **Obligatorio y opcional.** Son obligatorios el dueño, el profesional y su nombre, el horario, el estado, el nombre y apellido del paciente, `version` (por defecto 0) y las fechas de auditoría. Son opcionales el teléfono, `expires_at`, `request_event_id`, `reservation_id`, `failure_reason`, `cancellation_reason` y `cancelled_at`.
  - **Reglas en la base** (`CHECK`): `status` solo admite los nueve estados de la máquina de estados, el estado del outbox solo `PENDING` o `SENT`, y un turno termina después de empezar.
  - **Largos** tomados del contrato de la cátedra: 100 para `user_id` (el `externalPatientId`) y los ids de la cátedra, 100 para nombre y apellido del paciente, 20 para el teléfono y 500 para el motivo de cancelación.
  - **Outbox.** `outbox_events.reservation_process_id` es una clave foránea hacia el id de la cátedra de `reservation_processes`, y `message_key` guarda el mismo valor (la key que exige la cátedra). `event_id` es único.
  - **Índices.** `user_id` (toda consulta y cancelación filtra por dueño), `(status, expires_at)` (vencimiento y recuperación al arrancar), `outbox_events(reservation_process_id)` (PostgreSQL no indexa las claves foráneas) y un índice **parcial** sobre `created_at` solo para las filas `PENDING`, que es lo único que lee el relay.
- **Alternativas descartadas:**
  - Estado libre, validado solo en Java: un error de tipeo (`CONFIRMD`) quedaría guardado en silencio.
  - Clave foránea del outbox hacia el `id` propio: la fila no traería su message key y habría que copiarla.
  - Índice común sobre `outbox_events(status)`: crece con todas las filas ya enviadas, que el relay nunca lee.
  - Un `UNIQUE` local sobre profesional, fecha y hora: la cátedra ya responde `409` ante un horario ocupado, y una restricción local bloquearía los reintentos tras un `FAILED`.
  - Entidades y dominio en este paso: se definen con la máquina de estados, cuando se sepa qué necesita cada caso de uso.
- **Consecuencias:** agregar un estado nuevo exige una migración que cambie el `CHECK` (y el largo de la columna, si el nombre es más largo que el actual, de 15 caracteres). Aún no hay clases Java que usen las tablas. `TurnosSchemaTest` prueba el esquema con PostgreSQL real, y se verificó rompiendo la migración a propósito en nueve puntos.

## 7. Cliente de catálogo: JWT del usuario, reintentos y errores

- **Contexto:** antes de iniciar una operación, turnos necesita la agenda vigente de un profesional, que solo conoce catálogo (`GET /api/professionals/{id}`). El enunciado pide que, si catálogo no está disponible, no se inicien operaciones que necesiten información vigente, y que lo demás siga funcionando.
- **Decisión:**
  - **El JWT del usuario viaja con cada pedido.** Un interceptor del cliente HTTP (`JwtPropagationInterceptor`, en `shared.security`) lee el token del usuario autenticado y lo agrega como `Authorization: Bearer`. El puerto de salida `CatalogClient.findProfessional(id)` no menciona tokens: es un detalle de HTTP que no debe llegar al dominio. Si no hay un usuario con JWT, el pedido ni se hace (`IllegalStateException`): todo pedido a turnos exige token, así que sería un error de programación.
  - **Reintentos con `RetryTemplate`** de Spring Framework 7 (sin dependencias nuevas): hasta 3 intentos en total, con una espera que se duplica (200 ms, 400 ms). Solo se reintenta ante `CatalogUnavailableException` (conexión caída, timeout o 5xx); un 4xx nunca. Es seguro repetir porque la consulta es un `GET`.
  - **Qué sale en cada caso.** `404` devuelve `Optional.empty()` (el caso de uso decidirá `PROFESSIONAL_NOT_FOUND`). 5xx, timeout o conexión caída, agotados los reintentos, lanzan `CatalogUnavailableException`, que `ReservaExceptionHandler` responde como `503 CATALOG_UNAVAILABLE`. Cualquier otro 4xx (`401` por un secreto distinto, `403`...) o un cuerpo ilegible lanzan `CatalogResponseException`, que sale como `500` genérico: es un desajuste entre servicios, no una caída. El log registra solo el status; nunca el cuerpo de la respuesta ni el JWT.
  - **Configuración (criterio de la decisión del profesor sobre parámetros de ajuste).** `CATALOGO_BASE_URL` es obligatoria y sin valor por defecto (es una dirección de despliegue). `CATALOGO_CONNECT_TIMEOUT` (2 s), `CATALOGO_READ_TIMEOUT` (3 s), `CATALOGO_MAX_ATTEMPTS` (3) y `CATALOGO_INITIAL_BACKOFF` (200 ms) tienen valor por defecto en `application.properties`, y si la variable está definida en el entorno, esa gana. El compose las lista sin valor.
  - **Llegar a catálogo desde el contenedor.** Los dos servicios corren en composes separados y catálogo publica su puerto en el host, así que el `.env.example` usa `http://host.docker.internal:8081` y el compose agrega `extra_hosts: host.docker.internal:host-gateway` (en Linux ese nombre no existe sin eso). Fuera de Docker se pisa con `http://localhost:8081`.
  - **Modelo propio.** `CatalogProfessional` y `CatalogSchedule` son del dominio de turnos, no copias de las clases de catálogo: los servicios son independientes y el DTO ignora los campos que no conoce (`@JsonIgnoreProperties(ignoreUnknown = true)`), así que un campo nuevo en catálogo no rompe a turnos.
- **Alternativas descartadas:**
  - Pasar el token como parámetro por todas las capas: contamina el dominio y los casos de uso con un detalle de HTTP.
  - Un JWT técnico propio de turnos para hablar con catálogo: catálogo no puede saber quién es el usuario, y el contrato pide el del usuario.
  - Resilience4j o `spring-retry`: una dependencia más para algo que Spring ya trae.
  - Tratar todo error de catálogo como "no disponible": esconde los desajustes de configuración (un secreto distinto parecería una caída).
  - Una red de Docker compartida entre los dos composes: acopla dos repositorios independientes.
  - Un circuit breaker: queda para la degradación completa (reintentos, timeouts y degradación).
- **Consecuencias:** con los valores por defecto, un pedido que encuentre a catálogo caído espera hasta unos 10 segundos antes de recibir el `503` (3 intentos de 3 s más las esperas). Hay que bajar `CATALOGO_READ_TIMEOUT` o `CATALOGO_MAX_ATTEMPTS` si esa espera es demasiado larga para la app. Todavía no hay caso de uso que llame al puerto: llegan con la disponibilidad y el inicio de reserva. Los tests usan un servidor HTTP real y local (el de JDK) para probar timeouts, cortes de conexión y la cantidad exacta de intentos, y se verificó rompiendo el código a propósito en trece puntos.

## 8. Cliente de la cátedra: JWT técnico, errores y reintentos por operación

- **Contexto:** turnos consulta ocupaciones y crea, confirma, lista y cancela reservas en el servicio central de la cátedra. Dos hechos condicionan el diseño: la cátedra **no tiene idempotencia** en la creación de holds ni permite recuperar los ids de un hold (confirmado por el profesor), y un timeout no prueba que la operación haya fallado.
- **Decisión:**
  - **Un puerto y un adapter.** `CatedraApiClient` (puerto de salida) con cinco operaciones y `CatedraApiClientAdapter`, con modelos propios del dominio de turnos. Usa el **JWT técnico del proyecto** (`CATEDRA_JWT`, mismo que catálogo), no el del usuario: la cátedra no conoce a los usuarios finales; el dueño de la reserva viaja como `externalPatientId`.
  - **Reintentos distintos por operación.** Las lecturas, confirmar y cancelar se pueden repetir sin riesgo (confirmar responde `RESERVATION_PROCESS_ALREADY_CONFIRMED` si ya estaba; cancelar es idempotente por contrato) y se reintentan ante conexión caída, timeout o 5xx: 3 intentos en total, con una espera de 200 ms que se duplica. **Crear un hold no se reintenta** ante un resultado desconocido (timeout de lectura, corte o 5xx), porque el hold pudo haberse creado y repetir el POST chocaría con él sin poder reconocerlo. Solo se repite si la conexión nunca se estableció: nada se envió, así que se sabe que no hay hold.
  - **Dos niveles de «no disponible».** `CatedraNotReachedException` (nunca se conectó: el resultado es conocido) es un caso particular de `CatedraUnavailableException` (resultado desconocido). Los dos salen como `503 CATEDRA_UNAVAILABLE`; el caso de uso es quien necesita distinguirlos. Ante un resultado desconocido al crear un hold, el proceso pasa a `FAILED` y, antes de crear otro hold, se consultan las ocupaciones (lo implementan TUR-08, TUR-15 y TUR-16).
  - **Errores por status y code, nunca por el texto.** Un rechazo funcional de la matriz del §13 sale como `CatedraRejectedException` con un motivo (`CatedraRejection`); cada caso de uso decide qué hacer. Un `409 SLOT_ALREADY_HELD` nunca se interpreta como «es mi hold». `401` y `403` (JWT técnico vencido, de otra cuenta, o códigos de propiedad como `HOLD_OWNER_MISMATCH`) son `CatedraAuthenticationException`; cualquier otro `4xx` o un cuerpo ilegible, `CatedraResponseException`. Ambos salen como `500` genérico: son desajustes de configuración o de contrato, no caídas. Una combinación de status y code que no está en la matriz no se trata como rechazo.
  - **Lo que nunca llega a un mensaje ni a un log:** el cuerpo de la respuesta, el JWT técnico, y el nombre o el teléfono del paciente. Del cuerpo de un error solo se lee el `code`, y solo si tiene la forma de un code (mayúsculas y guiones bajos), para que no se pueda inyectar texto en un log. Las propiedades ocultan el JWT en su `toString`.
  - **Formatos.** La hora del hold se envía como `HH:mm` (como el ejemplo del contrato); al leer se aceptan `HH:mm` y `HH:mm:ss`. Los campos desconocidos se ignoran. Un estado de ocupación que el contrato no define es un error de respuesta. El listado exige `X-Total-Count`: un total inventado engañaría a quien pagina. El id de un hold se codifica en la ruta.
  - **Configuración (criterio de parámetros de ajuste).** `CATEDRA_BASE_URL` y `CATEDRA_JWT` obligatorias y sin valor por defecto. `CATEDRA_CONNECT_TIMEOUT` (2 s), `CATEDRA_READ_TIMEOUT` (5 s), `CATEDRA_MAX_ATTEMPTS` (3) y `CATEDRA_INITIAL_BACKOFF` (200 ms) tienen valor por defecto y el entorno gana. El compose las lista sin valor. Los timeouts son más cortos que en catálogo (3 s y 10 s) porque en turnos hay un usuario esperando la respuesta.
- **Alternativas descartadas:**
  - Reintentar todo igual: duplicaría holds o produciría un `409` contra el propio hold sin poder distinguirlo.
  - Tratar un `409 SLOT_ALREADY_HELD` posterior a un timeout como éxito: no hay forma de verificar que el hold sea nuestro ni de recuperar sus ids.
  - Que el adapter consulte las ocupaciones y repita el hold: es una política de negocio que combina dos operaciones y pertenece a los casos de uso, no al adapter.
  - Un puerto por operación: más interfaces sin beneficio; todas hablan con el mismo sistema externo.
  - Reutilizar las excepciones y la configuración del cliente de catálogo: la política de reintentos y el significado de los errores son distintos.
- **Consecuencias:** con los valores por defecto una lectura con la cátedra caída espera hasta unos 16 segundos antes del `503` (3 intentos de 5 s más las esperas), y un hold, hasta 5 segundos. Tras una respuesta perdida al crear un hold, el horario puede quedar bloqueado hasta unos 10 minutos (el TTL de la cátedra), también para el mismo usuario. Todavía no hay casos de uso que llamen al puerto: llegan con la disponibilidad, el inicio de reserva, la recuperación y la cancelación. Los tests usan un servidor HTTP real y local, y se verificó rompiendo el código a propósito en veinte puntos.

## 9. Disponibilidad: cálculo de slots, ocupaciones, zona horaria y categoría deshabilitada

- **Contexto:** `GET /api/availability` debe devolver los horarios libres de un profesional en un rango de fechas. La agenda vive en catálogo (horarios semanales) y lo ocupado, en la cátedra (reservas confirmadas y holds activos). Las horas de la cátedra son locales de atención, sin zona horaria.
- **Decisión:**
  - **Generación de slots.** Para cada día del rango y cada horario semanal habilitado de ese día de la semana se parte de `startTime` y se avanza de a `slotDurationMinutes` mientras el slot termine en o antes de `endTime`. Los horarios que se superponen se deduplican por fecha e inicio. Un horario con duración cero o negativa se ignora, porque no avanzaría nunca. La cuenta se hace en segundos desde el inicio hasta el cierre, no sumando a la hora, porque sumar pasada la medianoche vuelve a las 00:00 y el bucle no terminaría.
  - **Ocupaciones.** Se restan `CONFIRMED` y `HELD`. La respuesta es una foto: lo decisivo ocurre al crear el hold.
  - **Horarios pasados y zona.** Un slot es pasado si su inicio es anterior a «ahora» en la zona de atención. Un `Clock` inyectable lleva la zona (`AVAILABILITY_ZONE`, por defecto `America/Argentina/Mendoza`); los tests lo reemplazan por uno fijo.
  - **Rango.** `to` no anterior a `from` y, como máximo, `AVAILABILITY_MAX_RANGE_DAYS` (31) días contando ambos extremos. Ambos son parámetros de ajuste: valor por defecto en `application.properties` y el entorno gana. Se valida en `AvailabilityQueryMapper`, antes de llamar a catálogo o a la cátedra.
  - **Categoría deshabilitada.** Bloquea a sus profesionales (cada profesional tiene una sola categoría). Para saberlo, turnos lee `categoryEnabled` en `GET /api/professionals/{id}`; si catálogo no lo informa, turnos lo trata como un desajuste entre servicios (`500` genérico y sin reintentos) en lugar de suponer un valor. Un profesional o una categoría deshabilitados responden `400 PROFESSIONAL_DISABLED`, el mismo código que usa la cátedra, y no se consulta a la cátedra.
  - **Orden y fallos.** Primero catálogo y, si el profesional se puede reservar, la cátedra, en secuencia. Si cualquiera falla, `503` con su código: no se devuelve una disponibilidad parcial.
  - **Capas.** El caso de uso devuelve `Optional` (vacío si el profesional no existe) y lanza la excepción de profesional deshabilitado, que es una regla de negocio que decide con el profesional que ya trajo. `ReservaService` traduce el vacío en `404`.
- **Alternativas descartadas:**
  - No bloquear por categoría deshabilitada, como hace la cátedra: se prefirió que no se ofrezcan horarios de una categoría apagada.
  - Que turnos consulte `GET /api/professional-categories` aparte: no hay endpoint por id, sería una llamada más por consulta y otro motivo de `503`.
  - Que `enabled` del profesional pase a ser «profesional Y categoría habilitados»: se pierde saber qué se apagó y cambia el significado de un campo que ya está en `main`.
  - Restar solo `CONFIRMED`: ofrecería horarios que fallan con `409` al reservar.
  - Consultar catálogo y cátedra en paralelo: llamaría a la cátedra por profesionales que no existen o están deshabilitados.
  - Medir «ahora» en UTC: Mendoza va 3 horas atrás y se descartarían o dejarían pasar horarios cercanos a la medianoche.
  - Un código nuevo para la categoría (`CATEGORY_DISABLED`): el cliente no necesita distinguir el motivo y se mantiene el código del contrato.
- **Consecuencias:** hasta que catálogo publique `categoryEnabled`, turnos responde `500` a toda consulta de disponibilidad contra un catálogo viejo; los dos servicios se despliegan en ese orden. Entre la consulta y el hold el horario puede dejar de estar libre. Los tests cubren el cálculo con reloj fijo, el contrato HTTP y el flujo completo contra un catálogo y una cátedra simulados, y se verificó rompiendo el código a propósito en veinte puntos.

## 10. Máquina de estados de la reserva: transiciones, resultado y bloqueo optimista

- **Contexto:** una reserva recorre varios estados mientras REST y Kafka le llegan en momentos distintos y a veces a la vez. Hace falta decidir qué pasos son válidos, qué pasa con un paso inválido o repetido, y cómo evitar que dos operaciones se pisen. Las transiciones y sus reglas están en [maquina-de-estados.md](maquina-de-estados.md).
- **Decisión:**
  - **Transiciones.** `ReservationStatus` define los 9 estados y `canTransitionTo` la tabla del diagrama. `EXPIRED`, `FAILED` y `CANCELLED` son finales; `CONFIRMED` solo puede pasar a `CANCELLED`. Se agregaron dos pasos que el diagrama no tenía, `PHONE_QUEUED → CONFIRMED` y `PHONE_QUEUED → WAITING_PHONE`: la cátedra puede responder antes de que el relay anote que el teléfono se envió, y sin ellos un evento válido se ignoraría.
  - **Un solo lugar donde se cambia el estado.** `ApplyReservationTransitionUseCase` recibe el id del proceso y una `ReservationTransition` (estado destino y datos opcionales). Copia solo los datos presentes, actualiza `updatedAt` y, al cancelar, `cancelledAt`.
  - **Resultado, no excepción.** `APPLIED`; `ALREADY_IN_STATE` si ya estaba en el destino (se mira antes que la tabla, así repetir un paso es inocuo); `REJECTED` si el diagrama no lo permite (se ignora y no se escribe nada). Un proceso inexistente devuelve vacío y `ReservaService` lo traduce a una excepción. Cada llamador decide qué hacer con `REJECTED` (por ejemplo, la cancelación lo convertirá en `409`).
  - **Bloqueo optimista.** La columna `version` de la migración V1 es la `@Version` de la entity. Si otra operación cambió el proceso entre la lectura y el guardado, el adapter lanza `ReservationConflictException`; el caso de uso relee el proceso y vuelve a decidir sobre el estado vigente, hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS` intentos en total (3 por defecto, mínimo 1, el entorno gana). Agotados los intentos, relanza el conflicto.
  - **Capas.** El modelo no tiene lógica; la política de reintento llega al caso de uso como un registro del dominio (`ConflictRetryPolicy`) que arma la configuración. El caso de uso no loguea: `TransitionOutcomeLogger`, en infraestructura, registra el resultado sin datos del paciente.
- **Alternativas descartadas:**
  - Un método por transición en el modelo (`markConfirmed(...)`): más expresivo, pero reparte las reglas entre varios métodos y se aparta de la convención de la cátedra.
  - Lanzar una excepción ante una transición inválida: un evento tardío o duplicado de Kafka haría fallar al consumidor.
  - Bloqueo pesimista (`SELECT ... FOR UPDATE`): serializa todo y retiene la conexión; los conflictos son raros y el reintento es barato.
  - Que cada llamador implemente su reintento: el mismo bucle repetido en seis casos de uso.
  - Usar el `RetryTemplate` de Spring dentro de la aplicación: rompe la regla de que la aplicación no depende del framework.
- **Consecuencias:** repetir el reintento es seguro porque los estados solo avanzan, pero **no es válido dentro de una transacción abierta**: un fallo de versión la marca para deshacer. Los casos de uso que necesiten el cambio de estado junto con otra escritura en la misma transacción (el consumidor de Kafka con `processed_events`, el envío del teléfono con el outbox) tendrán que poner el reintento por fuera. La entity y el modelo de dominio repiten la lista de campos a propósito (la convención de la cátedra pide una clase separada para la entity): SonarCloud lo marcaba como duplicación del 4,4 % y se excluyen las entities del análisis de duplicación en el `pom.xml` (`sonar.cpd.exclusions`); el resto del código sigue sujeto al umbral de 3 %. Los tests cubren la tabla completa de 81 pares, el caso de uso con mocks y PostgreSQL real con carreras entre hilos (el mismo paso dos veces a la vez y cuatro respuestas distintas a la vez, comprobando que ninguna actualización se pierde y que los cambios forman una cadena válida); se verificó rompiendo el código a propósito en 18 puntos.

## 11. Iniciar una reserva (`POST /api/reservations`): orden, intentos y fallos

- **Contexto:** iniciar una reserva junta el catálogo (profesional, agenda, nombre del paciente), el proceso local y las dos operaciones de la cátedra (crear el hold y confirmarlo). La cátedra no tiene idempotencia en el hold y un timeout no prueba que la operación haya fallado.
- **Decisión:**
  - **Orden.** Primero lo que no deja rastro: el profesional (404; deshabilitado o de categoría deshabilitada, 400), que el horario sea un slot de su agenda habilitada y no haya pasado (`400 INVALID_SLOT`) y el nombre y apellido de la cuenta, con al menos 2 caracteres (`400 PATIENT_NAME_REQUIRED`). Recién entonces se crea el proceso en `HOLD_REQUESTED`, con el `uid` del JWT como dueño y como `externalPatientId`; el usuario nunca sale del cuerpo. La regla de qué profesional se puede reservar y la generación de slots son las mismas que usa la disponibilidad.
  - **Sin transacción abierta mientras se llama a la cátedra.** Cada cambio de estado es el de la máquina de estados (entrada 10): `HELD` con los ids y el vencimiento del hold, y `WAITING_PHONE` con el de la confirmación.
  - **Un intento es «hold + confirmación» sobre un proceso propio**, y se repite hasta `RESERVATION_HOLD_MAX_ATTEMPTS` intentos en total (2 por defecto, mínimo 1, el entorno gana). Se repite si se perdió la respuesta del hold y el horario figura libre, o si la cátedra dice `HOLD_EXPIRED` o `HOLD_NOT_FOUND` al confirmar.
  - **Respuesta del hold perdida.** El intento queda `FAILED` (`HOLD_RESULT_UNKNOWN`) y ese POST no se repite. Se consultan las ocupaciones del día: si el horario figura `HELD` o `CONFIRMED` se responde `409 SLOT_UNAVAILABLE` sin asumir que el hold es nuestro; si figura libre, se pide otro hold en un proceso nuevo. Agotados los intentos, `503`.
  - **Hold vencido al confirmar.** La cátedra confirma que el hold ya no existe, así que nada quedó bloqueado y no hace falta mirar las ocupaciones: el intento se cierra (`EXPIRED` o `FAILED`) y se pide otro hold. Si el horario lo tomaron en el medio, la cátedra responde `409` y se informa `SLOT_UNAVAILABLE`. Agotados los intentos, `409 RESERVATION_EXPIRED`.
  - **Confirmación perdida.** El proceso queda en `HELD`, con sus ids, y se responde `503`: el hold existe y la cátedra pudo haberlo confirmado. `HELD` no es final, de modo que el vencimiento o la reconciliación con la lista de reservas de la cátedra pueden resolverlo. Repetir la confirmación y recibir `RESERVATION_PROCESS_ALREADY_CONFIRMED` es la misma reserva y sigue a `WAITING_PHONE`.
  - **Rechazos.** Un `SLOT_ALREADY_HELD` o `SLOT_ALREADY_RESERVED` es `409 SLOT_UNAVAILABLE` y nunca se toma como «es mi hold»; profesional deshabilitado o inexistente y slot inválido según la cátedra se traducen a sus códigos; si la cátedra no se alcanzó, `503` sin investigar ningún hold. Cualquier otro rechazo o error inesperado deja el intento `FAILED` y sale como `500` genérico. Si no se puede registrar el cierre, el error original no se tapa.
- **Alternativas descartadas:**
  - No consultar ocupaciones y responder `503` ante un hold perdido: más simple, pero el reintento del usuario recibiría `409` por el hold huérfano hasta unos 10 minutos.
  - Pasar a `FAILED` una confirmación perdida: es un estado final e inmutable y perdería una reserva que la cátedra sí confirmó.
  - Responder `409` ante un hold vencido en lugar de reintentar: el hold ya no existe y repetir es seguro.
  - Reutilizar el mismo proceso para el segundo intento: el diagrama no permite volver a `HOLD_REQUESTED` y el `reservationProcessId` de la cátedra es único.
  - Consultar siempre las ocupaciones antes del hold: una llamada y un motivo de `503` más sin cambiar el resultado, porque la cátedra ya responde `409`.
  - Impedir localmente el doble clic sobre el mismo horario: la cátedra ya responde `409` y se informa como horario no disponible.
- **Consecuencias:** cada intento descartado deja una fila final (`EXPIRED` o `FAILED`) en `reservation_processes`; la consulta de «mis reservas» (TUR-12) tendrá que decidir si las muestra. En el peor caso, un pedido con la cátedra caída tarda lo que suman el hold, la consulta de ocupaciones y el segundo hold, hasta unos 30 segundos con los valores por defecto. Si el cambio a `HELD` falla después de crear el hold, el proceso queda en `HOLD_REQUESTED` sin ids y el hold huérfano vence solo; la recuperación al arrancar (TUR-16) lo pasa a `FAILED`. Los tests cubren el caso de uso sobre una base en memoria con la máquina de estados real, el contrato HTTP y el flujo completo con PostgreSQL y servidores simulados de catálogo y cátedra, y se verificó rompiendo el código a propósito en 28 puntos.
