# 002. Validar el JWT con el secreto compartido de catálogo (HS512)

**Estado:** aceptada

## Contexto

el JWT lo emite el servicio de catálogo y turnos debe confiar en él sin consultarle a catálogo en cada pedido.

## Decisión

turnos valida la firma con el mismo secreto HMAC que catálogo (`JWT_SECRET`, Base64, al menos 512 bits). El `JwtDecoder` solo acepta **HS512**, así que un token firmado con otro algoritmo se rechaza aunque la clave sea la correcta. Si la variable falta, no es Base64 o es corta, el servicio no arranca. Además de la firma, Spring controla la vigencia (`exp`, `nbf`). Turnos no tiene encoder: no emite tokens.

## Alternativas descartadas

- Clave pública (RSA): turnos no podría fabricar tokens, pero hay que generar y distribuir archivos PEM; con dos servicios propios en el mismo entorno ese riesgo no justifica la complejidad.
- Consultar a catálogo en cada pedido: acopla la disponibilidad de turnos a la de catálogo.

## Consecuencias

quien conozca la clave puede fabricar tokens, así que los dos `.env` deben cuidarse igual. Un token no se puede revocar antes de que venza.
