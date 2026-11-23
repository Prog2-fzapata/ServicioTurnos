# 001. Probar la integración con Testcontainers

**Estado:** aceptada

## Contexto

`TurnosApplicationTests` y los tests que levantan el contexto completo necesitan una base PostgreSQL. La base podía venir del `.env` o del servicio de base del workflow de CI.

## Decisión

cada ejecución de pruebas levanta un PostgreSQL 18 en un contenedor descartable (Testcontainers), conectado con `@ServiceConnection`. Es la misma imagen que usa el compose.

## Alternativas descartadas

- Usar la base del compose: los tests compartirían datos con el desarrollo manual.
- Una base en memoria (H2): su SQL difiere de PostgreSQL y un test podría pasar con un comportamiento que en producción falla.

## Consecuencias

los tests solo requieren Docker en ejecución y no usan el `.env`. El workflow de CI conserva su servicio de base, que ya no se usa para los tests y se podrá quitar.
