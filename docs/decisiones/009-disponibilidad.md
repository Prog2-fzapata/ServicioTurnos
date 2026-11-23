# 009. Calcular la disponibilidad en turnos y bloquear la categoría deshabilitada

**Estado:** aceptada

## Contexto

`GET /api/availability` debe devolver los horarios libres de un profesional en un rango de fechas. La agenda vive en catálogo (horarios semanales) y lo ocupado, en la cátedra (reservas confirmadas y holds activos). Las horas de la cátedra son locales de atención, sin zona horaria.

## Decisión

- **Generación de slots.** Para cada día del rango y cada horario semanal habilitado de ese día de la semana se parte de `startTime` y se avanza de a `slotDurationMinutes` mientras el slot termine en o antes de `endTime`. Los horarios que se superponen se deduplican por fecha e inicio. Un horario con duración cero o negativa se ignora, porque no avanzaría nunca. La cuenta se hace en segundos desde el inicio hasta el cierre, no sumando a la hora, porque sumar pasada la medianoche vuelve a las 00:00 y el bucle no terminaría.
- **Ocupaciones.** Se restan `CONFIRMED` y `HELD`. La respuesta es una foto: lo decisivo ocurre al crear el hold.
- **Horarios pasados y zona.** Un slot es pasado si su inicio es anterior a «ahora» en la zona de atención. Un `Clock` inyectable lleva la zona (`AVAILABILITY_ZONE`, por defecto `America/Argentina/Mendoza`); los tests lo reemplazan por uno fijo.
- **Rango.** `to` no anterior a `from` y, como máximo, `AVAILABILITY_MAX_RANGE_DAYS` (31) días contando ambos extremos. Ambos son parámetros de ajuste: valor por defecto en `application.properties` y el entorno gana. Se valida en `AvailabilityQueryMapper`, antes de llamar a catálogo o a la cátedra.
- **Categoría deshabilitada.** Bloquea a sus profesionales (cada profesional tiene una sola categoría). Para saberlo, turnos lee `categoryEnabled` en `GET /api/professionals/{id}`; si catálogo no lo informa, turnos lo trata como un desajuste entre servicios (`500` genérico y sin reintentos) en lugar de suponer un valor. Un profesional o una categoría deshabilitados responden `400 PROFESSIONAL_DISABLED`, el mismo código que usa la cátedra, y no se consulta a la cátedra.
- **Orden y fallos.** Primero catálogo y, si el profesional se puede reservar, la cátedra, en secuencia. Si cualquiera falla, `503` con su código: no se devuelve una disponibilidad parcial.
- **Capas.** El caso de uso devuelve `Optional` (vacío si el profesional no existe) y lanza la excepción de profesional deshabilitado, que es una regla de negocio que decide con el profesional que ya trajo. `ReservaService` traduce el vacío en `404`.

## Alternativas descartadas

- No bloquear por categoría deshabilitada, como hace la cátedra: se prefirió que no se ofrezcan horarios de una categoría apagada.
- Que turnos consulte `GET /api/professional-categories` aparte: no hay endpoint por id, sería una llamada más por consulta y otro motivo de `503`.
- Que `enabled` del profesional pase a ser «profesional Y categoría habilitados»: se pierde saber qué se apagó y cambia el significado de un campo que ya está en `main`.
- Restar solo `CONFIRMED`: ofrecería horarios que fallan con `409` al reservar.
- Consultar catálogo y cátedra en paralelo: llamaría a la cátedra por profesionales que no existen o están deshabilitados.
- Medir «ahora» en UTC: Mendoza va 3 horas atrás y se descartarían o dejarían pasar horarios cercanos a la medianoche.
- Un código nuevo para la categoría (`CATEGORY_DISABLED`): el cliente no necesita distinguir el motivo y se mantiene el código del contrato.

## Consecuencias

contra un catálogo que no publique `categoryEnabled` (anterior a su decisión 029), turnos responde `500` a toda consulta de disponibilidad; por eso catálogo se despliega primero. Entre la consulta y el hold el horario puede dejar de estar libre. Los tests cubren el cálculo con reloj fijo, el contrato HTTP y el flujo completo contra un catálogo y una cátedra simulados, y se verificó rompiendo el código a propósito en veinte puntos.
