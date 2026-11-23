# 005. Responder los 401 y 403 en problem+json y proteger por método

**Estado:** aceptada

## Contexto

Los `401` y `403` de los filtros de seguridad salen antes de llegar a un controller, así que ningún handler de errores los formatea, y la documentación de la API necesita saber cómo se manda el token.

## Decisión

Igual que en catálogo (su decisión 017): `ProblemDetailsSecurityHandler` escribe el `401` y el `403` de los filtros en `application/problem+json` con `code` `UNAUTHORIZED` y `FORBIDDEN`, sin revelar si el token venció, está mal firmado o falta (`WWW-Authenticate: Bearer`). `@EnableMethodSecurity` permite `@PreAuthorize`, y `GlobalExceptionHandler` traduce `AccessDeniedException` a `403` (sin eso daba `500`). `OpenApiConfiguration` declara el esquema Bearer JWT para que Swagger UI muestre «Authorize»; el token se obtiene en catálogo (`POST /api/authenticate`).

- **Rutas públicas:** solo `/actuator/health` y la documentación. Todo lo demás exige token, también las rutas que no existen.
- **CSRF desactivado:** la API no usa sesión ni cookies, cada pedido trae su JWT en `Authorization`, así que no hay nada que falsificar entre sitios. Sonar (`java:S4502`) lo marca igual, por eso la línea lleva `NOSONAR` con su motivo.

## Consecuencias

`SecurityRejectionTest` usa un controller de prueba para el `401` (sin token, mal formado, vencido, otra clave, otro algoritmo, cuerpo alterado, sin `uid`, sin `auth`), el `403` por rol y la lectura del `uid` desde el token. Se comprobó que falla al quitar el validador de claims, al aceptar HS256, al quitar `@EnableMethodSecurity` y al abrir `anyRequest()`.
