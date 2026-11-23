# 018. Acotar los reintentos y los timeouts y definir la degradación

**Estado:** aceptada

## Contexto

los reintentos y los timeouts de catálogo y de la cátedra ya existían, pero la política estaba repartida entre las secciones de cada cliente, no cubría Kafka, las tareas ni la base, y ningún parámetro de reintentos tenía techo (`CATALOGO_MAX_ATTEMPTS=1000000` era un bucle casi infinito). Tampoco estaba probado que, con un servicio caído, lo que depende solo de datos propios siguiera funcionando.

## Decisión

- **La política vive en el README** (sección «Reintentos, timeouts y degradación»): una tabla por dependencia (timeout, intentos, qué se reintenta, qué pasa al agotarse, qué ve el cliente), la lista de todos los bucles con su cota, cuánto puede tardar y qué funciona con cada cosa caída.
- **Todo parámetro de reintentos tiene un techo validado al arrancar** (`RetryLimits`): 10 para los intentos de catálogo y de la cátedra, el choque de versión y los reintentos del consumidor de Kafka, y 5 para los intentos de hold, porque cada uno es un hold más su confirmación y cada una reintenta por su cuenta. Un valor mayor impide arrancar y el error nombra el parámetro.
- **El teléfono del outbox es la excepción consciente:** se reintenta en cada vuelta mientras Kafka no confirme, porque esa es la garantía del patrón ([decisión 014](014-envio-del-telefono-con-outbox.md)) y topearlo perdería el teléfono del usuario (un estado de descarte se descartó en esa misma decisión). Está acotado por vuelta, es observable (`attempts` y un aviso en el log por mensaje) y el vencimiento cierra la reserva si Kafka no vuelve.
- **Base de datos.** Timeout de conexión de 5 s (`DB_CONNECTION_TIMEOUT`, en milisegundos porque es lo que lee Hikari) en vez de los 30 s por defecto, y un `503 DATABASE_UNAVAILABLE` en vez del `500` genérico cuando no hay base (`DataAccessResourceFailureException` y `CannotCreateTransactionException`); cualquier otro error de datos sigue siendo `500`. Se probó con una base realmente inaccesible: el servicio arranca igual y cada pedido que la necesita falla en el tiempo del pool.
- **Productor de Kafka.** Medido con un broker inaccesible: `send()` se bloqueaba 60 s (el `max.block.ms` por defecto) antes de que empezara a contar el timeout de 10 s de la confirmación, y fallaba con una `KafkaException` que el relay no trataba como fallo de publicación, así que no sumaba el intento y abortaba la vuelta con un `ERROR`. Ahora el bloqueo se acota con `OUTBOX_PUBLISH_BLOCK_TIMEOUT` (5000 ms) y cualquier `KafkaException` al enviar cuenta como fallo de publicación.
- **Degradación.** Con el catálogo caído no se inician reservas ni se consulta la disponibilidad (`503`, sin crear nada ni tocar la cátedra), pero la lista, el detalle, el teléfono, la cancelación y el stream no lo usan y siguen funcionando. Con la cátedra caída siguen la lista, el detalle, el teléfono y el stream; iniciar, cancelar y la disponibilidad responden `503`. Tests de integración con los dos servicios caídos y con solo el catálogo caído lo comprueban, y verifican que no queda nada a medias.
- **Cuánto puede tardar iniciar una reserva:** unos 64 s como cota teórica con los valores por defecto. No se agrega un plazo global.

## Alternativas descartadas

- Un circuito cortacorriente: agrega una dependencia y estado compartido para algo que los techos y los timeouts ya acotan en esta escala.
- Un backoff creciente en el outbox: requiere una columna nueva y una migración.
- Un tope de intentos del outbox con un estado de error: pierde el teléfono.
- Un plazo global para iniciar una reserva: habría que cancelar llamadas en vuelo y compensar lo que quede a medias; el vencimiento y la recuperación ya lo resuelven.
- Bajar los valores por defecto para acortar el peor caso: cada uno responde a un riesgo distinto y están pensados para una red con ZeroTier.
- Dejar el `500` cuando no hay base: el cliente no distinguiría un fallo propio de uno que se arregla esperando.

## Consecuencias

una configuración con un valor fuera del techo impide arrancar (es a propósito). Una vuelta del relay con Kafka caído tarda hasta unos 4 minutos con una tanda completa; corre en su propio hilo y no retrasa a las otras tareas. Un choque de versión agotado llegaba al cliente como `500` genérico; desde la [decisión 021](021-ajustes-de-la-prueba-real.md) es un `409` `RESERVATION_CONFLICT`.
