# Decisiones de diseño: servicio de turnos

Registro de las decisiones técnicas de este servicio, una por archivo (formato ADR): contexto, decisión, alternativas descartadas y consecuencias. Una sección que no aporta información se omite. Cada decisión se agrega cuando queda implementada en el código; si una se reemplaza no se borra: pasa a `reemplazada por NNN` y la nueva la referencia.

Estados posibles: `aceptada`, `reemplazada por NNN` y `obsoleta`.

| N.º | Decisión | Estado |
|---|---|---|
| 001 | [Probar la integración con Testcontainers](001-pruebas-con-testcontainers.md) | aceptada |
| 002 | [Validar el JWT con el secreto compartido de catálogo (HS512)](002-jwt-hs512-secreto-compartido.md) | aceptada |
| 003 | [Exigir los claims uid y auth](003-claims-obligatorios-uid-y-auth.md) | aceptada |
| 004 | [Copiar la seguridad de catálogo en lugar de compartirla](004-seguridad-copiada-de-catalogo.md) | aceptada |
| 005 | [Responder los 401 y 403 en problem+json y proteger por método](005-respuestas-401-y-403.md) | aceptada |
| 006 | [Definir el esquema de la base de turnos en la migración V1](006-esquema-de-la-base-de-turnos.md) | aceptada |
| 007 | [Consultar catálogo con el JWT del usuario, reintentos y errores propios](007-cliente-de-catalogo.md) | aceptada |
| 008 | [Consultar la cátedra con el JWT técnico y reintentos distintos por operación](008-cliente-de-la-catedra.md) | aceptada |
| 009 | [Calcular la disponibilidad en turnos y bloquear la categoría deshabilitada](009-disponibilidad.md) | aceptada |
| 010 | [Modelar la reserva como máquina de estados con bloqueo optimista](010-maquina-de-estados.md) | aceptada |
| 011 | [Iniciar una reserva sin dejar rastro hasta crear el proceso](011-iniciar-una-reserva.md) | aceptada |
| 012 | [Aislar las reservas por usuario y cancelar primero en la cátedra](012-consulta-y-cancelacion.md) | aceptada |
| 013 | [Procesar cada evento de la cátedra en una transacción, con el reintento por fuera](013-consumer-de-eventos-de-la-catedra.md) | aceptada |
| 014 | [Enviar el teléfono con un outbox transaccional y un relay](014-envio-del-telefono-con-outbox.md) | aceptada |
| 015 | [Avisar los cambios de la reserva por SSE después del COMMIT](015-stream-de-cambios-sse.md) | aceptada |
| 016 | [Vencer los procesos con un margen y una consulta previa a la cátedra](016-vencimiento-de-procesos.md) | aceptada |
| 017 | [Recuperar al arrancar solo lo que tiene evidencia](017-recuperacion-al-arrancar.md) | aceptada |
| 018 | [Acotar los reintentos y los timeouts y definir la degradación](018-reintentos-timeouts-y-degradacion.md) | aceptada |
| 019 | [Auditar los logs y comprobar que no filtran secretos](019-logs-sin-secretos.md) | aceptada |
| 020 | [Exigir el claim exp en el JWT](020-claim-exp-obligatorio.md) | aceptada |
| 021 | [Ajustar la reconciliación y los errores tras la prueba real contra la cátedra](021-ajustes-de-la-prueba-real.md) | aceptada, en parte reemplazada por 023 |
| 022 | [Escribir los logs en JSON, con los datos como campos](022-logs-en-json-con-campos.md) | aceptada |
| 023 | [Filtrar la reconciliación por la fecha del turno](023-reconciliacion-por-fecha.md) | aceptada |
