# 016. Vencer los procesos con un margen y una consulta previa a la cátedra

**Estado:** aceptada

## Contexto

el evento `AppointmentProcessExpired` cierra una reserva vencida, pero si se pierde o se atrasa la reserva quedaría abierta para siempre. Hace falta una red de seguridad local. El problema es que un `EXPIRED` es final: si la cátedra tenía confirmada la reserva y el `AppointmentConfirmed` todavía no llegó (atraso de Kafka, servicio caído que reinicia con el evento sin leer, reloj desfasado, caída entre el `confirm` y guardar `WAITING_PHONE`), expirarla localmente descartaría una confirmación real y la app mostraría vencido un turno que existe.

## Decisión

- **Una tarea programada y acotada.** Cada `RESERVATION_EXPIRATION_INTERVAL` (30 s, con espera fija entre vueltas) toma como máximo `RESERVATION_EXPIRATION_BATCH_SIZE` (100) procesos en `HELD`, `WAITING_PHONE`, `PHONE_QUEUED` o `PHONE_SUBMITTED` con `expiresAt` vencido hace más que `RESERVATION_EXPIRATION_GRACE` (60 s), del más viejo al más nuevo. Lo que sobre sigue en la vuelta siguiente: nunca hay un bucle sin límite. La consulta usa el índice `(status, expires_at)` de la migración V1.
- **Por el mismo camino que todo cambio de estado.** Cada proceso pasa a `EXPIRED` con el motivo `HOLD_EXPIRED` (el mismo código del evento) por `ApplyReservationTransitionUseCase`: reutiliza el bloqueo optimista, el reintento y el aviso por el stream. Cada uno va en su propia operación y sin transacción alrededor de la consulta HTTP; un fallo o un choque de versión agotado en uno no frena a los demás. Con dos instancias, la segunda ve `ALREADY_IN_STATE`. No se llama a la cátedra para liberar el hold: vence solo.
- **Consulta previa para `PHONE_QUEUED` y `PHONE_SUBMITTED`.** Son los únicos donde el teléfono pudo llegar a la cátedra. Antes de expirar se consulta `GET /api/appointments` (`status=CONFIRMED`, el `externalPatientId` y el día del turno como `from` y `to`: [decisión 023](023-reconciliacion-por-fecha.md)); si ese `reservationProcessId` figura confirmado, el proceso se repara a `CONFIRMED` con su `reservationId`. Si la cátedra no responde, o devuelve más resultados que la página (100) sin que aparezca el proceso, no se expira: ante la duda se espera. Tras el primer fallo de la cátedra no se la vuelve a consultar en esa vuelta; `HELD` y `WAITING_PHONE` no necesitan consulta y se expiran igual.
- **Evento y tarea convergen.** El evento y la tarea llevan al mismo estado final y el segundo ve `ALREADY_IN_STATE`. Un `AppointmentConfirmed` que llega después de expirado se descarta (el estado final es inmutable): por eso el margen y la consulta. Una prueba lanza la tarea y una confirmación a la vez sobre el mismo proceso y comprueba que siempre queda consistente.
- **Sin ejecución especial al arrancar.** La primera vuelta corre a los 30 s de levantar y cubre los vencidos que dejó una caída. Lo dudoso (`HOLD_REQUESTED`, que no tiene `expiresAt`) es de la recuperación al arrancar.
- **Hilos.** El planificador de Spring Boot tiene un solo hilo por defecto (`spring.task.scheduling.pool.size`, 1) y lo compartirían el relay del outbox (que espera hasta `OUTBOX_PUBLISH_TIMEOUT` a una Kafka que no responde), el latido del stream y esta tarea: una vuelta lenta retrasaría a las otras. `SCHEDULING_POOL_SIZE` vale 3 por defecto, un hilo por tarea; cada tarea sigue sin solaparse consigo misma. El consumer de Kafka procesa de a un evento, Tomcat atiende un hilo por pedido y no hay tareas asincrónicas propias.
- **Configuración:** los cuatro parámetros tienen valor por defecto y el entorno gana. El interruptor `expiration.enabled` (activo por defecto) lo apagan los tests.

## Alternativas descartadas

- Solo el margen: deja el caso del servicio caído con el evento sin leer, que es justo cuando más se necesita.
- No expirar localmente los estados con teléfono y esperar siempre el evento: cero falsos vencimientos, pero un evento perdido deja el proceso colgado hasta la recuperación al arrancar.
- Expirar exactamente en `expiresAt`: el riesgo del evento en camino sin ninguna defensa.
- Un motivo propio para el vencimiento local: la app tendría que tratar dos códigos con el mismo significado; el origen queda en el log.
- Repetir hasta vaciar la tanda: un atraso grande ocuparía el hilo mucho tiempo.
- Consultar a la cátedra también para `HELD` y `WAITING_PHONE`: sin teléfono enviado la cátedra no puede haber confirmado.
- Un `ExecutorService` propio o tareas asincrónicas: más piezas para lo que un pool del planificador ya resuelve.

## Consecuencias

entre el `expiresAt` y el cierre pasan de 60 a 90 s en los que la reserva sigue abierta; la app compara `expiresAt` con la hora. Si la cátedra está caída, las reservas con teléfono enviado no se expiran hasta que responda. La consulta previa y la recuperación al arrancar comparten la misma pregunta a la cátedra.
