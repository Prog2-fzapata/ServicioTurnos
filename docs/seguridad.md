# Seguridad

Este documento reúne las decisiones de autenticación, autorización y protección de datos del servicio de turnos. El contrato de los endpoints está en [contrato.md](contrato.md) y la justificación de cada decisión, en [decisiones/](decisiones/README.md).

## En resumen

- **Autenticación:** turnos no emite tokens: valida el JWT firmado con HS512 que emite catálogo, con el mismo secreto. No guarda contraseñas ni credenciales de usuarios.
- **Autorización:** todo exige un token válido salvo la salud del servicio y la documentación de la API. El usuario de cada pedido sale siempre del token, y cada reserva solo la ve su dueño.
- **Errores:** un rechazo no revela el motivo: un token inválido y uno vencido dan la misma respuesta, y una reserva ajena responde igual que una inexistente.
- **Secretos:** salen del entorno, no tienen valor por defecto y el servicio no arranca si faltan o son débiles. No aparecen en los logs, y un test lo comprueba.

## El token

- **Firma HS512** con el secreto compartido `JWT_SECRET`: una clave en Base64 de al menos 512 bits, la misma que usa catálogo para firmar. Si falta, no es Base64 o es corta, el servicio no arranca. No hay valor por defecto ([decisión 002](decisiones/002-jwt-hs512-secreto-compartido.md)).
- **Qué exige.** Que la firma sea válida y que no haya vencido ni sea anterior a su `nbf`. Que traiga `uid` y `auth` ([decisión 003](decisiones/003-claims-obligatorios-uid-y-auth.md)) y `exp`: el validador por defecto de Spring aceptaba un token bien firmado y sin `exp`, que no vencería nunca ([decisión 020](decisiones/020-claim-exp-obligatorio.md)).
- **Solo HS512.** Un token firmado con otro algoritmo se rechaza aunque use la clave correcta.
- **Qué rechaza**, con un test para cada caso (`SecurityRejectionTest`): sin token, mal formado, vencido, firmado con otra clave, con otro algoritmo, con el cuerpo alterado, sin `uid`, sin `auth` y sin `exp`.
- **Respuesta al rechazo:** siempre `401 UNAUTHORIZED` en `application/problem+json`, con la cabecera `WWW-Authenticate: Bearer` pero sin el motivo: el cliente solo necesita saber que debe volver a autenticarse ([decisión 005](decisiones/005-respuestas-401-y-403.md)).
- **La seguridad se copió de catálogo, no se comparte:** los dos servicios tienen repositorios y despliegues independientes, y una librería común obligaría a publicar y versionar un artefacto para unas pocas clases. Un cambio de seguridad hay que aplicarlo en los dos ([decisión 004](decisiones/004-seguridad-copiada-de-catalogo.md)).

## Autorización

- **Rutas públicas:** solo `GET /actuator/health` y la documentación de la API (`/v3/api-docs` y `/swagger-ui`). Todo lo demás exige un token válido, también las rutas que no existen.
- **Roles:** hoy todo usuario tiene `ROLE_USER` y lo que se aísla es el acceso por usuario, con el `uid`. La seguridad por método (`@PreAuthorize`) está habilitada para pedir roles sin cambiar la configuración; un usuario autenticado sin el rol necesario recibe `403 FORBIDDEN`. Como ningún endpoint real pide un rol, un test lo ejercita con un controller de prueba.
- **Sin sesión:** cada pedido trae su JWT y el servidor no guarda estado de autenticación entre pedidos.

## Aislamiento de las reservas por usuario

- **El usuario sale siempre del claim `uid`**, que el controller lee del token: nunca del cuerpo, de la URL ni de un parámetro. No hay forma de pedir las reservas de otro.
- **Toda búsqueda es por `id` y por dueño.** Consultar, cancelar y enviar el teléfono usan `findByIdAndUserId`: la fila se encuentra solo si es del usuario del token. La lista filtra por `user_id`.
- **Una reserva ajena responde `404 RESERVATION_NOT_FOUND`**, con el mismo cuerpo que una inexistente. Un `403` revelaría que la reserva existe ([decisión 012](decisiones/012-consulta-y-cancelacion.md)).
- **El stream de cambios** entrega a cada conexión solo los avisos de las reservas de su usuario, y hay un tope de conexiones por usuario (5 por defecto) para que una app que reconecta sin cerrar no acumule conexiones.
- **Lo que la respuesta no trae:** ni el usuario dueño ni los ids internos del bloqueo (`holdId`).

## El JWT hacia catálogo

Turnos le pide a catálogo la agenda de un profesional y el nombre del paciente **con el JWT del propio usuario**: un interceptor del cliente HTTP lee el token del usuario autenticado y lo reenvía en `Authorization: Bearer`. Catálogo lo valida con el mismo secreto ([decisión 007](decisiones/007-cliente-de-catalogo.md)).

- **El token no se pasa por parámetro** a través de las capas: el puerto de salida no menciona tokens, que son un detalle de HTTP y no llegan al dominio ni a los casos de uso.
- **Sin un usuario autenticado el pedido no se hace** (error de programación): todo pedido a turnos exige token.
- **Los dos JWT no se mezclan:** catálogo recibe el del usuario y la cátedra, el técnico. Un test lo comprueba sobre los pedidos reales de punta a punta.
- **Un `401` o un `403` de catálogo** (por ejemplo, por un secreto distinto en cada servicio) es un desajuste y no una caída: no se reintenta, se responde `500` genérico y el log nombra el problema sin mostrar el token.

## La cuenta técnica de la cátedra

- **Qué es.** Un JWT técnico del proyecto (`CATEDRA_JWT`), el mismo para los dos servicios. Identifica a turnos ante la cátedra y **nunca sale del backend**: la aplicación no lo conoce. El dueño de la reserva llega a la cátedra como `externalPatientId` ([decisión 008](decisiones/008-cliente-de-la-catedra.md)).
- **Cómo se protege.** Llega solo por el entorno, no tiene valor por defecto y el servicio no arranca si falta. `CatedraProperties` oculta su valor en `toString()`, de modo que un objeto que llegue por error a un log no lo imprime. Los mensajes de error y los logs de los clientes registran el status y el código, nunca el cuerpo de la respuesta ni las cabeceras.
- **Si vence o es de otra cuenta**, la cátedra responde `401` o `403`: es un error de configuración, se responde `500` genérico y el log lo señala sin mostrar el token.
- **Kafka.** El broker de la cátedra no pide autenticación ni cifrado, así que no hay credenciales que configurar; solo se usa el servidor y el grupo de la cuenta.

## Entradas

- Los cuerpos se validan con Bean Validation y los errores devuelven `400 VALIDATION_ERROR` con el campo y el motivo. Un parámetro de tipo equivocado devuelve `400` antes de llegar al controller.
- El motivo de una cancelación admite hasta 500 caracteres, y el teléfono hasta 40 antes de normalizarlo; después de quitar los separadores debe tener entre 7 y 15 dígitos, con un `+` opcional ([decisión 014](decisiones/014-envio-del-telefono-con-outbox.md)).
- El rango de la disponibilidad tiene un máximo de 31 días, validado antes de llamar a catálogo o a la cátedra.
- En la lista de reservas el orden es fijo (el `sort` del pedido se ignora) y el `size` tiene un tope: un valor mayor se reduce.
- Las respuestas de error no exponen mensajes internos: el `500` lleva un detalle genérico, y el mensaje de una excepción, que puede traer datos del pedido, solo llega al log.

## Secretos y datos sensibles

- Los secretos (`JWT_SECRET`, la clave de la base y `CATEDRA_JWT`) salen de variables de entorno, definidas en un `.env` que **nunca se versiona**. `.env.example` es la plantilla y trae `cambiar` en lugar de cada secreto. Ninguno tiene valor por defecto y, si falta uno, el servicio no arranca con un mensaje que nombra la propiedad.
- **Datos del paciente.** El nombre, el apellido y el teléfono se guardan en `reservation_processes` y se devuelven solo a su dueño. Ni ellos ni el motivo de una cancelación se escriben en ningún log.
- **Los logs no llevan secretos, y está probado.** Todas las líneas se escriben con un mensaje fijo y los datos como campos, y `LogStyleTest` falla si alguien escribe una de otra forma ([decisión 022](decisiones/022-logs-en-json-con-campos.md)). `SecretsNotInLogsIntegrationTest` corre los flujos reales (iniciar, teléfono con y sin confirmación de Kafka, cancelar, errores de catálogo y de la cátedra, el relay y el listener con mensajes válidos e inválidos) con un JWT de usuario, un JWT técnico, un teléfono, un nombre y un motivo reconocibles, captura mensaje, campos, contexto y la traza completa con todas sus causas, y verifica que ninguno aparece. Antes comprueba que el flujo sí dejó sus líneas, para que no pase en vacío ([decisión 019](decisiones/019-logs-sin-secretos.md)).
- **Una fuga real que el test encontró:** los adaptadores de catálogo y de la cátedra pasaban la excepción original de Spring como causa de la propia, y su mensaje trae el cuerpo de la respuesta, que aparecía en la traza de un error inesperado. Ya no se pasa como causa en las respuestas con cuerpo.
- `DatabaseDownIntegrationTest` hace lo mismo con la base caída y su contraseña.

## Superficie expuesta

- **CSRF desactivado:** CSRF protege a las aplicaciones que identifican al usuario con una cookie de sesión. Esta API no usa sesión ni cookies: cada pedido trae su JWT en `Authorization`, que un sitio ajeno no puede agregar. SonarCloud marca la línea como vulnerabilidad crítica sin mirar el resto de la configuración, por eso lleva un `NOSONAR` con el motivo ([decisión 005](decisiones/005-respuestas-401-y-403.md)).
- **CORS queda fuera:** lo aplican los navegadores y el único cliente es la aplicación Android, así que no hay ningún origen que permitir.
- **Documentación de la API:** `/swagger-ui` y `/v3/api-docs` responden sin token y están activos por defecto, porque forman parte de lo que se entrega. El perfil `prod` (`SPRING_PROFILES_ACTIVE=prod`) los desactiva para no revelar la superficie de la API en un despliegue real ([decisión 024](decisiones/024-api-docs-apagable-por-perfil.md)); un despliegue real tiene que declararlo.
- **`actuator`:** solo se expone `/actuator/health`, y no depende de catálogo ni de la cátedra.
- **Contenedores:** la imagen final solo trae el JRE y el JAR, corre con un usuario sin privilegios y los puertos se publican solo en `127.0.0.1`.
- **Mensajes de Kafka:** el teléfono que se publica lleva solo lo que exige la cátedra; el JWT nunca viaja en un mensaje.

## Límites conocidos

- **Un token no se puede revocar** antes de que venza: no hay una lista de tokens. Quien conozca `JWT_SECRET` puede fabricar tokens, así que el secreto debe cuidarse igual en los dos servicios.
- **Un stream puede seguir abierto unos segundos de más.** El servidor cierra una conexión en el primer latido posterior al vencimiento del token (cada 25 segundos por defecto) y el decodificador tolera 60 segundos de desfase de reloj, así que reconectar con un token vencido hace menos que eso se acepta y se cierra de nuevo en el siguiente latido. La app tiene que renovar el token antes de reconectar.
- **No hay límite de pedidos por usuario.** Nada frena a quien inicia muchas reservas seguidas más allá de lo que haga la cátedra con los bloqueos.
- **El teléfono del paciente se guarda en claro** en la base de datos, sin cifrado de columna.
- **Una sola instancia.** El registro de conexiones del stream vive en memoria y el relay del outbox no está pensado para varias instancias.
- **Sin HTTPS en local.** El servicio escucha en HTTP; en un entorno real tiene que publicarse detrás de HTTPS.
