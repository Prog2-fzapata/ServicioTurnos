# 020. Exigir el claim exp en el JWT

**Estado:** aceptada

## Contexto

el validador por defecto de Spring Security (`JwtValidators.createDefault()`, que es un `JwtTimestampValidator`) acepta un token bien firmado que no trae `exp`, y ese token no vence nunca. Turnos solo exigía `uid` y `auth` además. Se comprobó con un test: un token firmado con la clave correcta, con `uid` y `auth` y sin `exp`, obtenía `200`. Hoy no era explotable (catálogo siempre escribe `exp` y fabricar un token exige conocer `JWT_SECRET`), pero un token que no vence no debería poder ser válido en ningún caso.

## Decisión

el decodificador usa un `JwtTimestampValidator` con `setAllowEmptyExpiryClaim(false)` en lugar del validador por defecto. Un token sin `exp` recibe el mismo `401` `application/problem+json` que uno vencido, sin revelar el motivo. `createDefault()` no agregaba otra validación, así que no se pierde ninguna. `RequiredClaimsValidator` no cambia.

## Alternativas descartadas

- Agregar `exp` a los claims de `RequiredClaimsValidator`: es un cambio de una línea, pero solo serviría en turnos, porque catálogo no tiene ese validador. Corregir el validador de vigencia es el mismo cambio en los dos servicios.
- Dejarlo y documentarlo: el riesgo es bajo, pero el arreglo cuesta una línea y un test.

## Consecuencias

`ReservationStreamRegistry` no comprueba si el `exp` es nulo: con este validador no puede serlo.
