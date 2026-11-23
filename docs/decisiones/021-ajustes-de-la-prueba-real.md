# 021. Ajustar la reconciliación y los errores tras la prueba real contra la cátedra

**Estado:** aceptada; la reconciliación sin filtro por fecha la reemplaza la [decisión 023](023-reconciliacion-por-fecha.md)

## Contexto

la prueba real del 2026-11-06 (catálogo y turnos contra la cátedra de verdad) pasó de punta a punta, pero dejó tres ajustes. Los tests no los habían visto porque simulaban a la cátedra.

## Decisión

- **Reconciliación sin filtro por fecha.** `GET /api/appointments` de la cátedra responde `500 Failure during data access` si recibe `from` o `to` con una fecha válida (con un formato inválido responde `400`, y con `status` y `externalPatientId` responde `200`, también con `size` de 100 y de 500). El vencimiento y la recuperación mandaban justo esos filtros con la fecha del turno, así que con la cátedra así los procesos en `PHONE_QUEUED` y `PHONE_SUBMITTED` quedaban sin comprobar: ni se expiraban ni se reparaban. Ahora se consulta solo con `status=CONFIRMED` y `externalPatientId` (página de 100) y el proceso se busca por su `reservationProcessId` entre los resultados, que es lo que ya se hacía: la fecha solo achicaba la página. `AppointmentQuery` pierde `from` y `to` para que nadie vuelva a usar un filtro que falla. **Reemplazado por la [decisión 023](023-reconciliacion-por-fecha.md):** la cátedra corrigió el filtro y se vuelve a mandar.
- **Un test que se comporta como la cátedra real.** Los tests de vencimiento y de recuperación mockeaban el puerto y nunca ejercitaban la consulta HTTP. `CatedraReconciliationOverHttpIntegrationTest` usa el adaptador real contra un servidor simulado que responde `500` si le llegan `from` o `to`, y comprueba la consulta exacta que sale.
- **`expiresAt` en una reserva confirmada.** El contrato mostraba `expiresAt: null` para `CONFIRMED`, pero el código solo lo anulaba en los estados finales (`EXPIRED`, `FAILED`, `CANCELLED`) y el detalle y el stream seguían mostrando el vencimiento del hold, que ya no significa nada. `CONFIRMED` no es final (todavía se puede cancelar), así que no se tocó `isFinal()`: se agregó `ReservationStatus.isExpirable()`, apoyado en la lista `EXPIRABLE` que ya usa la tarea de vencimiento, y los dos mapeadores muestran `expiresAt` solo en esos estados.
- **Choque de versión agotado: `409 RESERVATION_CONFLICT`.** Iniciar, enviar el teléfono y cancelar reintentan el choque de versión hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS`; agotado, la excepción llegaba al manejador compartido como `500 INTERNAL_ERROR` con la traza, aunque no es un error propio. Ahora `ReservaExceptionHandler` lo traduce a `409` con el código `RESERVATION_CONFLICT` y deja un aviso de una línea con la ruta (antes quedaba la traza del `500`). El mensaje dice «consulte su estado antes de repetir el pedido» y no «intente nuevamente»: al cancelar, la cátedra se cancela primero y el estado local después, así que si el choque se agota en ese segundo paso el turno ya está cancelado y repetir ciegamente daría `RESERVATION_NOT_CANCELLABLE` hasta que llegue el `AppointmentCancelled`, que corrige el estado local (se vio en la prueba real).

## Alternativas descartadas

- Esperar a que la cátedra arregle el filtro: deja la reconciliación inutilizada mientras tanto y depende de un tercero.
- Dejar `from` y `to` en `AppointmentQuery` y que solo la reconciliación no los mande: queda una capacidad del cliente que sabemos que falla.
- Para `expiresAt`, escribir `isFinal() || == CONFIRMED` en cada mapeador: dos copias de una regla que ya tiene su lista (`EXPIRABLE`).
- Para el choque de versión, `503` (no es una dependencia caída) o dejar el `500` (el cliente no distingue un fallo propio de uno que se resuelve consultando el estado y repitiendo).

## Consecuencias

la consulta trae todos los turnos confirmados del paciente y no solo los de ese día. Es una cota por paciente en lugar de por día: si algún día un paciente tuviera más de 100 confirmados, la respuesta se considera incompleta y no se actúa (ante la duda no se actúa, como antes). Con un solo paciente y pocas reservas no se nota. Para la app (KMP): una reserva `CONFIRMED` ya no trae `expiresAt` (no hay que mostrar una cuenta regresiva) y `RESERVATION_CONFLICT` es un código `409` nuevo, que se trata como «consultar el estado y, si hace falta, repetir».
