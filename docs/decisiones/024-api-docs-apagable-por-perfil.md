# 024. Apagar la documentación de la API en el perfil prod

**Estado:** aceptada

## Contexto

La documentación de endpoints y DTO se genera a partir del código con SpringDoc (OpenAPI y Swagger UI), por lo que siempre coincide con la solución. Dejarla expuesta en un despliegue real revela la superficie de la API. Turnos la dejaba abierta en cualquier entorno, a diferencia de catálogo, que la apaga en el perfil `prod`.

## Decisión

La documentación está activa por defecto, porque forma parte de lo que se entrega y quien evalúe el proyecto necesita verla al levantarlo con `docker compose up`. El entorno se declara con `SPRING_PROFILES_ACTIVE` (`dev` por defecto, en `.env.example` y en el compose); el perfil `prod` (`application-prod.properties`) desactiva Swagger UI y `/v3/api-docs`. Las rutas de la documentación siguen sin pedir token, así que con `prod` responden `404` como cualquier ruta que no existe.

## Alternativas descartadas

- Dejarla siempre activa, que es lo que había: revela la superficie de la API en un despliegue real.
- Desactivarla siempre y no dejar ninguna forma de consultarla en ejecución, lo que le quita utilidad como documentación entregable.
- Protegerla con un token: Swagger UI no puede abrirse sin enviar la cabecera, así que no se podría ni cargar la página.

## Consecuencias

`ApiDocsProdProfileTest` comprueba que con el perfil `prod` el documento OpenAPI y Swagger UI responden `404`, y `SecurityIntegrationTest` que sin perfil el documento responde `200` y declara el esquema Bearer JWT. Un despliegue real tiene que declarar `SPRING_PROFILES_ACTIVE=prod`.
