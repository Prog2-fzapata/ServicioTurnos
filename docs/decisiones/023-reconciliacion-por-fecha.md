# 023. Filtrar la reconciliación por la fecha del turno

**Estado:** aceptada

## Contexto

la [decisión 021](021-ajustes-de-la-prueba-real.md) sacó `from` y `to` de la consulta de reconciliación porque la cátedra respondía `500` a esos filtros, y dejó una cota «por paciente»: con más de 100 turnos confirmados la respuesta quedaba incompleta y no se actuaba. La cátedra corrigió el filtro. Una prueba real (2026-11-11) sobre un turno creado para la prueba (`PHONE_PENDING`, fecha 2026-11-24) mostró que `from`, `to` y los dos juntos responden `200` y filtran bien: el turno aparece con `from=to` el día del turno, con un rango que lo contiene, con `from` del día anterior y con `to` del día siguiente, y no aparece con `from` del día siguiente, con `to` del día anterior ni con un rango futuro.

## Decisión

- `AppointmentQuery` recupera `from` y `to`, y `CatedraApiClientAdapter.findAppointments` los manda cuando están presentes.
- `CatedraReconciliation.askIfConfirmed` pide los turnos confirmados del paciente **en el día del turno del proceso** (`from` y `to` iguales a `appointmentDate`), así que el vencimiento y la recuperación vuelven a acotar por día. El proceso se sigue buscando por su `reservationProcessId` entre los resultados, y una respuesta con más resultados que la página (100) sin que aparezca el proceso sigue sin actuar.
- `CatedraReconciliationOverHttpIntegrationTest` simula una cátedra que filtra por fecha como la real, y un test nuevo comprueba que un paciente con 120 turnos confirmados en otras fechas igual se resuelve.

## Alternativas descartadas

- Seguir sin `from` ni `to`: funciona, pero la cota queda «por paciente» y la respuesta puede ser incompleta con más de 100 turnos confirmados.
- Un rango de varios días alrededor del turno: trae más resultados sin ganar nada, porque el proceso tiene una fecha exacta.

## Consecuencias

la consulta devuelve a lo sumo los turnos del paciente en un día, así que una sola página alcanza aunque tenga muchos en otras fechas. Si la cátedra volviera a fallar con las fechas, la consulta daría error y, ante la duda, no se actuaría: el proceso quedaría sin comprobar. La prueba contra la cátedra real se hizo con un turno en `PHONE_PENDING` y no en `CONFIRMED` (confirmar de verdad exige el teléfono por Kafka); el filtro de fechas no depende del estado.
