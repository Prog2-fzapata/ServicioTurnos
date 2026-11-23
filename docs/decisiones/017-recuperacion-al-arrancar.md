# 017. Recuperar al arrancar solo lo que tiene evidencia

**Estado:** aceptada

## Contexto

una caída del servicio puede dejar procesos que ningún evento va a resolver: un `HOLD_REQUESTED` cuyo pedido de hold se perdió (nunca se recibieron los ids), o un proceso con el teléfono enviado cuyo `AppointmentConfirmed` no llegó. Son los casos que iniciar una reserva y el vencimiento dejan explícitamente «para la reconciliación».

## Decisión

- **Una vez, al terminar de arrancar.** Un caso de uso (`RecoverReservationsUseCase`) lo dispara un adaptador de entrada sobre `ApplicationReadyEvent`, en el hilo de arranque y con el servidor ya atendiendo. Está acotado por una tanda (`RECOVERY_BATCH_SIZE`, 100, con valor por defecto y el entorno gana) y cada proceso se cierra en su propia operación. El interruptor `recovery.enabled` lo apagan los tests.
- **Huérfanos.** Los `HOLD_REQUESTED` sin `holdId` ni `reservationProcessId` pasan a `FAILED` con `HOLD_RESULT_UNKNOWN`, el motivo que ya usa iniciar una reserva cuando se pierde la respuesta del hold. El hold que la cátedra haya creado vence solo: no hay forma de recuperar sus ids.
- **La guarda del arranque.** Tomcat acepta pedidos antes de que se dispare el evento; un `HOLD_REQUESTED` de un pedido en curso se parecería a uno huérfano. Solo se fallan los creados antes de `ApplicationContext.getStartupDate()`; los creados después son pedidos de esta instancia.
- **Reconciliar los `PHONE_QUEUED` y `PHONE_SUBMITTED`** con `GET /api/appointments` (confirmadas del paciente en el día del turno: [decisión 023](023-reconciliacion-por-fecha.md)): los que la cátedra tiene confirmados se reparan a `CONFIRMED` con su `reservationId`. Solo se actúa con evidencia: que no figuren no prueba que haya que cerrarlos, y ante una respuesta incompleta o una cátedra caída no se toca nada.
- **Componente compartido con el vencimiento.** La pregunta «¿la cátedra lo tiene confirmado?» y la aplicación de un cambio sin que un choque de versión frene a quien llama se extrajeron a `CatedraReconciliation`, que usan la tarea de vencimiento ([decisión 016](016-vencimiento-de-procesos.md)) y la recuperación. Un solo lugar para la regla de «ante la duda no se actúa».
- **Carrera con el consumer de Kafka,** que repasa su atraso mientras corre la recuperación: segura por el bloqueo optimista; el segundo ve `ALREADY_IN_STATE`. Una prueba lanza la recuperación y una confirmación a la vez, 20 veces.
- **Cátedra caída al arrancar:** esos procesos quedan como están, sin reintentos en bucle. Siguen protegidos por la consulta previa del vencimiento y el próximo arranque lo reintenta.

## Alternativas descartadas

- Repetir el `confirm` de los `HELD`: el contrato dice que es seguro, pero le crearía una reserva esperando teléfono a un usuario que ya vio un error al reservar. Recuperar no debe arrancar acciones nuevas con efectos. Los `HELD` los resuelve el evento que Kafka reentrega o, si no, el vencimiento.
- Comparar también los `CONFIRMED` con las canceladas de la cátedra: útil, pero un evento de cancelación perdido es muy raro porque Kafka lo retiene. Queda como extensión.
- Fallar todos los `HOLD_REQUESTED` sin la guarda: fallaría un pedido que está andando.
- Reconciliación periódica o reintentos: queda fuera: el vencimiento ya protege lo dudoso.
- Esperar a que el consumer termine de repasar su atraso: innecesario con el bloqueo optimista.
- Correrla en un hilo propio o asincrónico: no hay tareas asincrónicas propias y el hilo de arranque no atiende pedidos.

## Consecuencias

un `HOLD_REQUESTED` huérfano queda en `FAILED` y el usuario puede reservar de nuevo ese horario cuando el hold de la cátedra venza (hasta 10 minutos). Con la cátedra caída al arrancar, las reservas con teléfono enviado esperan al próximo arranque o a que llegue su evento.
