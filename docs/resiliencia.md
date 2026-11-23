# Resiliencia: reintentos, timeouts y degradación

Qué hace el servicio cuando algo de lo que usa no responde. Cada parámetro tiene un valor por defecto y el entorno lo pisa; los de **reintentos tienen además un techo** que se valida al arrancar: el servicio no levanta con `CATALOGO_MAX_ATTEMPTS=1000000` y el error nombra el parámetro ([decisión 018](decisiones/018-reintentos-timeouts-y-degradacion.md)). Qué pasa con un mensaje duplicado, perdido o fuera de orden está en [idempotencia-y-recuperacion.md](idempotencia-y-recuperacion.md).

## Política por dependencia

| Dependencia | Timeout | Intentos | Qué se reintenta | Si se agotan | Qué ve el cliente |
| --- | --- | --- | --- | --- | --- |
| **Catálogo** (profesional y cuenta) | conexión 2 s, lectura 3 s | `CATALOGO_MAX_ATTEMPTS` (3, máx. 10), con espera de 200 ms que se duplica | `5xx`, timeout o conexión caída; un `4xx` no | no se hace nada | `503 CATALOG_UNAVAILABLE` |
| **Cátedra**: ocupaciones, listar, confirmar y cancelar | conexión 2 s, lectura 5 s | `CATEDRA_MAX_ATTEMPTS` (3, máx. 10), misma espera | corte o timeout: repetirlas es seguro | iniciar deja el proceso en `HELD` para el vencimiento o la recuperación | `503 CATEDRA_UNAVAILABLE` |
| **Cátedra**: crear un hold | igual | los mismos, **solo si nunca se llegó a conectar** | un hold no se repite si el pedido pudo haber salido: la cátedra no tiene idempotencia | el proceso pasa a `FAILED` | `503 CATEDRA_UNAVAILABLE` |
| **Hold + confirmación** completos | — | `RESERVATION_HOLD_MAX_ATTEMPTS` (2, máx. 5) | hold perdido con el horario libre, o hold vencido antes de confirmar | `FAILED` o `EXPIRED` | `409`, `503` según el motivo |
| **Choque de versión** del mismo proceso | — | `RESERVATION_CONFLICT_MAX_ATTEMPTS` (3, máx. 10) | relee el proceso y decide de nuevo | el pedido falla | `409` `RESERVATION_CONFLICT` (caso extremo) |
| **Kafka, consumidor** | — | `CATEDRA_KAFKA_MAX_RETRIES` (3, máx. 10), espera de 1 s que se duplica | cualquier fallo al procesar un evento | se descarta con un log de error; lo recuperan el vencimiento y la recuperación al arrancar | nada |
| **Kafka, productor** (teléfono) | bloqueo 5 s (`OUTBOX_PUBLISH_BLOCK_TIMEOUT`) y ack 10 s (`OUTBOX_PUBLISH_TIMEOUT`) | **sin tope**: el relay lo vuelve a intentar en cada vuelta | falta de confirmación | el mensaje queda `PENDING` con un intento más | `202` al enviar; la reserva sigue en `PHONE_QUEUED` hasta que el broker confirma |
| **Base de datos** | 5 s por conexión (`DB_CONNECTION_TIMEOUT`) | ninguno | — | falla el pedido | `503 DATABASE_UNAVAILABLE` |

El teléfono es la única excepción consciente a «todo reintento tiene tope»: es la garantía del outbox (el mensaje se entrega cuando Kafka vuelva) y topearlo perdería el teléfono del usuario. Está acotado por vuelta, es observable (el contador `attempts` de la fila y un aviso en el log por cada mensaje) y, si Kafka no vuelve, el vencimiento cierra la reserva.

## Todos los bucles tienen cota

| Bucle | Cota |
| --- | --- |
| Reintentos HTTP a catálogo y a la cátedra | `CATALOGO_MAX_ATTEMPTS` y `CATEDRA_MAX_ATTEMPTS`, hasta 10 |
| Intentos de hold con su confirmación | `RESERVATION_HOLD_MAX_ATTEMPTS`, hasta 5 |
| Reintentos por choque de versión | `RESERVATION_CONFLICT_MAX_ATTEMPTS`, hasta 10 |
| Reintentos del consumidor de Kafka | `CATEDRA_KAFKA_MAX_RETRIES`, hasta 10 |
| Relay del outbox | una tanda de `OUTBOX_RELAY_BATCH_SIZE` por vuelta, con espera fija entre vueltas; las vueltas no se solapan |
| Vencimiento | una tanda de `RESERVATION_EXPIRATION_BATCH_SIZE` por vuelta |
| Recuperación al arrancar | una sola vez, `RECOVERY_BATCH_SIZE` de cada clase |
| Latido del stream | una vuelta cada `STREAM_HEARTBEAT_INTERVAL`, sin acumular |

## Cuánto puede tardar

Con los valores por defecto y un servicio que acepta la conexión pero no contesta:

- **Una consulta a catálogo:** hasta 9,6 s (3 lecturas de 3 s y las esperas de 0,2 s y 0,4 s). Si ni conecta, 6,6 s.
- **Una llamada a la cátedra que se repite** (confirmar, cancelar, listar): hasta 15,6 s. Crear un hold, 6,6 s.
- **Iniciar una reserva**, cota superior teórica: dos consultas a catálogo (19,2 s) y hasta dos rondas de hold más confirmación (2 × 22,2 s), unos **64 s**. No hay un plazo global: cancelar llamadas en vuelo dejaría estados intermedios, y lo que quede a medias lo resuelven el vencimiento y la recuperación al arrancar. La app debe tener su propio timeout; un pedido que abandona igual termina en el servidor y la reserva aparece en «mis reservas».
- **Una vuelta del relay con Kafka caído:** cada mensaje falla a los 5 s, así que una tanda completa de 50 tarda hasta unos 4 minutos. Corre en su propio hilo del planificador y no retrasa al latido ni al vencimiento.

El planificador tiene un hilo por tarea programada (`SCHEDULING_POOL_SIZE`, 3 por defecto: relay del outbox, latido del stream y vencimiento): con uno solo, una vuelta lenta retrasaría a las otras ([decisión 016](decisiones/016-vencimiento-de-procesos.md)).

## Qué funciona con cada cosa caída

| Operación | Catálogo caído | Cátedra caída | Kafka caído | Base caída |
| --- | --- | --- | --- | --- |
| Listar y ver mis reservas | funciona | funciona | funciona | `503` |
| Stream de cambios | funciona | funciona | funciona | abre, pero no hay cambios que avisar |
| Enviar el teléfono | funciona | funciona (queda en el outbox) | funciona (queda `PHONE_QUEUED`) | `503` |
| Cancelar | funciona | `503`, no cambia nada | funciona | `503` |
| Iniciar una reserva | `503`, no se crea nada | `503`, el intento queda `FAILED` | funciona | `503` |
| Consultar disponibilidad | `503` | `503` | funciona | funciona (no usa la base) |
| Eventos de la cátedra | funciona | — | se detienen hasta que vuelva | se reintentan y, agotados, se descartan |
| Vencimiento | funciona | no toca lo que necesita preguntarle | funciona | la vuelta falla y se repite en la siguiente |
| Recuperación al arrancar | funciona | no toca lo que necesita preguntarle | funciona | falla y queda para el próximo arranque |

Lo que depende solo de datos propios sigue funcionando: validar el JWT no consulta a nadie y el estado del servicio (`/actuator/health`) no depende de catálogo ni de la cátedra. Lo prueban tests de integración con los dos servicios caídos, con solo catálogo caído y con una base inaccesible.
