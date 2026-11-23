# 004. Copiar la seguridad de catálogo en lugar de compartirla

**Estado:** aceptada

## Contexto

`SecurityConfiguration`, `JwtConfiguration`, `ProblemDetailsSecurityHandler` y los errores compartidos son prácticamente iguales en los dos servicios.

## Decisión

se copian a `shared.security` y `shared.web` de turnos, adaptando el paquete y quitando lo que turnos no usa (encoder, vigencias, endpoints públicos). La repetición es a propósito.

## Alternativas descartadas

- una librería compartida. Los servicios tienen repositorios y despliegues independientes, y una librería obliga a publicar un artefacto y a versionarlo en dos repos para unas pocas clases.

## Consecuencias

un cambio de seguridad hay que aplicarlo en los dos servicios.
