# 008. Consultar la cátedra con el JWT técnico y reintentos distintos por operación

**Estado:** aceptada

## Contexto

turnos consulta ocupaciones y crea, confirma, lista y cancela reservas en el servicio central de la cátedra. Dos hechos condicionan el diseño: la cátedra **no tiene idempotencia** en la creación de holds ni permite recuperar los ids de un hold, y un timeout no prueba que la operación haya fallado.

## Decisión

- **Un puerto y un adapter.** `CatedraApiClient` (puerto de salida) con cinco operaciones y `CatedraApiClientAdapter`, con modelos propios del dominio de turnos. Usa el **JWT técnico del proyecto** (`CATEDRA_JWT`, mismo que catálogo), no el del usuario: la cátedra no conoce a los usuarios finales; el dueño de la reserva viaja como `externalPatientId`.
- **Reintentos distintos por operación.** Las lecturas, confirmar y cancelar se pueden repetir sin riesgo (confirmar responde `RESERVATION_PROCESS_ALREADY_CONFIRMED` si ya estaba; cancelar es idempotente por contrato) y se reintentan ante conexión caída, timeout o 5xx: 3 intentos en total, con una espera de 200 ms que se duplica. **Crear un hold no se reintenta** ante un resultado desconocido (timeout de lectura, corte o 5xx), porque el hold pudo haberse creado y repetir el POST chocaría con él sin poder reconocerlo. Solo se repite si la conexión nunca se estableció: nada se envió, así que se sabe que no hay hold.
- **Dos niveles de «no disponible».** `CatedraNotReachedException` (nunca se conectó: el resultado es conocido) es un caso particular de `CatedraUnavailableException` (resultado desconocido). Los dos salen como `503 CATEDRA_UNAVAILABLE`; el caso de uso es quien necesita distinguirlos. Ante un resultado desconocido al crear un hold, el proceso pasa a `FAILED` y, antes de crear otro hold, se consultan las ocupaciones (lo implementan las decisiones 011, 016 y 017).
- **Errores por status y code, nunca por el texto.** Un rechazo funcional de la matriz del §13 sale como `CatedraRejectedException` con un motivo (`CatedraRejection`); cada caso de uso decide qué hacer. Un `409 SLOT_ALREADY_HELD` nunca se interpreta como «es mi hold». `401` y `403` (JWT técnico vencido, de otra cuenta, o códigos de propiedad como `HOLD_OWNER_MISMATCH`) son `CatedraAuthenticationException`; cualquier otro `4xx` o un cuerpo ilegible, `CatedraResponseException`. Ambos salen como `500` genérico: son desajustes de configuración o de contrato, no caídas. Una combinación de status y code que no está en la matriz no se trata como rechazo.
- **Lo que nunca llega a un mensaje ni a un log:** el cuerpo de la respuesta, el JWT técnico, y el nombre o el teléfono del paciente. Del cuerpo de un error solo se lee el `code`, y solo si tiene la forma de un code (mayúsculas y guiones bajos), para que no se pueda inyectar texto en un log. Las propiedades ocultan el JWT en su `toString`.
- **Formatos.** La hora del hold se envía como `HH:mm` (como el ejemplo del contrato); al leer se aceptan `HH:mm` y `HH:mm:ss`. Los campos desconocidos se ignoran. Un estado de ocupación que el contrato no define es un error de respuesta. El listado exige `X-Total-Count`: un total inventado engañaría a quien pagina. El id de un hold se codifica en la ruta.
- **Configuración.** `CATEDRA_BASE_URL` y `CATEDRA_JWT` obligatorias y sin valor por defecto. `CATEDRA_CONNECT_TIMEOUT` (2 s), `CATEDRA_READ_TIMEOUT` (5 s), `CATEDRA_MAX_ATTEMPTS` (3) y `CATEDRA_INITIAL_BACKOFF` (200 ms) tienen valor por defecto y el entorno gana. El compose las lista sin valor. Los timeouts son más cortos que en catálogo (3 s y 10 s) porque en turnos hay un usuario esperando la respuesta.

## Alternativas descartadas

- Reintentar todo igual: duplicaría holds o produciría un `409` contra el propio hold sin poder distinguirlo.
- Tratar un `409 SLOT_ALREADY_HELD` posterior a un timeout como éxito: no hay forma de verificar que el hold sea nuestro ni de recuperar sus ids.
- Que el adapter consulte las ocupaciones y repita el hold: es una política de negocio que combina dos operaciones y pertenece a los casos de uso, no al adapter.
- Un puerto por operación: más interfaces sin beneficio; todas hablan con el mismo sistema externo.
- Reutilizar las excepciones y la configuración del cliente de catálogo: la política de reintentos y el significado de los errores son distintos.

## Consecuencias

con los valores por defecto una lectura con la cátedra caída espera hasta unos 16 segundos antes del `503` (3 intentos de 5 s más las esperas), y un hold, hasta 5 segundos. Tras una respuesta perdida al crear un hold, el horario puede quedar bloqueado hasta unos 10 minutos (el TTL de la cátedra), también para el mismo usuario. Los tests usan un servidor HTTP real y local, y se verificó rompiendo el código a propósito en veinte puntos.
