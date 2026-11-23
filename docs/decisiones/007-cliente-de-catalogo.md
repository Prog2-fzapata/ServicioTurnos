# 007. Consultar catálogo con el JWT del usuario, reintentos y errores propios

**Estado:** aceptada

## Contexto

antes de iniciar una operación, turnos necesita la agenda vigente de un profesional, que solo conoce catálogo (`GET /api/professionals/{id}`). El enunciado pide que, si catálogo no está disponible, no se inicien operaciones que necesiten información vigente, y que lo demás siga funcionando.

## Decisión

- **El JWT del usuario viaja con cada pedido.** Un interceptor del cliente HTTP (`JwtPropagationInterceptor`, en `shared.security`) lee el token del usuario autenticado y lo agrega como `Authorization: Bearer`. El puerto de salida `CatalogClient.findProfessional(id)` no menciona tokens: es un detalle de HTTP que no debe llegar al dominio. Si no hay un usuario con JWT, el pedido ni se hace (`IllegalStateException`): todo pedido a turnos exige token, así que sería un error de programación.
- **Reintentos con `RetryTemplate`** de Spring Framework 7 (sin dependencias nuevas): hasta 3 intentos en total, con una espera que se duplica (200 ms, 400 ms). Solo se reintenta ante `CatalogUnavailableException` (conexión caída, timeout o 5xx); un 4xx nunca. Es seguro repetir porque la consulta es un `GET`.
- **Qué sale en cada caso.** `404` devuelve `Optional.empty()` (el caso de uso decidirá `PROFESSIONAL_NOT_FOUND`). 5xx, timeout o conexión caída, agotados los reintentos, lanzan `CatalogUnavailableException`, que `ReservaExceptionHandler` responde como `503 CATALOG_UNAVAILABLE`. Cualquier otro 4xx (`401` por un secreto distinto, `403`...) o un cuerpo ilegible lanzan `CatalogResponseException`, que sale como `500` genérico: es un desajuste entre servicios, no una caída. El log registra solo el status; nunca el cuerpo de la respuesta ni el JWT.
- **Configuración.** `CATALOGO_BASE_URL` es obligatoria y sin valor por defecto (es una dirección de despliegue). `CATALOGO_CONNECT_TIMEOUT` (2 s), `CATALOGO_READ_TIMEOUT` (3 s), `CATALOGO_MAX_ATTEMPTS` (3) y `CATALOGO_INITIAL_BACKOFF` (200 ms) tienen valor por defecto en `application.properties`, y si la variable está definida en el entorno, esa gana. El compose las lista sin valor.
- **Llegar a catálogo desde el contenedor.** Los dos servicios corren en composes separados y catálogo publica su puerto en el host, así que el `.env.example` usa `http://host.docker.internal:8081` y el compose agrega `extra_hosts: host.docker.internal:host-gateway` (en Linux ese nombre no existe sin eso). Fuera de Docker se pisa con `http://localhost:8081`.
- **Modelo propio.** `CatalogProfessional` y `CatalogSchedule` son del dominio de turnos, no copias de las clases de catálogo: los servicios son independientes y el DTO ignora los campos que no conoce (`@JsonIgnoreProperties(ignoreUnknown = true)`), así que un campo nuevo en catálogo no rompe a turnos.

## Alternativas descartadas

- Pasar el token como parámetro por todas las capas: contamina el dominio y los casos de uso con un detalle de HTTP.
- Un JWT técnico propio de turnos para hablar con catálogo: catálogo no puede saber quién es el usuario, y el contrato pide el del usuario.
- Resilience4j o `spring-retry`: una dependencia más para algo que Spring ya trae.
- Tratar todo error de catálogo como "no disponible": esconde los desajustes de configuración (un secreto distinto parecería una caída).
- Una red de Docker compartida entre los dos composes: acopla dos repositorios independientes.
- Un circuit breaker: no hace falta: los techos y los timeouts ya acotan la espera ([decisión 018](018-reintentos-timeouts-y-degradacion.md)).

## Consecuencias

con los valores por defecto, un pedido que encuentre a catálogo caído espera hasta unos 10 segundos antes de recibir el `503` (3 intentos de 3 s más las esperas). Hay que bajar `CATALOGO_READ_TIMEOUT` o `CATALOGO_MAX_ATTEMPTS` si esa espera es demasiado larga para la app. Los tests usan un servidor HTTP real y local (el de JDK) para probar timeouts, cortes de conexión y la cantidad exacta de intentos, y se verificó rompiendo el código a propósito en trece puntos.
