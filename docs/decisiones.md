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
