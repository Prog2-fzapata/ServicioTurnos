# 015. Avisar los cambios de la reserva por SSE después del COMMIT

**Estado:** aceptada

## Contexto

la app necesita enterarse de que una reserva cambió (por ejemplo, que ya puede pedir el teléfono) sin consultar todo el tiempo. Los cambios los provocan seis llamadores distintos (el consumer de Kafka, el relay del teléfono, el envío del teléfono, iniciar y cancelar) y varios corren dentro de una transacción abierta.

## Decisión

- **Un solo punto de aviso.** `ApplyReservationTransitionUseCaseImpl`, por donde pasa todo cambio de estado, llama al puerto de salida `ReservationChangePublisher` cuando el resultado es `APPLIED` o `DATA_COMPLETED`. Este último es el pedido de teléfono: llega con la reserva ya en `WAITING_PHONE`, el estado no cambia y sí `canSubmitPhone`. Crear la fila inicial no avisa: la respuesta del `POST` ya la trae.
- **Después del COMMIT.** El adaptador (`SseReservationChangePublisher`) arma el contenido al publicar y, si hay una transacción abierta, registra el envío en `afterCommit`; si no la hay, envía al instante. Una transacción que se deshace no avisa. Atrapa sus propios errores: un fallo del aviso no debe hacer reintentar un evento de Kafka que ya se aplicó. La aplicación no importa nada de Spring para esto: la sincronización con la transacción vive en el adaptador.
- **Conexiones en memoria.** `ReservationStreamRegistry` guarda `usuario → conexiones`, con la lista reemplazada entera en cada cambio. Hay una sola instancia (igual que el relay del outbox, [decisión 014](014-envio-del-telefono-con-outbox.md)) y, si se pierde, la app reconecta y reconsulta. Un envío que falla descarta esa conexión.
- **Contenido.** `data` lleva `id`, `status`, `expiresAt` (`null` si terminó, como `Reservation`), `canSubmitPhone` y `updatedAt`; el `id` del evento es `{id}-{versión}`. Sin historia ni `Last-Event-ID`.
- **Latido y tope.** Comentario `:ping` al conectar y cada `STREAM_HEARTBEAT_INTERVAL` (25 s), y `STREAM_MAX_CONNECTIONS_PER_USER` (5): al superarlo se cierra la conexión más vieja. Ambos con valor por defecto y el entorno gana.
- **JWT vencido.** En cada latido se cierra la conexión cuyo `exp` ya pasó. No puede ser un `401` porque la cabecera `200` ya salió. La prueba por HTTP real mostró que el decodificador tolera 60 s de desfase de reloj, así que reconectar con el mismo token se acepta y se vuelve a cerrar; la app debe renovarlo.
- **Apagado.** El registro es un `SmartLifecycle` con la fase más alta y cierra todo al empezar el apagado: el apagado ordenado de Spring Boot espera a los pedidos en curso y un stream abierto no termina solo (30 s de demora en cada reinicio).
- **Desvío de la plantilla.** `ReservationStreamController` inyecta el registro (infraestructura) y no `ReservaService`: administrar conexiones abiertas es transporte, no lógica de negocio, y un caso de uso no tendría nada que decidir.

## Alternativas descartadas

- Avisar desde cada llamador: hay seis y el próximo se olvidaría.
- Avisar apenas se guarda, sin esperar el COMMIT: la app podría consultar y no ver el cambio, o enterarse de algo que se deshace.
- Un `data` sin `canSubmitPhone`, con la app consultando el detalle en cada aviso: una consulta más por evento, y al llegar el pedido de teléfono el evento sería idéntico al anterior.
- Guardar historia y reenviar con `Last-Event-ID`: una tabla o memoria extra para algo que la app resuelve reconsultando.
- Repartir los avisos entre instancias con Redis o Kafka: sobra con una instancia.
- Rechazar con `429` la conexión que pasa el tope: una app que perdió su conexión quedaría bloqueada hasta que el servidor la detecte.
- Cerrar justo en el `exp` con un temporizador por conexión: más piezas para ganar unos segundos.

## Consecuencias

las conexiones y los avisos pendientes de una caída del servicio se pierden; es aceptable porque el stream no es la fuente de verdad. El latido es una tarea de fondo más, siempre activa. Escalar a varias instancias exigiría repartir los avisos entre ellas.
