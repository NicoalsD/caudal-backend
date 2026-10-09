# Historias de usuario

Este documento define 39 historias de usuario (HU-01 a HU-39), agrupadas por módulo (M1 a M13). Cada historia tiene actor, enunciado, prioridad MoSCoW, criterios de aceptación en Gherkin, requisitos relacionados y patrones de diseño involucrados.

Convenciones:

- **Prioridad (MoSCoW):** `must` (sin esto no hay MVP), `should` (importante, con alternativa temporal), `could` (deseable).
- **Requisitos:** `RF-xx` son requisitos funcionales y `RNF-xx` no funcionales, definidos en [Requerimientos.md](Requerimientos.md).
- **Patrones:** `Pxx` del catálogo de la sección 16 de los hechos canónicos. Cuando una clase no aparece en ese catálogo, se marca "(clase propuesta, por definir)".
- **Códigos de error:** los códigos son `UPPER_SNAKE_CASE` en inglés, según el catálogo de [`API.md`](API.md), y el mensaje para el usuario va en español.

| Código | Significado | HTTP |
|---|---|---|
| `INVALID_CREDENTIALS` | Usuario o contraseña incorrectos. Mensaje genérico. | 401 |
| `UNAUTHORIZED` | Token ausente, vencido, con `tv` no vigente o inválido. | 401 |
| `PASSWORD_CHANGE_REQUIRED` | La cuenta debe cambiar la contraseña antes de usar la API. | 403 |
| `FORBIDDEN` | El rol no tiene el permiso, o la membresía no está vigente. | 403 |
| `NOT_FOUND` | El recurso no existe o pertenece a otro acueducto. | 404 |
| `RATE_LIMITED` | Se superó un límite de tasa. Incluye `Retry-After`. | 429 |
| `VALIDATION_ERROR` | Un campo no cumple sus límites. `details` indica el campo. | 400 |
| `GAUGE_OUT_OF_RANGE`, `MISSING_TIMESTAMP`, `FUTURE_TIMESTAMP`, `TOO_OLD` | Validación de lectura (ver HU-11). La lectura no se guarda. | 422 |
| `REASON_REQUIRED` | Falta el motivo (10 a 500 caracteres). | 422 |
| `RULE_SET_IMMUTABLE`, `RULE_SET_IMMUTABLE` (propuestos) | Solo se edita un borrador; una regla activa no cambia. | 409 |
| `INVALID_STATE_TRANSITION`, `INVALID_STATE_TRANSITION` (propuestos) | Transición de propuesta no permitida. | 409 |
| `INVALID_STATE_TRANSITION` | Cambio de estado de incidente no permitido. | 409 |
| `CONFLICT` | El día ya tiene cierre. | 409 |
| `SCHEDULE_OVERLAP` | Dos turnos se solapan en el mismo sector o tanque. | 409 |
| `INVALID_SIGNATURE`, `NONCE_REPLAY`, `TIMESTAMP_OUT_OF_WINDOW` | Verificación de firma de dispositivo. | 401 |
| `DUPLICATE_READING` | El UUID del cliente ya existe con otro cuerpo. | 409 |

Formato de error de la API: `{"error":{"code":"ENGLISH_CODE","message":"Mensaje en español","details":{},"request_id":"..."}}`.

---

## M1: Login y usuarios

### HU-01: Iniciar sesión

- **Actor:** cualquier usuario con cuenta (`OPERATOR`, `BOARD_ADMIN`, `BOARD_MEMBER`, `PROJECT_TEAM`, `SUPPORT_ENTITY`).
- **Historia:** Como fontanero, quiero ingresar con mi usuario y contraseña, para registrar lecturas desde mi celular.
- **Prioridad:** must

```gherkin
Escenario: ingreso correcto
  Dado una cuenta activa con contraseña válida y must_change_password en falso
  Cuando envía POST /api/v1/auth/login con usuario y contraseña
  Entonces recibe un access token en el cuerpo de la respuesta
  Y recibe el refresh token en la cookie caudal_rt (HttpOnly, Secure, SameSite=None, Path=/api/v1/auth)
  Y GET /api/v1/auth/me devuelve su rol y su acueducto

Escenario: contraseña incorrecta
  Dado una cuenta activa
  Cuando envía una contraseña incorrecta
  Entonces recibe 401 con código INVALID_CREDENTIALS y mensaje "Usuario o contraseña incorrectos"
  Y el tiempo de respuesta es similar al de una contraseña correcta (hash señuelo)

Escenario: usuario que no existe
  Cuando envía un usuario que no existe
  Entonces recibe el mismo mensaje y código que con contraseña incorrecta
  Y no se revela si el usuario existe
```

- **Requisitos:** RF-01; RNF-01, RNF-02, RNF-05, RNF-11.
- **Patrones:** P09 (`AuthHttpClient`, decorador del cliente HTTP en el frontend); P06 (`ApiDtoAdapter`, convierte la respuesta a la vista).

### HU-02: Cambio de contraseña obligatorio en el primer ingreso

- **Actor:** cualquier usuario con una cuenta nueva o restablecida.
- **Historia:** Como usuario nuevo, quiero fijar mi contraseña con el código de activación que me dio la Junta y cambiarla al primer ingreso, para que nadie más conozca mi acceso.
- **Prioridad:** must

```gherkin
Escenario: primer ingreso fuerza el cambio
  Dado una cuenta con must_change_password en verdadero
  Cuando inicia sesión con la contraseña que fijó con el código de activación
  Entonces la aplicación muestra solo el formulario de cambio de contraseña
  Y cualquier otra llamada a la API responde 403 con código PASSWORD_CHANGE_REQUIRED

Escenario: contraseña nueva débil o que contiene el usuario
  Dado una cuenta en cambio obligatorio
  Cuando envía una contraseña de 11 caracteres
  Entonces recibe 400 con código VALIDATION_ERROR y mensaje "La contraseña debe tener entre 12 y 128 caracteres"
  Cuando envía una contraseña que aparece en la lista de comunes o que contiene su usuario
  Entonces recibe 422 con mensaje "La contraseña es demasiado común" o "La contraseña no puede contener el usuario"

Escenario: no se reutiliza una de las últimas 5 contraseñas
  Dado que la contraseña nueva coincide con una de las últimas 5 en iam.password_history
  Cuando envía el cambio
  Entonces recibe 422 con mensaje "No puede repetir una contraseña usada recientemente"
  Y la contraseña actual no cambia
```

- **Requisitos:** RF-02; RNF-01, RNF-04.
- **Patrones:** P13 (comando de cambio de contraseña, clase `ChangePasswordCommand` (clase propuesta, por definir)); P09 (`AuditingCommandHandler`, registra el cambio sin guardar contraseñas).

### HU-03: Bloqueo por intentos fallidos

- **Actor:** cualquier usuario; la Junta y el equipo lo revisan en auditoría.
- **Historia:** Como administrador, quiero que las cuentas se bloqueen tras varios intentos fallidos, para que nadie adivine una contraseña.
- **Prioridad:** must

```gherkin
Escenario: bloqueo de cuenta tras cinco fallos
  Dado una cuenta activa con failed_login_count en 4
  Cuando falla el quinto intento en menos de 15 minutos desde el primero
  Entonces la cuenta pasa a LOCKED con locked_until a 15 minutos
  Y se registra ACCOUNT_LOCKED en iam.security_events con severidad MEDIUM
  Y el siguiente intento responde 401 con INVALID_CREDENTIALS, sin indicar que está bloqueada

Escenario: bloqueo escalonado
  Dado una cuenta que ya se bloqueó una vez (lockout_level 1)
  Cuando vuelve a fallar cinco veces tras el desbloqueo
  Entonces el bloqueo dura 30 minutos
  Y cada nuevo bloqueo duplica el anterior, con un máximo de 24 horas

Escenario: límite por IP
  Dado una IP con 20 intentos fallidos en 15 minutos, contra cualquier cuenta
  Cuando envía un intento más
  Entonces recibe 429 con código RATE_LIMITED
  Y no se evalúa la contraseña
```

- **Requisitos:** RF-03; RNF-03, RNF-19.
- **Patrones:** P18 (`State`, estados de cuenta `ACTIVE`, `LOCKED`, `DISABLED` (clase propuesta, por definir)); P17 (`Observer`, publica el evento de seguridad).

### HU-04: Administrar usuarios y membresías

- **Actor:** `BOARD_ADMIN`.
- **Historia:** Como presidente de la Junta, quiero crear usuarios y darles un rol con fecha de vigencia, para que cada persona vea solo lo que le corresponde.
- **Prioridad:** must

```gherkin
Escenario: crear un fontanero con membresía vigente
  Dado que el administrador está autenticado en el acueducto "vereda-ejemplo"
  Cuando crea un usuario con rol OPERATOR y valid_from hoy y valid_to en 365 días
  Entonces la cuenta queda con must_change_password en verdadero
  Y recibe un código de activación de un solo uso, que se muestra una sola vez
  Y la membresía aparece en GET /api/v1/memberships

Escenario: un miembro intenta administrar usuarios
  Dado un usuario con rol BOARD_MEMBER
  Cuando llama a POST /api/v1/users
  Entonces recibe 403 con código FORBIDDEN
  Y se registra PERMISSION_DENIED en iam.security_events

Escenario: membresía vencida
  Dado un fontanero cuya membresía tiene valid_to en el pasado
  Cuando intenta registrar una lectura con su token vigente
  Entonces recibe 403 con código FORBIDDEN

Escenario: membresía de otro acueducto
  Dado un usuario sin membresía en el acueducto "otra-vereda"
  Cuando el administrador de "vereda-ejemplo" pide PATCH /api/v1/users/{id} de esa cuenta en otro acueducto
  Entonces recibe 404 con código NOT_FOUND
```

- **Requisitos:** RF-04, RF-40; RNF-07, RNF-19.
- **Patrones:** P13 (`CommandBus` para los comandos de administración); P09 (`AuditingCommandHandler`).

### HU-05: Cerrar sesión en todos los dispositivos

- **Actor:** cualquier usuario autenticado.
- **Historia:** Como usuario, quiero cerrar mi sesión en todos mis dispositivos, para proteger mi cuenta si pierdo el celular.
- **Prioridad:** should

```gherkin
Escenario: cierre global
  Dado que la misma cuenta tiene sesiones en dos celulares
  Cuando envía POST /api/v1/auth/logout-all desde uno de ellos
  Entonces token_version se incrementa en iam.users
  Y todos los refresh tokens quedan revocados con revoked_reason LOGOUT_ALL
  Y el access token del otro celular responde 401 con código UNAUTHORIZED en su siguiente llamada

Escenario: reutilización de refresh token
  Dado un refresh token ya rotado
  Cuando alguien lo vuelve a presentar en POST /api/v1/auth/refresh
  Entonces se revoca toda la familia de tokens con revoked_reason REUSE_DETECTED
  Y se registra TOKEN_REUSE_DETECTED en iam.security_events con severidad HIGH
  Y recibe 401 con código UNAUTHORIZED
```

- **Requisitos:** RF-05; RNF-02, RNF-19.
- **Patrones:** P13 (`CommandBus`); P11 (`Proxy`, en el cliente del frontend, verifica la sesión antes de cada llamada) (clase propuesta, por definir).

### HU-06: Activar MFA (TOTP) para la Junta administradora y el equipo

- **Actor:** `BOARD_ADMIN` y `PROJECT_TEAM`.
- **Historia:** Como administrador, quiero activar un código de verificación en mi celular, para que una contraseña robada no baste para entrar.
- **Prioridad:** should

```gherkin
Escenario: activación con código válido
  Dado un usuario sin MFA activo
  Cuando inicia POST /api/v1/auth/mfa/enroll y confirma con un código TOTP válido
  Entonces se guarda el secreto cifrado con AES-256-GCM
  Y se muestran 10 códigos de respaldo una sola vez, guardados como hash

Escenario: código incorrecto
  Dado un usuario con MFA en proceso de activación
  Cuando envía un código TOTP incorrecto
  Entonces recibe 401 con código UNAUTHORIZED y mensaje "El código no es válido"
  Y se registra MFA_FAILED en iam.security_events

Escenario: código repetido
  Dado un código TOTP ya usado en el último paso de tiempo
  Cuando se envía de nuevo, aunque sea válido
  Entonces se rechaza (anti-repetición del paso)
```

- **Requisitos:** RF-06; RNF-01, RNF-02, RNF-09.
- **Patrones:** P06 (`Adapter`, clase `TotpVerifierAdapter` (clase propuesta, por definir)).

---

## M2: Reglas de la Junta versionadas

### HU-07: Crear un borrador de reglas

- **Actor:** `BOARD_MEMBER` y `BOARD_ADMIN` (crean y editan borradores; solo `BOARD_ADMIN` activa).
- **Historia:** Como miembro de la Junta, quiero crear un borrador con un cambio en las reglas de riego, para que la Junta lo revise antes de que entre en vigor.
- **Prioridad:** must

```gherkin
Escenario: crear borrador copiando la versión vigente
  Dado una regla vigente con las bandas HIGH, LOW y CRITICAL
  Cuando el miembro envía POST /api/v1/rule-sets
  Entonces se crea un borrador con status DRAFT copiado de la vigente (RuleSet.copyForNewVersion())
  Y la vigente no cambia

Escenario: bandas que se solapan
  Dado un borrador con una banda de 1,5 a 3,5 y otra de 3,0 a 5,0
  Cuando se guarda con PATCH /api/v1/rule-sets/{id}
  Entonces recibe 400 con código VALIDATION_ERROR y mensaje "Las bandas de nivel no pueden solaparse"
  Y el borrador no se modifica

Escenario: bandas que no cubren el rango
  Dado un borrador de regla 0 a 5 con bandas que terminan en 4,5
  Cuando se guarda
  Entonces recibe 422 con mensaje "Las bandas deben cubrir todo el rango de la regla"

Escenario: editar una regla activa
  Dado una regla con status ACTIVE
  Cuando se envía PATCH /api/v1/rule-sets/{id}
  Entonces recibe 409 con código RULE_SET_IMMUTABLE
```

- **Requisitos:** RF-07; RNF-04, RNF-07, RNF-22.
- **Patrones:** P05 (`Prototype`, `RuleSet.copyForNewVersion()`).

### HU-08: Activar una versión de reglas con motivo

- **Actor:** `BOARD_ADMIN`.
- **Historia:** Como presidente de la Junta, quiero activar una versión de reglas con un motivo escrito, para que siempre se sepa qué reglas estaban vigentes y por qué cambiaron.
- **Prioridad:** must

```gherkin
Escenario: activación con motivo
  Dado un borrador válido y una regla activa actual
  Cuando el administrador envía POST /api/v1/rule-sets/{id}/activate con un motivo de 10 a 500 caracteres
  Entonces el borrador pasa a ACTIVE y la anterior pasa a SUPERSEDED
  Y se registra RULESET_ACTIVATED en audit.audit_log con el motivo
  Y GET /api/v1/rule-sets/current devuelve la nueva versión

Escenario: activación sin motivo
  Cuando envía el activate sin motivo o con 9 caracteres
  Entonces recibe 422 con código REASON_REQUIRED
  Y la regla no cambia

Escenario: una regla activa es inmutable
  Dado una regla con status ACTIVE
  Cuando se intenta modificar sus bandas
  Entonces recibe 409 con código RULE_SET_IMMUTABLE
```

- **Requisitos:** RF-08; RNF-19, RNF-22.
- **Patrones:** P13 (`ActivateRuleSetCommand` con `CommandBus`); P17 (`Observer`, evento `ReglasActivadas`).

---

## M3: Lecturas offline con validación y correcciones

### HU-09: Registrar una lectura sin señal

- **Actor:** `OPERATOR` (fontanero).
- **Historia:** Como fontanero, quiero anotar el nivel del tanque en el celular aunque no haya señal, para no volver al cuaderno de papel.
- **Prioridad:** must

```gherkin
Escenario: guardado sin conexión
  Dado que el celular está sin señal
  Cuando el fontanero registra la regla 3,2, agua normal y sin daño
  Entonces la lectura se guarda en IndexedDB con un UUID generado en el cliente
  Y la pantalla muestra "Guardado en el celular. Se enviará cuando haya señal"
  Y la cola no guarda contraseña ni token

Escenario: reenvío sin duplicar
  Dado una lectura en cola con UUID X
  Cuando el celular recupera la señal y envía POST /api/v1/readings con UUID X dos veces
  Entonces la primera responde 201 y crea la lectura
  Y la segunda responde 200 con la misma lectura, sin crear otra fila

Escenario: formato inválido en el formulario
  Dado el rango del tanque de 0 a 5 cargado desde la caché de reglas
  Cuando el fontanero escribe 3,256 (tres decimales)
  Entonces el formulario muestra "Use como máximo dos decimales" y no permite guardar
```

- **Requisitos:** RF-09, RF-41; RNF-04, RNF-12, RNF-14.
- **Patrones:** P13 (`SubmitReadingCommand` en la cola offline); P01 (`Singleton`, `OfflineDatabase`); P17 (`Observer`, `SyncQueueStore`).

### HU-10: Sincronizar lecturas pendientes por lotes

- **Actor:** `OPERATOR`.
- **Historia:** Como fontanero, quiero que las lecturas guardadas se envíen solas al volver la señal, para no tener que reenviarlas una por una.
- **Prioridad:** must

```gherkin
Escenario: sincronización automática
  Dado 30 lecturas en cola
  Cuando ConnectivityMonitor detecta la conexión
  Entonces la app envía POST /api/v1/readings/batch con esas 30 lecturas
  Y la pantalla pasa de "Sin conexión" a "Sincronizando" y luego a "En línea"

Escenario: lote mayor al límite
  Dado un lote de 101 lecturas
  Cuando se envía POST /api/v1/readings/batch
  Entonces recibe 400 con código VALIDATION_ERROR y details.max = 100

Escenario: fallo parcial
  Dado un lote con 30 lecturas, una de ellas con valor 8
  Cuando el servidor procesa el lote
  Entonces responde el resultado de cada lectura por separado
  Y las 29 válidas quedan ACCEPTED o FLAGGED, y la de valor 8 responde 422 con código GAUGE_OUT_OF_RANGE sin guardarse
  Y la app conserva en la cola solo la lectura rechazada, con su motivo, hasta que el fontanero la corrige o la descarta
```

- **Requisitos:** RF-10; RNF-04, RNF-14.
- **Patrones:** P17 (`Observer`, `ConnectivityMonitor`); P18 (`State`, `ConnectionState`: En línea, Sin conexión, Sincronizando); P13 (`CommandBus` de la cola).

### HU-11: Rechazar un valor fuera de rango y pedir corrección

- **Actor:** `OPERATOR`.
- **Historia:** Como fontanero, quiero que la app me avise si anoté un número imposible, para corregirlo antes de que la Junta lo use.
- **Prioridad:** must

```gherkin
Escenario: valor fuera de rango
  Dado una regla de 0 a 5
  Cuando se envía una lectura con valor 8
  Entonces la API responde 422 con código GAUGE_OUT_OF_RANGE y la lectura no se guarda
  Y el mensaje es "El valor 8 está fuera del rango 0 a 5. Revise la regla pintada"
  Y la app pide corregir el valor antes de reenviarla

Escenario: valor dentro de rango
  Cuando se envía una lectura con valor 4,5
  Entonces la lectura queda ACCEPTED

Escenario: sin fecha
  Cuando se envía una lectura sin timestamp
  Entonces recibe 422 con código MISSING_TIMESTAMP y no se guarda nada
```

- **Requisitos:** RF-11, RF-41; RNF-04, RNF-22.
- **Patrones:** P12 (`Chain of Responsibility`, `ReadingValidationHandler`, primer eslabón: rango).

### HU-12: Detectar duplicados y saltos bruscos

- **Actor:** `OPERATOR` (recibe el aviso) y `BOARD_ADMIN` (revisa el registro).
- **Historia:** Como administrador, quiero que el sistema marque lecturas repetidas o con saltos imposibles, para no tomar decisiones con datos dudosos.
- **Prioridad:** should

```gherkin
Escenario: duplicado dentro de la ventana
  Dado una lectura de 2,1 en el tanque a las 10:00
  Cuando llega otra de 2,1 a las 10:20 y la ventana de duplicados es de 60 minutos
  Entonces la segunda queda FLAGGED con issue_code DUPLICATE
  Y no se borra ni se oculta del historial

Escenario: salto brusco aceptado con marca
  Dado un salto por hora mayor al configurado en la regla
  Cuando llega la lectura
  Entonces queda FLAGGED con issue_code SUDDEN_JUMP y severity WARNING
  Y se acepta para el cálculo, marcada

Escenario: el aviso no reemplaza la corrección
  Dado una lectura FLAGGED por DUPLICATE
  Cuando el fontanero confirma que es correcta
  Entonces sigue visible con su marca original
```

- **Requisitos:** RF-12, RF-11; RNF-04, RNF-19.
- **Patrones:** P12 (`ReadingValidationHandler`, eslabones de duplicado y salto brusco).

### HU-13: Corregir una lectura con motivo

- **Actor:** `OPERATOR` (solo lecturas propias) y `BOARD_MEMBER` / `BOARD_ADMIN` (cualquier lectura del acueducto).
- **Historia:** Como fontanero, quiero corregir una lectura que anoté mal, para que el registro quede bien sin perder lo que escribí antes.
- **Prioridad:** must

```gherkin
Escenario: corrección con motivo
  Dado una lectura propia guardada con valor 2,10 en estado ACCEPTED
  Cuando el fontanero envía POST /api/v1/readings/{id}/corrections con valor 2,00 y motivo "Se leyó mal la regla"
  Entonces se crea una fila en ops.reading_corrections que apunta a la original
  Y la lectura original no cambia: sigue valiendo 2,10 en la base de datos
  Y el valor efectivo pasa a 2,00

Escenario: motivo muy corto
  Cuando envía un motivo de 9 caracteres
  Entonces recibe 422 con código REASON_REQUIRED y mensaje "Escriba un motivo de al menos 10 caracteres"

Escenario: corregir una lectura de otro fontanero
  Dado una lectura creada por otro OPERATOR
  Cuando intenta corregirla
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-13; RNF-07, RNF-19.
- **Patrones:** P09 (`AuditingCommandHandler`); P13 (`CommandBus`).

---

## M4: Estado del tanque y pronóstico

### HU-14: Ver el estado del tanque con la antigüedad del dato

- **Actor:** `OPERATOR`, `BOARD_ADMIN`, `BOARD_MEMBER`, `PROJECT_TEAM`.
- **Historia:** Como miembro de la Junta, quiero ver el último nivel, cuánto tiempo hace que se tomó y si viene bajando, para decidir con información actual.
- **Prioridad:** must

```gherkin
Escenario: estado normal
  Dado una última lectura aceptada de 2,1 hace 5 horas
  Y la tendencia de las últimas lecturas está bajo el umbral de la regla
  Cuando se consulta GET /api/v1/tanks/{id}/status
  Entonces la pantalla muestra "2,1 · hace 5 horas · bajando"
  Y la banda es LOW si 2,1 cae en el rango bajo de la regla vigente

Escenario: dato viejo
  Dado una última lectura cuya antigüedad supera el máximo de la regla
  Cuando se consulta el estado
  Entonces la respuesta incluye stale en verdadero
  Y la pantalla muestra el aviso "El dato tiene más de X horas. Confirme el nivel" con X tomado de la regla

Escenario: sin lecturas
  Dado un tanque sin lecturas aceptadas
  Cuando se consulta el estado
  Entonces la pantalla muestra "Todavía no hay lecturas" y no muestra un nivel inventado
```

- **Requisitos:** RF-14; RNF-12, RNF-13, RNF-25.
- **Patrones:** P10 (`Facade`, `TankStatusFacade` en la API y `CaudalApi` en el frontend); P18 (`State`, `TankLevelState`: Alto, Bajo, Crítico); P19 (`Strategy`, `TrendStrategy`).

### HU-15: Ver el pronóstico con rango

- **Actor:** `OPERATOR`, `BOARD_ADMIN`, `BOARD_MEMBER`, `PROJECT_TEAM`.
- **Historia:** Como miembro de la Junta, quiero ver el nivel probable para los próximos días con un rango, para saber qué tan seguro es el pronóstico.
- **Prioridad:** must

```gherkin
Escenario: pronóstico con rango
  Dado un pronóstico de 2 días con p10 1,8, p50 2,1 y p90 2,4 para mañana
  Cuando se abre la pantalla de estado
  Entonces se muestra "Lo más probable: 2,1 (podría estar entre 1,8 y 2,4)"
  Y el gráfico muestra la banda entre p10 y p90 (Recharts)

Escenario: horizonte fuera de 1 a 3 días
  Dado una solicitud con horizonte 5
  Cuando se consulta POST /api/v1/tanks/{id}/forecasts
  Entonces recibe 400 con código VALIDATION_ERROR y details.max = 3

Escenario: sin pronóstico vigente
  Dado un tanque sin pronóstico reciente
  Cuando se abre la pantalla
  Entonces se muestra "Pronóstico no disponible" y el último estado sin cambios
```

- **Requisitos:** RF-15; RNF-10, RNF-13, RNF-25.
- **Patrones:** P06 (`Adapter`, `IaForecastClientAdapter`); P11 (`Proxy`, `RemoteForecasterProxy`, que decide entre IA y respaldo).

### HU-16: Respaldo si la IA no responde

- **Actor:** sistema (activo para todos los roles que ven el pronóstico).
- **Historia:** Como fontanero, quiero que la app siga funcionando aunque la IA esté caída, para no quedarme sin información en el campo.
- **Prioridad:** must

```gherkin
Escenario: timeout de la IA
  Dado que el servicio de IA no responde en 10 segundos
  Cuando se pide un pronóstico
  Entonces el backend guarda una estimación de persistencia con fallback_reason IA_TIMEOUT
  Y la pantalla muestra "Estimación simple: seguirá igual que hoy" con el rango empírico de cambios diarios
  Y la respuesta es 200, no un error

Escenario: circuito abierto
  Dado que el circuit breaker está en estado Abierto
  Cuando se pide un pronóstico
  Entonces no se llama a la IA y el motivo es CIRCUIT_OPEN

Escenario: historial insuficiente
  Dado un tanque con menos puntos que el mínimo configurado
  Cuando el servicio de IA responde 422 INSUFFICIENT_HISTORY
  Entonces el backend guarda la estimación simple con fallback_reason INSUFFICIENT_HISTORY
  Y guarda ambos pronósticos para la evaluación (HU-33)
```

- **Requisitos:** RF-16; RNF-10, RNF-16.
- **Patrones:** P11 (`RemoteForecasterProxy`, remoto con respaldo); P18 (`State`, `CircuitBreakerState`: Cerrado, Abierto, Semiabierto); P19 (`Strategy`, `Forecaster`, con la estimación simple como estrategia alterna).

### HU-17: Revisar las anomalías

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero confirmar o descartar las sospechas del sistema (fuga, sensor fallando), para que las decisiones no se basen en sospechas no revisadas.
- **Prioridad:** should

```gherkin
Escenario: confirmar una sospecha de fuga
  Dado una anomalía POSSIBLE_LEAK con status OPEN
  Cuando la Junta envía PATCH /api/v1/anomalies/{id} con status CONFIRMED y un motivo
  Entonces la anomalía queda CONFIRMED
  Y el estatus epistémico sigue siendo INFERRED hasta que se verifique en campo

Escenario: descartar con motivo
  Cuando la Junta envía status DISMISSED sin motivo
  Entonces recibe 422 con código REASON_REQUIRED

Escenario: el fontanero no puede revisar anomalías
  Dado un usuario con rol OPERATOR
  Cuando envía PATCH /api/v1/anomalies/{id}
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-17; RNF-19.
- **Patrones:** P17 (`Observer`, `DomainEventPublisher`); P13 (`CommandBus`).

---

## M5: Propuesta de turnos explicada

### HU-18: Generar una propuesta de turnos explicada

- **Actor:** `BOARD_MEMBER`, `BOARD_ADMIN`.
- **Historia:** Como miembro de la Junta, quiero que el sistema proponga los turnos de agua con una explicación de cada decisión, para revisarla en la reunión sin tener que calcularla a mano.
- **Prioridad:** must

```gherkin
Escenario: propuesta con explicaciones
  Dado un tanque en banda LOW (8 horas diarias) y el sector Alto sin servicio hace 38 horas
  Cuando se envía POST /api/v1/schedule-proposals
  Entonces la propuesta queda en PENDING_REVIEW
  Y cada turno tiene reason_code y parámetros, por ejemplo HOURS_WITHOUT_SERVICE con horas 38
  Y la explicación se muestra como "Sector Alto lleva 38 horas sin servicio"

Escenario: sectores prioritarios primero
  Dado un sector marcado como prioritario (la escuela)
  Cuando la banda es CRITICAL, con 3 horas diarias de servicio
  Entonces el turno del sector prioritario aparece primero con reason_code PRIORITY_SECTOR
  Y los demás sectores no reciben turno en esa banda

Escenario: sin horas disponibles
  Dado un tanque cuyo p10 está bajo la reserva
  Cuando se genera la propuesta
  Entonces se aplica `reserve_policy` de la regla (por defecto `REDUCE_TO_CRITICAL_BAND`) y el turno incluye RESERVE_GUARD
```

- **Requisitos:** RF-18; RNF-18, RNF-22, RNF-25.
- **Patrones:** P04 (`Builder`, `ScheduleProposalBuilder`); P14 (`Iterator`, `SectorTurnIterator`: prioritarios y luego mayor espera); P19 (`Strategy`, `TurnAllocationStrategy`); P22 (`Interpreter`, plantillas como `{sector} lleva {horas} horas sin servicio`).

### HU-19: Respetar reserva, horas máximas y ventana de operación

- **Actor:** sistema (efecto visible para `BOARD_MEMBER` y `BOARD_ADMIN`).
- **Historia:** Como miembro de la Junta, quiero que la propuesta respete las reglas de la Junta, para no tener que corregir turnos imposibles.
- **Prioridad:** must

```gherkin
Escenario: turno más largo que el máximo
  Dado max_shift_hours de 4 y una asignación de 6 horas para un sector
  Cuando se calcula la propuesta
  Entonces el sector recibe dos turnos consecutivos que suman 6 horas, cada uno de 4 horas o menos

Escenario: horas totales por encima del máximo diario
  Dado que el máximo por día es 24 y la banda permite 30 horas
  Cuando se calcula la propuesta
  Entonces el total queda recortado a 24 horas

Escenario: turnos solapados
  Dado un turno ya publicado de 06:00 a 10:00 en el mismo tanque
  Cuando se intenta insertar un turno de 09:00 a 11:00
  Entonces la base de datos rechaza la inserción por la restricción EXCLUDE con código SCHEDULE_OVERLAP
```

- **Requisitos:** RF-19; RNF-22, RNF-26.
- **Patrones:** P19 (`Strategy`, reparto equitativo como estrategia alterna `EQUAL_SPLIT`); P14 (`Iterator`, `SectorTurnIterator`).

---

## M6: Decisión de la Junta

### HU-20: Aprobar la propuesta tal cual

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero aprobar la propuesta sin cambios, para publicarla rápido cuando estoy de acuerdo.
- **Prioridad:** must

```gherkin
Escenario: aprobación sin cambios
  Dado una propuesta en PENDING_REVIEW
  Cuando la Junta envía POST /api/v1/schedule-proposals/{id}/approve
  Entonces la propuesta pasa a APPROVED
  Y se crea una fila en ops.proposal_decisions con decision APPROVED y el usuario que decide
  Y la propuesta queda lista para publicar

Escenario: el fontanero intenta aprobar
  Dado un usuario con rol OPERATOR
  Cuando envía el approve
  Entonces recibe 403 con código FORBIDDEN

Escenario: aprobar una propuesta ya publicada
  Dado una propuesta en PUBLISHED
  Cuando se envía approve
  Entonces recibe 409 con código INVALID_STATE_TRANSITION
```

- **Requisitos:** RF-20; RNF-07, RNF-19.
- **Patrones:** P13 (`ApproveProposalCommand` con `CommandBus`); P18 (`State`, `ProposalState`).

### HU-21: Modificar la propuesta con motivo

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero cambiar los turnos cuando la propuesta no refleja lo que sabemos, y escribir por qué, para que la decisión quede explicada.
- **Prioridad:** must

```gherkin
Escenario: modificación con motivo
  Dado una propuesta en PENDING_REVIEW
  Cuando la Junta cambia el turno del sector Bajo de 4 a 6 horas y escribe el motivo "El sector Bajo tiene una familia de 6 personas"
  Entonces la propuesta pasa a APPROVED_WITH_CHANGES
  Y la versión original queda guardada en ops.proposal_snapshots

Escenario: motivo obligatorio y de longitud
  Cuando envía el modify con un motivo de 9 caracteres
  Entonces recibe 422 con código REASON_REQUIRED

Escenario: el total de horas no cuadra con la banda
  Dado que la suma de horas modificadas supera las horas disponibles de la banda
  Cuando la Junta intenta guardar
  Entonces ProposalEditorMediator muestra "Las horas superan las disponibles (16). Ajuste los turnos" y no se guarda
```

- **Requisitos:** RF-21; RNF-18, RNF-19.
- **Patrones:** P16 (`Memento`, `ProposalMemento`, guarda la propuesta original); P15 (`Mediator`, `ProposalEditorMediator` en el frontend); P13 (`ModifyProposalCommand`).

### HU-22: Rechazar la propuesta con motivo

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero rechazar una propuesta que no sirve y dejar escrito por qué, para pedir una nueva.
- **Prioridad:** should

```gherkin
Escenario: rechazo con motivo
  Dado una propuesta en PENDING_REVIEW
  Cuando la Junta envía reject con motivo de 10 o más caracteres
  Entonces la propuesta pasa a REJECTED y no se puede publicar
  Y se puede generar una nueva propuesta, que crea otro registro

Escenario: rechazo sin motivo
  Cuando envía reject sin motivo
  Entonces recibe 422 con código REASON_REQUIRED
```

- **Requisitos:** RF-22; RNF-19.
- **Patrones:** P13 (`RejectProposalCommand`); P18 (`State`, `ProposalState`).

---

## M7: Publicación y reportes de daño

### HU-23: Publicar el horario aprobado

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero publicar el horario aprobado, para que las familias lo vean.
- **Prioridad:** must

```gherkin
Escenario: publicación de una propuesta aprobada
  Dado una propuesta APPROVED
  Cuando la Junta envía POST /api/v1/schedule-proposals/{id}/publish
  Entonces la propuesta pasa a PUBLISHED y se crea una fila en ops.publications
  Y se publica el evento HorarioPublicado
  Y se generan comandos de válvula con not_before y expires_at solo para los turnos aprobados

Escenario: publicar sin aprobación
  Dado una propuesta en PENDING_REVIEW
  Cuando se intenta publicar
  Entonces recibe 409 con código INVALID_STATE_TRANSITION
  Y no se crea ninguna publicación

Escenario: publicar dos veces
  Dado una propuesta ya PUBLISHED
  Cuando se vuelve a publicar
  Entonces recibe 409 con código INVALID_STATE_TRANSITION
```

- **Requisitos:** RF-23; RNF-08, RNF-19.
- **Patrones:** P13 (`PublishScheduleCommand`); P17 (`Observer`, `DomainEventPublisher`); P08 (`Composite`, `SectorNode` y `ValveNode` para expandir el horario por válvula).

### HU-24: Página pública sin datos personales

- **Actor:** Familias (público, sin login).
- **Historia:** Como vecino, quiero ver en el celular el horario de mi sector sin registrarme, para saber cuándo llega el agua.
- **Prioridad:** must

```gherkin
Escenario: horario publicado
  Dado un horario PUBLISHED para el acueducto con slug "guaitarilla-veredas"
  Cuando alguien abre GET /api/v1/public/guaitarilla-veredas/schedule sin login
  Entonces ve sectores, horas y la fecha de publicación
  Y la respuesta no contiene nombres, teléfonos ni IDs de usuarios

Escenario: sin horario publicado
  Dado un acueducto sin publicaciones
  Cuando se abre la página pública
  Entonces se muestra "Todavía no hay horario publicado para esta semana"

Escenario: acueducto que no existe
  Cuando se consulta un slug inexistente
  Entonces recibe 404 con código NOT_FOUND
```

- **Requisitos:** RF-24; RNF-06, RNF-11, RNF-13, RNF-20.
- **Patrones:** P11 (`Proxy`, `PublicScheduleProxy`, quita datos personales); P07 (`Bridge`, `Notice` por `PublicationChannel`).

### HU-25: Copiar el mensaje para WhatsApp

- **Actor:** Familias (público) y `BOARD_ADMIN`, `BOARD_MEMBER` (lo comparten en el grupo de la vereda).
- **Historia:** Como miembro de la Junta, quiero copiar un mensaje listo con el horario para enviarlo al grupo de WhatsApp, para llegar a quienes no abren la página.
- **Prioridad:** should

```gherkin
Escenario: texto generado
  Dado un horario publicado con el sector "Alto" de 06:00 a 10:00
  Cuando se consulta GET /api/v1/public/{aqueductSlug}/schedule/whatsapp-text
  Entonces el texto dice "Sector Alto: agua de 6:00 a 10:00"
  Y no contiene teléfonos ni nombres de personas

Escenario: botón de copiar
  Cuando la persona toca "Copiar mensaje"
  Entonces el texto queda en el portapapeles y el botón muestra "Copiado"

Escenario: el navegador no permite copiar
  Dado un navegador que bloquea el portapapeles
  Cuando toca el botón
  Entonces el texto queda seleccionado y se muestra "Mantenga presionado el texto y copie"
```

- **Requisitos:** RF-25; RNF-12, RNF-21.
- **Patrones:** P22 (`Interpreter`, plantillas con plurales y coma decimal); P07 (`Bridge`, `PublicationChannel` para WhatsApp).

### HU-26: Descargar el cartel en PDF

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER` (lo imprimen y lo ponen en un lugar de paso).
- **Historia:** Como miembro de la Junta, quiero imprimir un cartel con el horario, para ponerlo donde la gente lo vea aunque no tenga celular.
- **Prioridad:** could

```gherkin
Escenario: cartel con el horario vigente
  Dado un horario PUBLISHED
  Cuando se descarga GET /api/v1/public/{aqueductSlug}/schedule/poster.pdf
  Entonces el PDF muestra la fecha de publicación, los sectores y las horas
  Y no muestra nombres ni teléfonos

Escenario: cartel de acueducto demo
  Dado un acueducto con is_demo en verdadero
  Cuando se genera el cartel
  Entonces el PDF incluye la marca "Datos simulados"

Escenario: sin horario publicado
  Cuando no hay publicación
  Entonces recibe 404 con código NOT_FOUND y mensaje "No hay horario publicado para imprimir"
```

- **Requisitos:** RF-26; RNF-20, RNF-21.
- **Patrones:** P03 (`Abstract Factory`, `DocumentFactory`, familia PDF y HTML); P02 (`Factory Method`, `DocumentGeneratorCreator`); P20 (`Template Method`, `DocumentGenerator`: encabezado, secciones y firmas).

### HU-27: Reportar un daño con código de seguimiento

- **Actor:** Familias (público, sin login).
- **Historia:** Como vecino, quiero reportar una fuga o agua sucia desde el celular y tener un código para seguirlo, para que la Junta sepa del problema sin que yo deje mi nombre.
- **Prioridad:** must

```gherkin
Escenario: reporte válido
  Dado una categoría del catálogo DAMAGE_CATEGORY (código LEAK, de la semilla)
  Cuando se envía POST /api/v1/public/{aqueductSlug}/damage-reports con descripción de 10 a 500 caracteres
  Entonces recibe 201 con un código de seguimiento de 8 caracteres Crockford base32
  Y el código se muestra una sola vez; en la base de datos solo se guarda su SHA-256
  Y el incidente queda REPORTED

Escenario: campo oculto llenado (honeypot)
  Dado un formulario en el que un bot llenó el campo oculto
  Cuando envía el reporte
  Entonces no se guarda nada, se registra HONEYPOT_TRIGGERED y responde 201 genérico

Escenario: exceso de reportes
  Dado una IP con 3 reportes en la última hora
  Cuando envía un cuarto
  Entonces recibe 429 con código RATE_LIMITED

Escenario: seguimiento con código
  Cuando consulta GET /api/v1/public/damage-reports/{trackingCode} con el código correcto
  Entonces ve su estado (REPORTED, VERIFYING, CONFIRMED, RESOLVED o DISMISSED)
  Y no ve IP, nombre ni teléfono
```

- **Requisitos:** RF-27; RNF-03, RNF-04, RNF-20.
- **Patrones:** P13 (`CommandBus`, con clave de idempotencia por el envío); P18 (`State`, estados del reporte).

### HU-28: Gestionar el estado de un incidente

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero cambiar el estado de un reporte de daño a medida que se verifica y se arregla, para que la comunidad sepa qué pasa.
- **Prioridad:** should

```gherkin
Escenario: transición válida
  Dado un incidente en REPORTED
  Cuando la Junta cambia el estado a VERIFYING con la nota "Visita técnica el jueves"
  Entonces se agrega una fila a ops.incident_status_history con from_status REPORTED y to_status VERIFYING
  Y el seguimiento público muestra "En verificación"

Escenario: transición no permitida
  Dado un incidente en REPORTED
  Cuando la Junta intenta pasar directo a RESOLVED
  Entonces recibe 409 con código INVALID_STATE_TRANSITION

Escenario: el fontanero no cambia estados
  Dado un usuario con rol OPERATOR
  Cuando envía PATCH /api/v1/incidents/{id}/status
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-28; RNF-19.
- **Patrones:** P18 (`State`, transiciones de incidente); P17 (`Observer`, `DomainEventPublisher`).

---

## M8: Cierre del día

### HU-29: Registrar la ejecución de los turnos y el cierre del día

- **Actor:** `OPERATOR`, `BOARD_ADMIN`.
- **Historia:** Como fontanero, quiero anotar al final del día qué turnos se cumplieron, cuáles a medias y si hubo novedades, para que la siguiente propuesta parta de lo que pasó de verdad.
- **Prioridad:** must

```gherkin
Escenario: cierre completo
  Dado un día con tres turnos publicados
  Cuando el fontanero marca dos como COMPLETED y uno como PARTIAL con la nota "Se cortó el agua a las 9"
  Y envía POST /api/v1/days/{date}/closure
  Entonces el día queda cerrado con sus ejecuciones registradas
  Y las horas sin servicio se recalculan desde el fin del último turno confirmado

Escenario: segundo cierre del mismo día
  Dado un día ya cerrado
  Cuando se envía otro cierre
  Entonces recibe 409 con código CONFLICT

Escenario: nota demasiado larga
  Cuando la nota tiene 11 líneas o más de 500 caracteres
  Entonces recibe 400 con código VALIDATION_ERROR
```

- **Requisitos:** RF-29; RNF-19, RNF-22.
- **Patrones:** P13 (`CloseDayCommand` en la cola offline); P18 (`State`, la propuesta pasa a `CLOSED`); P17 (`Observer`).

---

## M9: Actas y resúmenes para entidades

### HU-30: Generar un acta con cuatro secciones

- **Actor:** `BOARD_ADMIN`, `BOARD_MEMBER`.
- **Historia:** Como miembro de la Junta, quiero generar un acta del periodo que separe lo observado, lo estimado, lo inferido y lo confirmado, para que nadie confunda una sospecha con un hecho.
- **Prioridad:** should

```gherkin
Escenario: acta con cuatro secciones
  Dado un periodo del 1 al 31 de octubre con lecturas, pronósticos, anomalías y ejecuciones
  Cuando la Junta envía POST /api/v1/minutes con period_from y period_to
  Entonces el acta queda en DRAFT con las secciones observado, estimado, inferido y confirmado
  Y cada registro aparece solo en la sección de su estatus epistémico

Escenario: acta final inmutable
  Dado un acta en estado FINAL con pdf_sha256 guardado
  Cuando se intenta regenerar o modificar
  Entonces recibe 409 y se conserva el PDF original

Escenario: el fontanero no genera actas
  Dado un usuario con rol OPERATOR
  Cuando envía POST /api/v1/minutes
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-30; RNF-19, RNF-22.
- **Patrones:** P20 (`Template Method`, `DocumentGenerator`: encabezado, secciones, firmas); P21 (`Visitor`, `MinutesSectionVisitor`, clasifica registros por estatus); P04 (`Builder`, `MinutesBuilder`).

### HU-31: Autorizar un resumen a una entidad, con vigencia

- **Actor:** `BOARD_ADMIN`.
- **Historia:** Como presidente de la Junta, quiero autorizar a una entidad de apoyo a ver un resumen por un tiempo definido, para compartir información sin dar acceso a todo el sistema.
- **Prioridad:** should

```gherkin
Escenario: autorización con vigencia
  Dado un usuario con rol SUPPORT_ENTITY
  Cuando el administrador crea una autorización MONTHLY_SUMMARY con expires_at en 90 días y un motivo de 10 o más caracteres
  Entonces se crea la fila en reporting.summary_shares con authorized_by y authorized_at
  Y la autorización aparece en GET /api/v1/summaries para esa entidad

Escenario: vigencia obligatoria
  Cuando se envía la autorización sin expires_at
  Entonces recibe 400 con código VALIDATION_ERROR

Escenario: revocación
  Dado una autorización vigente
  Cuando el administrador envía DELETE /api/v1/summary-shares/{id} con motivo
  Entonces se llena revoked_at y la entidad deja de verla de inmediato
```

- **Requisitos:** RF-31; RNF-20, RNF-19.
- **Patrones:** P13 (`CommandBus`); P09 (`AuditingCommandHandler`).

### HU-32: Ver solo los resúmenes autorizados y vigentes

- **Actor:** `SUPPORT_ENTITY`.
- **Historia:** Como representante de una entidad de apoyo, quiero ver los resúmenes que la Junta me autorizó, para apoyar con información oficial del acueducto.
- **Prioridad:** should

```gherkin
Escenario: autorización vigente
  Dado una autorización de resumen mensual con expires_at en el futuro
  Cuando la entidad consulta GET /api/v1/summaries
  Entonces ve el resumen del periodo autorizado
  Y cada consulta queda registrada en audit.data_access_log

Escenario: autorización vencida
  Dado una autorización con expires_at en el pasado
  Cuando la entidad consulta la lista
  Entonces no aparece nada y el acceso directo a su acta responde 404 con código NOT_FOUND

Escenario: intento de ver datos operativos
  Dado una entidad autenticada
  Cuando pide GET /api/v1/readings
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-32, RF-40; RNF-07, RNF-20.
- **Patrones:** P09 (`AuditingCommandHandler`, registra accesos); RLS y repositorio (arquitectónico, ver [Actores-y-permisos.md](Actores-y-permisos.md) sección 4).

---

## M10: Evaluación IA vs estimación simple

### HU-33: Ver si la IA ayuda de verdad

- **Actor:** `PROJECT_TEAM`, `BOARD_ADMIN`.
- **Historia:** Como equipo del proyecto, quiero comparar el pronóstico de la IA con la estimación simple, para saber si la IA mejora algo antes de presumirla.
- **Prioridad:** should

```gherkin
Escenario: evaluación con muestra suficiente
  Dado 45 pronósticos evaluados con IA y con estimación simple
  Cuando se consulta GET /api/v1/forecast-evaluation
  Entonces la respuesta muestra MAE del p50, WQL, cobertura de [p10, p90] y skill para los horizontes 1, 2 y 3 días
  Y skill = 1 − MAE_IA / MAE_simple por horizonte

Escenario: muestra insuficiente
  Dado 12 pronósticos evaluados
  Cuando se consulta la evaluación
  Entonces se muestra "Muestra insuficiente: 12 de 30 pronósticos"
  Y no se muestra un veredicto sobre la IA

Escenario: cobertura fuera del rango
  Dado una cobertura de 65 % con 40 pronósticos
  Cuando se consulta
  Entonces el veredicto es "La IA no cumple el criterio de cobertura (70 % a 90 %)"

Escenario: skill no calculable
  Dado un MAE de la estimación simple igual a 0
  Cuando se calcula el skill
  Entonces el valor se muestra como "No calculable" y no como un número
```

- **Requisitos:** RF-33; RNF-18, RNF-25.
- **Patrones:** P20 (`Template Method`, `Backtest`); P14 (`Iterator`, `RollingWindowIterator` en el servicio de IA para recorrer ventanas); P19 (`Strategy`, `Forecaster`).

---

## M11: Dispositivos

### HU-34: Registrar un dispositivo y su clave pública

- **Actor:** `BOARD_ADMIN`, `PROJECT_TEAM`.
- **Historia:** Como equipo del proyecto, quiero registrar un sensor o una válvula con su clave pública, para que sus datos se acepten solo si vienen firmados.
- **Prioridad:** should

```gherkin
Escenario: registro con clave pública
  Dado un dispositivo tipo LEVEL_SENSOR
  Cuando se envía POST /api/v1/devices y luego POST /api/v1/devices/{id}/keys con la clave pública Ed25519
  Entonces la base de datos guarda solo la clave pública
  Y la respuesta nunca incluye una clave privada

Escenario: rotación de clave
  Dado un dispositivo con una clave activa
  Cuando se registra una clave nueva
  Entonces la anterior sigue aceptada 7 días (configurable) y después queda revocada

Escenario: identificador repetido
  Cuando se registra un dispositivo con un identificador ya existente
  Entonces recibe 409 con código CONFLICT
```

- **Requisitos:** RF-34; RNF-08, RNF-19.
- **Patrones:** P06 (`Adapter`, `DeviceTelemetryAdapter`).

### HU-35: Recibir telemetría firmada

- **Actor:** dispositivo (sistema).
- **Historia:** Como sistema, quiero aceptar telemetría solo si viene firmada y es nueva, para que nadie pueda inyectar niveles falsos ni reenviar mensajes viejos.
- **Prioridad:** should

```gherkin
Escenario: telemetría válida
  Dado un dispositivo activo con firma Ed25519 correcta, timestamp dentro de ±300 segundos y nonce nuevo
  Cuando envía POST /api/v1/devices/telemetry
  Entonces recibe 202 y los puntos se guardan en devices.telemetry_points
  Y el nonce se guarda para 10 minutos

Escenario: nonce repetido
  Dado un nonce usado hace 2 minutos
  Cuando el mismo nonce llega de nuevo
  Entonces recibe 401 con código NONCE_REPLAY y se registra en iam.security_events

Escenario: firma inválida o timestamp fuera de ventana
  Cuando la firma no valida con la clave del dispositivo
  Entonces recibe 401 con código INVALID_SIGNATURE
  Cuando el timestamp está a 301 segundos del servidor
  Entonces recibe 401 con código TIMESTAMP_OUT_OF_WINDOW

Escenario: lote demasiado grande
  Cuando envía 101 puntos en una petición
  Entonces recibe 400 con código VALIDATION_ERROR
```

- **Requisitos:** RF-35; RNF-04, RNF-08.
- **Patrones:** P06 (`Adapter`, `DeviceTelemetryAdapter`); P12 (`Chain of Responsibility`, cadena de verificación de firma, ventana, nonce y payload; clase propuesta, por definir).

### HU-36: Enviar un comando de válvula desde un turno aprobado

- **Actor:** sistema (el comando sale del turno publicado). `BOARD_ADMIN` para comandos manuales.
- **Historia:** Como miembro de la Junta, quiero que las válvulas solo se muevan según un turno aprobado (o por una orden manual con motivo), para que nadie abra una válvula por error.
- **Prioridad:** should

```gherkin
Escenario: comando desde turno aprobado
  Dado un turno publicado de 06:00 a 10:00 en la válvula V-01
  Cuando llega la hora de not_before
  Entonces el dispositivo recibe el comando OPEN en GET /api/v1/devices/commands
  Y al confirmar con POST /api/v1/devices/commands/{id}/ack el estado pasa a ACKED

Escenario: comando vencido
  Dado un comando con expires_at ya pasado
  Cuando el dispositivo lo pide
  Entonces el comando queda EXPIRED y no se ejecuta

Escenario: comando manual sin motivo
  Dado un administrador
  Cuando envía POST /api/v1/valve-commands/manual sin motivo
  Entonces recibe 422 con código REASON_REQUIRED

Escenario: comando manual por un fontanero
  Dado un usuario con rol OPERATOR
  Cuando envía POST /api/v1/valve-commands/manual
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-36; RNF-08, RNF-19.
- **Patrones:** P13 (`CommandBus`, `OpenValveCommand` y `CloseValveCommand` en el simulador); P18 (`State`, `ValveState`); P06 (`Adapter`, `DeviceTelemetryAdapter`).

---

## M12: Auditoría

### HU-37: Consultar la bitácora y los eventos de seguridad

- **Actor:** `BOARD_ADMIN`, `PROJECT_TEAM`.
- **Historia:** Como equipo del proyecto, quiero consultar quién hizo qué y cuándo, para investigar un problema o demostrar que una decisión fue de la Junta.
- **Prioridad:** should

```gherkin
Escenario: filtro por fecha
  Dado eventos de auditoría de varios días
  Cuando se consulta GET /api/v1/audit-log con un rango de fechas y limit 20
  Entonces la respuesta trae hasta 20 eventos con cursor opaco firmado para la siguiente página

Escenario: sin secretos en la respuesta
  Dado un evento de cambio de contraseña
  Cuando se consulta la bitácora
  Entonces el evento no contiene la contraseña, ni tokens, ni la IP en claro (solo HMAC)

Escenario: el fontanero no ve la auditoría
  Dado un usuario con rol OPERATOR
  Cuando consulta GET /api/v1/security-events
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-37; RNF-09, RNF-19, RNF-24.
- **Patrones:** P09 (`AuditingCommandHandler`); P17 (`Observer`, publica los eventos de seguridad).

---

## M13: Datos simulados e importación de historial

### HU-38: Importar historial simulado solo en el acueducto demo

- **Actor:** `PROJECT_TEAM`.
- **Historia:** Como equipo del proyecto, quiero cargar un año de datos simulados en el acueducto demo, para probar reglas, pronósticos y evaluación con un historial realista.
- **Prioridad:** should

```gherkin
Escenario: importación en el acueducto demo
  Dado el acueducto con is_demo en verdadero
  Cuando el equipo envía POST /api/v1/imports/readings con 1.000 lecturas en un archivo de 200 KiB
  Entonces se crea una fila en sim.import_batches con kind READINGS y los conteos aceptados y rechazados
  Y las lecturas se marcan como origen IMPORT

Escenario: importación en un acueducto real
  Dado un acueducto con is_demo en falso
  Cuando se intenta importar
  Entonces recibe 403 con código FORBIDDEN
  Y se registra IMPORT_REJECTED en iam.security_events con severidad HIGH

Escenario: archivo demasiado grande
  Cuando el cuerpo supera 1 MiB o 5.000 filas
  Entonces recibe 413 o 400 con código VALIDATION_ERROR y no importa nada
```

- **Requisitos:** RF-38; RNF-04, RNF-07.
- **Patrones:** P12 (`Chain of Responsibility`, validación de cada fila); P17 (`Observer`, evento de importación).

### HU-39: Mostrar que los datos son simulados

- **Actor:** todos los roles y el público.
- **Historia:** Como vecino o miembro de la Junta, quiero ver claramente cuándo los datos son simulados, para no tomar una prueba como si fuera la realidad.
- **Prioridad:** must

```gherkin
Escenario: acueducto demo
  Dado el acueducto demo
  Cuando se abre el estado, el horario, el cartel o un acta
  Entonces aparece la marca "Datos simulados" en la pantalla y en el PDF
  Y la API devuelve el campo simulated en verdadero

Escenario: acueducto real
  Dado un acueducto con is_demo en falso
  Cuando se abre la página pública
  Entonces no aparece la marca y simulated es falso

Escenario: el texto de WhatsApp
  Dado el acueducto demo
  Cuando se genera el mensaje de WhatsApp
  Entonces el texto empieza con "[Datos simulados]"
```

- **Requisitos:** RF-39; RNF-21, RNF-28.
- **Patrones:** P22 (`Interpreter`, plantillas de mensajes con la marca); P07 (`Bridge`, `Notice` se muestra igual por cualquier canal).

---

## Resumen por módulo

| Módulo | Historias | Cantidad | Prioridad (must / should) |
|---|---|---|---|
| M1 Login y usuarios | HU-01 a HU-06 | 6 | 4 / 2 |
| M2 Reglas versionadas | HU-07 a HU-08 | 2 | 2 / 0 |
| M3 Lecturas offline | HU-09 a HU-13 | 5 | 4 / 1 |
| M4 Estado y pronóstico | HU-14 a HU-17 | 4 | 3 / 1 |
| M5 Propuesta | HU-18 a HU-19 | 2 | 2 / 0 |
| M6 Decisión | HU-20 a HU-22 | 3 | 2 / 1 |
| M7 Publicación y reportes | HU-23 a HU-28 | 6 | 3 / 2 (y 1 could: HU-26) |
| M8 Cierre del día | HU-29 | 1 | 1 / 0 |
| M9 Actas y entidades | HU-30 a HU-32 | 3 | 0 / 3 |
| M10 Evaluación | HU-33 | 1 | 0 / 1 |
| M11 Dispositivos | HU-34 a HU-36 | 3 | 0 / 3 |
| M12 Auditoría | HU-37 | 1 | 0 / 1 |
| M13 Datos simulados | HU-38 a HU-39 | 2 | 1 / 1 |

Total: 39 historias. Prioridades: 22 `must`, 16 `should` y 1 `could` (HU-26, cartel).

Alcance por fase (ver `Caudal.md` y los hechos, sección 19): el MVP cubre las fases 1 a 8 y el `backfill` de historial. Las historias de M9 (actas y entidades), M10, M11 y M12 corresponden a fases posteriores (9 a 11), salvo HU-38 (importación de historial simulado), que sí forma parte del MVP.

Relacionados: [Caudal.md](Caudal.md) · [Actores-y-permisos.md](Actores-y-permisos.md) · [Requerimientos.md](Requerimientos.md) · [Riesgos.md](Riesgos.md)
