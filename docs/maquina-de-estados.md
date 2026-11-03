# Máquina de estados de la reserva

Cada reserva es un proceso local (tabla `reservation_processes`) que avanza por estos estados. REST y Kafka le informan lo que pasa en la cátedra y llegan en momentos distintos: por eso el proceso solo avanza, y un evento tardío o repetido nunca lo hace retroceder.

```mermaid
stateDiagram-v2
    [*] --> HOLD_REQUESTED: usuario elige horario
    HOLD_REQUESTED --> HELD: 201 hold creado
    HOLD_REQUESTED --> FAILED: 409 / 400 / 404
    HOLD_REQUESTED --> FAILED: resultado desconocido (timeout / 5xx), el hold huérfano vence solo

    HELD --> WAITING_PHONE: 202 confirm
    HELD --> EXPIRED: HOLD_EXPIRED / expiresAt vencido
    HELD --> FAILED: error no recuperable

    WAITING_PHONE --> PHONE_QUEUED: la app envía el teléfono (estado + outbox en una transacción)
    WAITING_PHONE --> EXPIRED: AppointmentProcessExpired / expiresAt
    WAITING_PHONE --> FAILED: AppointmentProcessInvalid

    PHONE_QUEUED --> PHONE_SUBMITTED: el relay publica en Kafka y marca SENT
    PHONE_QUEUED --> CONFIRMED: AppointmentConfirmed llega antes del ack del relay
    PHONE_QUEUED --> WAITING_PHONE: AdditionalInformationRejected llega antes del ack del relay
    PHONE_QUEUED --> EXPIRED: vence antes de poder enviarse
    PHONE_QUEUED --> FAILED: AppointmentProcessInvalid

    PHONE_SUBMITTED --> CONFIRMED: AppointmentConfirmed
    PHONE_SUBMITTED --> WAITING_PHONE: AdditionalInformationRejected
    PHONE_SUBMITTED --> EXPIRED: AppointmentProcessExpired
    PHONE_SUBMITTED --> FAILED: AppointmentProcessInvalid

    CONFIRMED --> CANCELLED: cancelación propia / AppointmentCancelled

    EXPIRED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

## Reglas

- **Estados finales:** `EXPIRED`, `FAILED` y `CANCELLED`. Son inmutables: ni un evento tardío ni uno duplicado los reabre. `CONFIRMED` solo puede pasar a `CANCELLED`.
- **Un solo lugar cambia el estado.** Todo paso pasa por un único caso de uso (`ApplyReservationTransitionUseCase`), que responde:
  - `APPLIED`: el estado cambió.
  - `ALREADY_IN_STATE`: el proceso ya estaba en el estado pedido; no cambia nada. Se mira antes que la tabla, y hace inocuo repetir un paso (un evento duplicado, una cancelación repetida).
  - `REJECTED`: el diagrama no permite ese paso desde el estado actual. Se ignora y se registra; no es un error ni retrocede nada.
- **Gana el estado más avanzado.** La cátedra puede contestar antes de que el relay anote que el teléfono se envió: por eso desde `PHONE_QUEUED` también se aceptan `CONFIRMED` y `WAITING_PHONE`. El relay, en cambio, solo pasa a `PHONE_SUBMITTED` desde `PHONE_QUEUED`; si el proceso ya avanzó, ese paso es `REJECTED`.
- **Bloqueo optimista.** Cada proceso lleva una `version`. Si dos operaciones lo modifican a la vez, la segunda recibe un conflicto, relee el proceso y decide de nuevo sobre el estado vigente, hasta `RESERVATION_CONFLICT_MAX_ATTEMPTS` intentos (3 por defecto). Agotados, la operación falla con un conflicto. El reintento no es válido dentro de una transacción ya abierta.
- **Respuesta perdida del hold:** la cátedra no tiene idempotencia ni permite recuperar los ids de un hold. Un proceso en `HOLD_REQUESTED` sin respuesta pasa a `FAILED`; antes de crear otro hold se consultan las ocupaciones del profesional, y si el horario aparece `HELD` se espera su `expiresAt` sin asumir que el hold es nuestro. Un `409` nunca se toma como «es mi hold».
- Cada proceso guarda el usuario dueño, el `reservationProcessId` y el `holdId` de la cátedra, el `requestEventId`, `expiresAt`, los datos del horario y los datos descriptivos del profesional al momento de reservar.

Las decisiones y las alternativas descartadas están en [decisiones.md](decisiones.md), entrada 10.
