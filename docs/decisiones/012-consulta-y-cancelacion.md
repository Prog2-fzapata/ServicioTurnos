# 012. Aislar las reservas por usuario y cancelar primero en la cátedra

**Estado:** aceptada

## Contexto

la app necesita listar, consultar y cancelar las reservas del usuario. Cada intento descartado de iniciar una reserva ([decisión 011](011-iniciar-una-reserva.md)) deja una fila final en `reservation_processes`, y cancelar toca dos sistemas (la cátedra y la base local) que no comparten transacción.

## Decisión

- **Aislamiento.** El usuario sale siempre del JWT y toda búsqueda es por `id` **y** `userId` (`findByIdAndUserId`). Una reserva ajena responde `404 RESERVATION_NOT_FOUND`, con el mismo cuerpo que una inexistente, para no revelar que existe.
- **Lista.** Orden fijo por fecha y hora del turno descendente, con el `id` como desempate para que las páginas no repitan ni salteen filas; el `sort` del pedido se ignora. Sin `status` se excluyen las `EXPIRED` y las `FAILED` (`ReservationStatus.DISCARDED`): son los intentos que no llegaron a ser una reserva. Con `status` se devuelven exactamente las de ese estado, también `FAILED` o `EXPIRED`; una `CANCELLED` se muestra siempre.
- **Paginación.** `Pageable` de Spring, con `X-Total-Count` y `Link` como en el catálogo. `size` 20 por defecto y tope `RESERVATION_PAGE_MAX_SIZE` (100 por defecto, el entorno gana).
- **Cancelación.** Primero la cátedra y después el proceso local, que pasa a `CANCELLED` por el caso de uso de transiciones ([decisión 010](010-maquina-de-estados.md)). Un proceso ya `CANCELLED` se devuelve tal cual, sin llamar a la cátedra; cualquier otro estado distinto de `CONFIRMED` es `409 RESERVATION_NOT_CANCELLABLE`, también sin llamarla. Si la cátedra responde `RESERVATION_NOT_CANCELLABLE` es el mismo `409`; si responde `RESERVATION_NOT_FOUND` o `RESERVATION_OWNER_MISMATCH` es un desajuste entre servicios (`500` genérico) y el proceso local no cambia. El motivo es opcional, hasta 500 caracteres.
- **Sin transacción abierta mientras se llama a la cátedra.** Si la cátedra cancela y el guardado local falla (o se pierde la respuesta), el proceso sigue `CONFIRMED` y repetir el pedido lo repara, porque la cancelación en la cátedra es idempotente.

## Alternativas descartadas

- Mostrar todos los intentos y que la app filtre: la lista se llenaría de filas que el usuario nunca vio como reserva.
- Ocultar solo los intentos que nunca consiguieron un hold (`reservation_process_id` nulo) y mostrar las `FAILED` y `EXPIRED` con hold: son dos reglas distintas según un campo interno que el usuario no ve.
- Cancelar primero en la base: si la cátedra fallara habría que deshacerlo y quedaría una cancelación que no ocurrió.
- Llamar a la cátedra aunque el proceso ya esté `CANCELLED`: una llamada de más que no cambia el resultado.
- Responder `403` ante una reserva ajena: revela que existe.
- Paginación propia con `page` y `size` en vez de `Pageable`: se pierde el tope configurable de Spring y la coherencia con el catálogo.

## Consecuencias

`CONFIRMED` solo se alcanza por Kafka o por la reconciliación, así que los tests de cancelación insertan el proceso ya confirmado. La consulta usa el índice por `user_id` ya existente.
