# 003. Exigir los claims uid y auth

**Estado:** aceptada

## Contexto

un token con firma válida pero sin `uid` (o sin autoridades) no puede venir de catálogo; dejarlo pasar terminaría en un error `500` más adelante, cuando un caso de uso busque el usuario.

## Decisión

`RequiredClaimsValidator` rechaza con `401` los tokens sin `uid` o sin `auth`. Los casos de uso reciben el `uid` como parámetro, que el controller lee del JWT (`@AuthenticationPrincipal Jwt`); nunca sale del cuerpo ni de la URL. No se exige un rol concreto: hoy todos los usuarios tienen `ROLE_USER` y lo que se aísla es el acceso por usuario, con el `uid`.

## Alternativas descartadas

- un puerto `out` que devuelva «el usuario actual»: oculta de dónde sale el dato y obliga a que el dominio dependa del contexto de seguridad.
