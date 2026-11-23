# 019. Auditar los logs y comprobar que no filtran secretos

**Estado:** aceptada

## Contexto

el enunciado y el contrato de integración piden que ni el JWT ni las contraseñas lleguen a un log, y se agregó el teléfono. Los loggers de negocio ya escribían solo ids y estados (los casos de uso no loguean: lo hace quien los dispara), pero cada uno se probaba por separado: nada comprobaba el servicio entero. Y el mensaje de una excepción lo escribe una librería, no nosotros, así que loguear la excepción entera podía arrastrar datos de un pedido.

## Decisión

- **Se auditaron todas las líneas de log de `src/main`** (unas 30 en 14 clases) y los `toString` que podían llegar a una. No hizo falta cambiar código: las líneas de negocio llevan ids y estados, los clientes de catálogo y de la cátedra solo el status o el nombre de la clase de la excepción, el manejador de la base caída solo el nombre de la clase, y los modelos con nombre o teléfono no tienen `toString`. Los errores inesperados siguen registrando la excepción entera (mensaje y traza) porque son los que hay que diagnosticar.
- **Un test que captura todo el log y busca valores reconocibles** (`SecretsNotInLogsIntegrationTest`, y el caso de la base caída en `DatabaseDownIntegrationTest`): corre los flujos reales contra un PostgreSQL y un Kafka reales en contenedores (Testcontainers), con catálogo y cátedra simulados como servidores HTTP, y con un JWT de usuario, un JWT técnico, un teléfono, un nombre y un motivo de cancelación únicos. Captura el mensaje **y la traza** de cada excepción, lo propio a nivel `DEBUG` y el resto como en producción. Antes de buscar el valor comprueba que sí viajó por el flujo (por ejemplo, que catálogo recibió el JWT del usuario), para que no pase en vacío. Corren el relay y el listener reales, incluidos los mensajes inválidos que llegan al manejador de errores de Kafka.
- **Se comprobó que el test detecta fugas:** se agregó a propósito, de a una, una línea que escribe el teléfono en el logger del relay, el JWT en el interceptor que lo reenvía, el nombre del paciente en el mapper de la cuenta y el teléfono en el controller; cada una hizo fallar al menos un test.
- **«Observabilidad básica» queda en lo que hay:** el id del proceso en cada línea, los avisos de reintentos y de vueltas dudosas, y `/actuator/health` como único endpoint de `actuator`. La política queda en la sección «Logs» del README.

## Alternativas descartadas

- Un identificador por pedido en el contexto del log (MDC): cada línea de negocio ya lleva el id del proceso, y el MDC exigiría engancharlo en tres entradas distintas (HTTP, el listener de Kafka y las tareas programadas) para un beneficio que el enunciado no pide.
- Exponer más endpoints de `actuator` (métricas, `loggers`): agranda la superficie expuesta de un servicio cuyo criterio de seguridad es exponer lo mínimo.
- Registrar solo el nombre de la clase de toda excepción: ocultaría la traza justo de los errores que no se esperaban y que hay que diagnosticar.
- Probar cada logger por separado: es lo que ya había, y no detecta que otra clase escriba el dato por su cuenta.

## Consecuencias

agregar un log nuevo que escriba un dato sensible hace fallar el test si el dato pasa por alguno de los flujos probados. No cubre los flujos que no se corren (el vencimiento, la recuperación y el latido del stream, que tienen sus propios tests de logger y escriben solo ids) ni un dato que llegue en el mensaje de una excepción que el test no provoque. Un dato personal nuevo se cubre sumando su valor a la lista de valores reconocibles del test.
