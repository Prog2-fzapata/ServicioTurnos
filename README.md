# ServicioTurnos

Servicio de turnos y reservas.

## Requisitos

- Docker y Docker Compose v2.
- Java 25, solo si se ejecuta el servicio fuera de Docker.

## Configuración

```bash
cp .env.example .env
```

Completar la clave de la base y el secreto del JWT en `.env` (el mismo valor que `JWT_SECRET` en el `.env` de catálogo). El archivo no se versiona.

| Variable | Descripción | Por defecto |
| --- | --- | --- |
| `TURNOS_DB_NAME` | Nombre de la base de datos | `turnos_db` |
| `TURNOS_DB_USER` | Usuario de la base | `turnos_user` |
| `TURNOS_DB_PASSWORD` | Clave del usuario | |
| `JWT_SECRET` | Clave con la que catálogo firma los tokens (HS512): Base64 de al menos 512 bits. Si falta o es más corta, el servicio no arranca | |
| `POSTGRES_PORT` | Puerto del host donde se publica PostgreSQL | `5433` |
| `TURNOS_PORT` | Puerto del host donde se publica el servicio | `8082` |

PostgreSQL y el servicio se publican solo en `127.0.0.1`.

## Levantar con Docker

```bash
docker compose up -d --build
```

Levanta una instancia de PostgreSQL propia del servicio y el servicio, que arranca cuando la base está lista. Estado: `http://localhost:8082/actuator/health`.

| Acción | Comando |
| --- | --- |
| Ver estado | `docker compose ps` |
| Ver logs | `docker compose logs -f turnos` |
| Detener (conserva los datos) | `docker compose down` |
| Borrar todo, incluidos los datos | `docker compose down -v` |

## Ejecutar fuera de Docker

Levantar solo la base y correr el servicio con Maven, con las variables de `.env` cargadas:

```bash
docker compose up -d --wait postgres
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

El servicio se conecta a `localhost` en el puerto `POSTGRES_PORT`.

## Seguridad

Turnos no emite tokens: valida el JWT que emite catálogo (`POST /api/authenticate`) con el mismo secreto. Todo exige `Authorization: Bearer <jwt>`, salvo `/actuator/health` y la documentación. Un token ausente, mal firmado, vencido o sin `uid`/`auth` recibe `401`; la falta de rol recibe `403`; ambos en `application/problem+json`. En Swagger UI el botón «Authorize» recibe el token. El `uid` del usuario sale siempre del token.

## Tests

```bash
./mvnw verify
```

Los tests de integración levantan su propio PostgreSQL 18 en un contenedor descartable (Testcontainers), así que solo requieren Docker en ejecución; no usan el `.env` ni la base del compose.

## Decisiones de diseño

El registro de decisiones técnicas, con alternativas descartadas y justificación, está en [docs/decisiones.md](docs/decisiones.md).
