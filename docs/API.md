# API REST de CAUDAL

Este documento describe la API REST de `caudal-backend` para quien la implementa, la consume o la prueba. Cubre todos los endpoints de la sección 13 de los hechos canónicos. La especificación formal (OpenAPI) se genera desde el código y se consulta en Swagger UI.

Convenciones de lectura:

- Los campos de tipo `texto` tienen límites tomados de `FieldLimits` (`org`, `ops` y `iam` en `schema.yaml`). Cuando un límite viene de la BD se indica el nombre de la constante.
- Los códigos de error están en inglés. Los mensajes para el usuario están en español.
- Los valores de ejemplo son ficticios. No son datos reales de Guaitarilla.
- Los permisos son códigos de `iam.permissions`. Los nombres de esta página son propuesta de diseño, verificar contra la BD cuando exista la Fase 2.

## 1. Convenciones

### 1.1 Prefijo, formato y nombres

| Tema | Decisión |
|---|---|
| Prefijo | Todas las rutas empiezan con `/api/v1`. Excepciones: `/actuator/health` y la documentación (`/swagger-ui.html`, `/v3/api-docs`). |
| Formato de datos | JSON en UTF-8. Content-Type `application/json`. |
| Nombres de campos JSON | `snake_case`, igual que las columnas de la base de datos y que el resto de proyectos del equipo (por ejemplo, Insumap). El frontend en TypeScript usa `snake_case` en los DTO y `camelCase` solo en su capa de vista (`ApiDtoAdapter`). |
| Códigos de error y enums | Inglés en `SCREAMING_SNAKE_CASE` (`VALIDATION_ERROR`, `HIGH`, `PRIORITY_THEN_LONGEST_WAIT`). |
| Fechas y horas | ISO-8601 con zona, por ejemplo `2026-10-09T08:00:00-05:00`. El servidor guarda UTC y responde con offset `Z` o el que corresponda. Fechas sin hora (`service_date`) usan `YYYY-MM-DD`. |
| Números decimales | En JSON, con punto (`2.10`). La UI muestra coma (`2,10`). Los valores de nivel tienen dos decimales. |
| Identificadores | UUID en texto. Los generados por el cliente (lecturas, puntos de telemetría) son UUID versión 7 recomendados. |
| Zona horaria | Los datos se muestran en `America/Bogota`. |

Nota: el campo de correlación se llama `request_id`, en `snake_case` como el resto del JSON.

### 1.2 Autenticación

Hay tres mecanismos, según el actor:

| Actor | Mecanismo | Cabecera o cookie |
|---|---|---|
| Personas (Junta, fontanero, equipo, entidades) | JWT de acceso (HS256, 15 minutos) | `Authorization: Bearer <access_token>` |
| Sesión de la persona | Refresh token opaco de 256 bits | Cookie `caudal_rt` (`HttpOnly; Secure; SameSite=None; Path=/api/v1/auth`), con cabecera `X-Requested-With: caudal-web` y verificación de `Origin` |
| Dispositivos (sensores, válvulas, gateways) | Firma Ed25519 sobre la petición | Cabeceras `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature` (ver `Protocolo-de-dispositivos.md`) |
| Servicio de IA | Token de servicio | `Authorization: Bearer <IA_SERVICE_TOKEN>` (solo entre backend e IA, ver `Contrato-IA.md`) |
| Público | Ninguna | Solo en `/api/v1/public/*` |

Reglas del JWT de acceso:

- Claims: `iss`, `aud`, `sub`, `role`, `aqueduct_id`, `tv` (token_version), `jti`, `iat`, `exp`.
- Se valida el algoritmo de forma explícita (`HS256`), `iss`, `aud`, `exp` y `nbf`, con tolerancia de 30 segundos.
- El secreto tiene al menos 256 bits.
- `Authorization` mayor de 2 KiB se rechaza antes de parsear (`413`).
- El access token vive solo en memoria del frontend. Nunca va en `localStorage`.

Refresh token: se rota en cada uso. Si se reutiliza un token ya rotado, se revoca toda su familia y se registra `TOKEN_REUSE_DETECTED`. Duración: 7 días; 30 días para `OPERATOR` (configurable).

Cambiar contraseña, cambiar rol o desactivar una cuenta incrementa `token_version` y revoca sus refresh tokens. Así los access tokens vigentes dejan de servir.

La cookie del refresh token se llama `caudal_rt`. El frontend (Vercel) y la API (Render) son sitios distintos, así que la cookie usa `SameSite=None; Secure; HttpOnly; Path=/api/v1/auth`. Además, la API exige la cabecera `X-Requested-With: caudal-web` y verifica que `Origin` esté en `CORS_ALLOWED_ORIGINS`. Si algún día comparten dominio, se pasa a `SameSite=Strict`.

### 1.3 Formato de error canónico

Toda respuesta de error tiene esta forma:

```json
{
  "error": {
    "code": "GAUGE_OUT_OF_RANGE",
    "message": "La lectura está fuera del rango de la regla del tanque (0,00 a 5,00).",
    "details": {
      "gauge_min": "0,00",
      "gauge_max": "5,00",
      "value": "8,00"
    },
    "request_id": "7f3c2a9e-1d4b-4e1a-9c0e-2b8f5a6d1e33"
  }
}
```

- `code`: código del catálogo de la siguiente tabla. El frontend decide su comportamiento con este campo, no con el texto.
- `message`: en español, pensado para mostrarse al usuario. No revela si un usuario existe.
- `details`: opcional. Siempre un objeto JSON. No contiene trazas, SQL ni secretos.
- `request_id`: correlaciona la respuesta con los logs. También viene en la cabecera `X-Request-Id`.

### 1.4 Catálogo de códigos de error

| Código | HTTP | Mensaje (español, ejemplo) | Cuándo |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Revisa los datos enviados. | Formato, tipo, longitud, campo desconocido, JSON mal formado, rango de fechas inválido, UUID inválido |
| `UNAUTHORIZED` | 401 | Sesión no válida. Vuelve a iniciar sesión. | Falta el token, expiró o no es válido. |
| `INVALID_CREDENTIALS` | 401 | Usuario o contraseña incorrectos. | Login fallido. Igual con cuenta bloqueada o desactivada: no se revela el bloqueo. |
| `SESSION_REVOKED` | 401 | Tu sesión se cerró. Vuelve a iniciar sesión. | Refresh token reutilizado o familia revocada |
| `INVALID_SIGNATURE` | 401 | La firma del dispositivo no es válida. | Firma Ed25519 incorrecta |
| `NONCE_REPLAY` | 401 | Petición repetida. | `X-Nonce` ya visto en los últimos 10 minutos |
| `TIMESTAMP_OUT_OF_WINDOW` | 401 | La hora de la petición está fuera de la ventana permitida. | `X-Timestamp` fuera de ±300 s |
| `FORBIDDEN` | 403 | No tienes permiso para esta acción. | Falta el permiso o el recurso es de otro acueducto |
| `PASSWORD_CHANGE_REQUIRED` | 403 | Debes cambiar tu contraseña antes de continuar. | `must_change_password = true`; solo se permiten `/auth/me`, `/auth/change-password` y `/auth/logout` |
| `PRIVACY_NOTICE_PENDING` | 403 | Debes aceptar el aviso de privacidad. | Hay una versión nueva sin aceptar |
| `DEVICE_INACTIVE` | 403 | El dispositivo no está activo. | Dispositivo `SUSPENDED` o `RETIRED` |
| `DEMO_ONLY` | 403 | Esta operación solo aplica al acueducto de demostración. | Importación fuera de `is_demo = true` |
| `NOT_FOUND` | 404 | No encontramos ese recurso. | Recurso inexistente o fuera del acueducto (no se distingue, para no revelar existencia) |
| `CONFLICT` | 409 | Los datos cambiaron. Recarga e intenta de nuevo. | `lock_version` distinto, usuario o código ya existente, cierre ya registrado |
| `INVALID_STATE_TRANSITION` | 409 | Esta acción no es válida en el estado actual. | Transición no permitida (propuesta, incidente, anomalía, día cerrado) |
| `DUPLICATE_READING` | 409 | Ya existe una lectura con ese identificador y otro contenido. | Mismo UUID de cliente con contenido distinto |
| `SCHEDULE_OVERLAP` | 409 | Los turnos se solapan. | Turnos que se cruzan en el tiempo |
| `RULE_SET_IMMUTABLE` | 409 | Las reglas activas no se pueden modificar. Crea un borrador nuevo. | Edición de una versión `ACTIVE` o `SUPERSEDED` |
| `RATE_LIMITED` | 429 | Hiciste demasiadas solicitudes. Espera un momento. | Límite de tasa. Incluye `Retry-After` en segundos |
| `GAUGE_OUT_OF_RANGE` | 422 | La lectura está fuera del rango de la regla del tanque. | Valor fuera de `gauge_min..gauge_max` o con más de 2 decimales |
| `MISSING_TIMESTAMP` | 422 | Falta la fecha y hora de la observación. | `observed_at` ausente o no válido |
| `FUTURE_TIMESTAMP` | 422 | La fecha de la observación está en el futuro. | `observed_at` mayor que ahora + 5 minutos |
| `TOO_OLD` | 422 | La observación es demasiado antigua para registrarla. | Más de `max_backdate_days` días atrás |
| `REASON_REQUIRED` | 422 | Escribe el motivo (entre 10 y 500 caracteres). | Motivo ausente, vacío o de menos de 10 caracteres |
| `PASSWORD_POLICY_VIOLATION` | 422 | La contraseña no cumple la política. | Menos de 12 caracteres, en la lista de comunes, contiene el usuario, repetida del historial |
| `RULE_SET_INVALID` | 422 | La versión de reglas no es válida. | Bandas con solape o huecos, rangos inválidos (ver `Reglas-de-negocio.md`, sección 2) |
| `NO_LEVEL_DATA` | 422 | No hay un nivel válido para calcular la propuesta. | No hay lectura efectiva del tanque |
| `HOURS_EXCEEDED` | 422 | La suma de turnos supera las horas disponibles. | Turnos modificados que superan el presupuesto del día |
| `SHIFT_LENGTH_INVALID` | 422 | La duración del turno no está entre el mínimo y el máximo. | Fuera de `min_shift_hours..max_shift_hours` |
| `OUTSIDE_OPERATING_WINDOW` | 422 | El turno cae fuera del horario de operación. | Fuera de `rule_operating_windows` |
| `IMPORT_LIMIT_EXCEEDED` | 422 | El lote supera 5.000 filas. | Importación con más filas del límite |
| `INVALID_KEY` | 422 | La clave pública no es válida. | No es Ed25519 de 32 bytes en base64url |
| `CURSOR_INVALID` | 400 | El cursor de paginación no es válido. | Cursor alterado o expirado |
| `PAYLOAD_TOO_LARGE` | 413 | El cuerpo de la petición es demasiado grande. | Supera el límite de la familia de rutas (ver sección 1.8) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Tipo de contenido no soportado. | Content-Type distinto de `application/json` en rutas JSON |
| `IA_UNAVAILABLE` | 503 | El servicio de pronóstico no responde. Se usa la estimación simple. | Solo si fallan la IA y la estimación de respaldo a la vez (no debería ocurrir). El fallo normal de la IA no devuelve error: responde `200`/`201` con `is_fallback = true`. |
| `INTERNAL_ERROR` | 500 | Ocurrió un error. Intenta de nuevo más tarde. | Error no controlado. Sin trazas en la respuesta. |

Los códigos del servicio de IA (`INSUFFICIENT_HISTORY`, `INVALID_SERIES`, `MODEL_NOT_READY`) no llegan al cliente de la API. El backend los convierte en respaldo (ver `Contrato-IA.md`).

### 1.5 Paginación por cursor

Las colecciones son paginadas con cursor opaco firmado.

- Parámetros: `limit` (entero de 1 a 100, por defecto 20) y `cursor` (texto opaco, opcional).
- Respuesta: `items` (lista) y `next_cursor` (texto o `null` si no hay más).

```json
{
  "items": [{ "id": "0190f3a2-7c1d-7b2e-9f11-3d4c5e6f7a81", "status": "REPORTED" }],
  "next_cursor": "eyJvIjoyMH0.c2lnbmF0dXJh"
}
```

- El cursor se firma con HMAC. Un cursor alterado responde `400 CURSOR_INVALID`.
- Las listas tienen orden estable (por `reported_at`, `observed_at`, `created_at` o `id`, según el recurso, descendente por defecto).
- No se soportan `offset` ni conteos totales.

### 1.6 Idempotencia

| Operación | Clave de idempotencia | Comportamiento |
|---|---|---|
| `POST /readings` | `id` (UUID del cliente) | Mismo `id` y mismo contenido: `200` con la lectura original y `idempotent_replay: true`. Mismo `id` con contenido distinto: `409 DUPLICATE_READING`. |
| `POST /readings/batch` | `sync_batch_id` y `id` de cada lectura | Un lote repetido devuelve el resultado original. Cada lectura ya guardada se marca como `REPLAYED`. |
| `POST /devices/telemetry` | `id` de cada punto | Un punto repetido se cuenta en `duplicates` y no se guarda otra vez. |
| `POST /imports/*` | `file_sha256` del archivo | Un archivo ya importado responde `409 CONFLICT`. |
| Resto de `POST` | Por definir | No hay idempotencia por cabecera en esta versión. Usar `lock_version` donde aplique. |

### 1.7 Versionado

- Versión en la ruta: `/api/v1`.
- Cambios aditivos (campos nuevos en respuestas, endpoints nuevos, valores nuevos opcionales) no suben la versión. Los clientes deben ignorar campos desconocidos en respuestas.
- Cambios incompatibles (quitar campos, cambiar tipos, cambiar significado de un código) requieren `/api/v2`.
- Las versiones anteriores se marcan con cabeceras `Deprecation` y `Sunset` antes de retirarse (propuesta).
- El contrato con la IA tiene su propia versión (`/v1/*` en el servicio de IA). Ver `Contrato-IA.md`.

### 1.8 Límites de tamaño y de tasa

| Límite | Valor | Fuente |
|---|---|---|
| Cuerpo JSON (por defecto) | 64 KiB | Hechos, sección 11 |
| Cuerpo de telemetría | 256 KiB | Hechos, sección 11 |
| Cuerpo de importación | 1 MiB o 5.000 filas por lote | Hechos, sección 11 |
| Cabecera `Authorization` | 2 KiB | Hechos, sección 11 |
| Profundidad JSON | 10 niveles | Hechos, sección 11 |
| Elementos por lista | 100 (salvo que el campo indique otro) | Hechos, sección 11 |
| Texto global | 2.000 caracteres (tope por campo además) | Hechos, sección 11 |
| Campos desconocidos | Se rechazan (`VALIDATION_ERROR`) | `FAIL_ON_UNKNOWN_PROPERTIES` |
| Tipos | Sin coerción (un número no es texto) | Hechos, sección 11 |
| Login | 5 por minuto por IP | Hechos, sección 11 |
| Reportes públicos de daño | 3 por hora por IP, más honeypot y tiempo mínimo de llenado | Hechos, sección 11 |
| API autenticada | 120 por minuto por usuario | Hechos, sección 11 |
| Telemetría | 60 por minuto por dispositivo | Hechos, sección 11 |
| Recálculo de pronóstico | 10 por hora por acueducto | Hechos, sección 11 |
| Otros grupos (importación, lectura pública, evaluación) | Por definir | |

Respuesta al exceder un límite: `429 RATE_LIMITED` con `Retry-After`. El contador vive en PostgreSQL (`iam.rate_limit_buckets`) para que sea compartido entre instancias.

Campo `text` con normalización: NFC, recorte de espacios, colapso de espacios internos en nombres. Se rechazan caracteres de control (salvo `\n` en textos largos), caracteres bidi (U+202A a U+202E, U+2066 a U+2069) y de ancho cero (U+200B a U+200F, U+FEFF).

### 1.9 Documentación Swagger

- Swagger UI: `/swagger-ui.html`. OpenAPI JSON: `/v3/api-docs`.
- Activo en todos los entornos. Se apaga con `API_DOCS_ENABLED=false`.
- `/v3/api-docs` y `/swagger-ui.html` son públicos (solo documentación). Cabecera CSP de `/swagger-ui/**`: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'`. El resto de la API usa `default-src 'none'; frame-ancestors 'none'`.
- Esquema de seguridad `bearerAuth` (JWT). Ejecutar peticiones desde Swagger requiere un JWT válido.
- Cada endpoint tiene `summary` y `description` en español.
- Los ejemplos usan datos ficticios. No se publican datos reales de personas ni de la comunidad.
- Los ejemplos de respuesta de errores usan los códigos de la sección 1.4.

### 1.10 Notación de este documento

Cada endpoint indica:

- **Permiso:** código requerido. Los roles que lo tienen están en la matriz de la sección 9 de los hechos.
- **Cuerpo:** campos, tipo, si es obligatorio y límites.
- **Respuesta:** campos principales.
- **Errores:** códigos posibles, además de los comunes: `401 UNAUTHORIZED` si no hay sesión, `403 FORBIDDEN` si falta el permiso, `404 NOT_FOUND` si el recurso no existe en el acueducto, `429 RATE_LIMITED` si se excede la tasa, `500 INTERNAL_ERROR`. Estos comunes no se repiten en cada endpoint.
- **Ejemplo:** fragmento corto.

Los permisos de la tabla siguiente son los que se usan en las secciones de recursos:

| Permiso (propuesta) | BOARD_ADMIN | BOARD_MEMBER | OPERATOR | PROJECT_TEAM | SUPPORT_ENTITY | Público |
|---|---|---|---|---|---|---|
| `USER_MANAGE`, `MEMBERSHIP_MANAGE` | Sí | No | No | No | No | No |
| `RULESET_PROPOSE` (crear borrador) | Sí | Sí | No | No | No | No |
| `RULESET_EDIT` | Sí | Sí | No | No | No | No |
| `RULESET_ACTIVATE` | Sí | No | No | No | No | No |
| `READING_CREATE` | Sí | No | Sí | No | No | No |
| `READING_CORRECT` | Sí | Sí | Sí (propias) | No | No | No |
| `TANK_STATUS_READ` | Sí | Sí | Sí | Sí | No | No |
| `FORECAST_RUN` | Sí | Sí | Sí | No | No | No |
| `PROPOSAL_GENERATE`, `PROPOSAL_DECIDE`, `PROPOSAL_PUBLISH` | Sí | Sí | No | No | No | No |
| `PROPOSAL_READ` (borradores) | Sí | Sí | No | No | No | No |
| `EXECUTION_RECORD`, `DAY_CLOSE` | Sí | No | Sí | No | No | No |
| `INCIDENT_CREATE`, `INCIDENT_READ` | Sí | Sí | Sí | Sí | No | No (solo público) |
| `MINUTES_GENERATE`, `MINUTES_READ`, `SUMMARY_SHARE_MANAGE` | Sí | Sí (sin compartir) | No | No | No | No |
| `SUMMARY_READ` | Sí | Sí | No | No | Sí (solo autorizados) | No |
| `EVALUATION_READ`, `IMPORT_RUN`, `AUDIT_READ`, `DEVICE_REGISTER` | Sí | No | No | Sí | No | No |
| `VALVE_COMMAND_MANUAL` | Sí | No | No | No | No | No |

Nota: `BOARD_MEMBER` no comparte resúmenes ni registra dispositivos. La tabla responde a la matriz de la sección 9 de los hechos. Los nombres de permisos son propuesta y se confirman al cargar `iam.permissions` (Fase 2).

## 2. Autenticación (`/auth`)

### 2.1 `POST /api/v1/auth/login`

- **Permiso:** público.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `username` | texto | Sí | 3 a 32, `^[a-z0-9][a-z0-9._-]{1,30}[a-z0-9]$`, se normaliza a minúsculas (`FieldLimits.USERNAME`) |
| `password` | texto | Sí | 12 a 128, NFKC (`FieldLimits.PASSWORD`) |

- **Respuesta 200:** `access_token` (texto), `token_type` (`Bearer`), `expires_in` (900), `user` (`id`, `username`, `full_name`, `role`, `aqueduct_id`, `must_change_password`). Cookie `Set-Cookie` con el refresh token.
- **Errores:** `401 INVALID_CREDENTIALS` (siempre con el mismo mensaje, aunque la cuenta esté bloqueada o desactivada), `429 RATE_LIMITED`.
- **Notas:** bloqueo por cuenta (5 fallos consecutivos en 15 minutos → 15 minutos, escalonado (x2) hasta 24 horas), bloqueo por IP (20 intentos en 15 minutos) y tiempo constante con hash señuelo. El flujo de MFA por definir (ver 2.8).
- **Ejemplo:**

```json
{ "username": "junta.presidencia", "password": "contraseña-de-ejemplo-larga" }
```

### 2.2 `POST /api/v1/auth/refresh`

- **Permiso:** cookie `caudal_rt` válida, cabecera `X-Requested-With: caudal-web` y `Origin` permitido. Sin cuerpo.
- **Respuesta 200:** `access_token` nuevo y `expires_in`. Cookie rotada.
- **Errores:** `401 UNAUTHORIZED` (sin cookie o vencida), `401 SESSION_REVOKED` (reutilización detectada: se revoca la familia), `403 FORBIDDEN` (falta `X-Requested-With` o `Origin` no permitido).

### 2.3 `POST /api/v1/auth/logout`

- **Permiso:** autenticado.
- **Respuesta 204.** Revoca la familia del refresh token actual (`LOGOUT`) y borra la cookie.
- **Nota:** si hay pendientes en la cola offline, el frontend avisa antes de llamar a este endpoint.

### 2.4 `POST /api/v1/auth/logout-all`

- **Permiso:** autenticado.
- **Respuesta 204.** Revoca todos los refresh tokens del usuario (`LOGOUT_ALL`).
- **Por definir:** si además incrementa `token_version` para invalidar los access tokens vigentes (propuesta: sí).

### 2.5 `POST /api/v1/auth/change-password`

- **Permiso:** autenticado. Permitido aunque `must_change_password = true`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `current_password` | texto | Sí | 1 a 128 |
| `new_password` | texto | Sí | 12 a 128, NFKC, sin lista de comunes, sin el usuario, no repetida de las últimas 5 |

- **Respuesta 204.** Incrementa `token_version`, revoca los refresh tokens (`PASSWORD_CHANGED`) y pone `must_change_password = false`.
- **Errores:** `401 UNAUTHORIZED` (contraseña actual incorrecta), `422 PASSWORD_POLICY_VIOLATION`.
- **Ejemplo:**

```json
{ "current_password": "contraseña-temporal-ejemplo", "new_password": "nueva-contraseña-de-ejemplo" }
```

### 2.6 `POST /api/v1/auth/reset-password`

- **Permiso:** público (con código de un solo uso emitido por la Junta).
- **Cuerpo:** `username` (texto, mismo límite que login), `code` (texto, longitud por definir), `new_password` (mismo límite que 2.5).
- **Respuesta 204.** Marca el código como usado y revoca las sesiones.
- **Errores:** `401 UNAUTHORIZED` (código inválido, vencido o usado; mensaje genérico), `422 PASSWORD_POLICY_VIOLATION`, `429 RATE_LIMITED`.

### 2.7 `GET /api/v1/auth/me`

- **Permiso:** autenticado. Permitido aunque `must_change_password = true`.
- **Respuesta 200:** `id`, `username`, `full_name`, `status`, `must_change_password`, `privacy_accepted_version`, `memberships` (lista de `aqueduct_id`, `role`, `valid_from`, `valid_to`) y `permissions` (lista de códigos).
- **Ejemplo:**

```json
{
  "id": "0190f3a2-0000-7000-8000-000000000001",
  "username": "fontanero.ejemplo",
  "full_name": "Persona de Ejemplo",
  "must_change_password": false,
  "memberships": [{ "aqueduct_id": "0190f3a2-0000-7000-8000-0000000000aa", "role": "OPERATOR" }],
  "permissions": ["READING_CREATE", "READING_CORRECT", "EXECUTION_RECORD", "DAY_CLOSE"]
}
```

### 2.8 `POST /api/v1/auth/mfa/*`

- **Permiso:** autenticado (roles `BOARD_ADMIN` y `PROJECT_TEAM`).
- **Rutas:** por definir. Los hechos solo nombran el prefijo. Se espera enrolamiento TOTP (secreto cifrado con AES-256-GCM, códigos de respaldo hasheados y anti-repetición del paso), confirmación y verificación en login.

### 2.9 `POST /api/v1/auth/switch-aqueduct`

- **Permiso:** autenticado.
- **Cuerpo:** `aqueduct_id` (UUID, obligatorio).
- **Respuesta 200:** `access_token` nuevo con `aqueduct_id` del acueducto indicado. Un JWT lleva un solo acueducto (el activo).
- **Errores:** `403 FORBIDDEN` (la membresía no está vigente o no existe).
- **Ejemplo:** `{ "aqueduct_id": "0190f3a2-0000-7000-8000-0000000000bb" }`

## 3. Usuarios y membresías

### 3.1 `GET /api/v1/users`

- **Permiso:** `USER_MANAGE`.
- **Parámetros:** `status` (`ACTIVE`, `LOCKED`, `DISABLED`), `limit`, `cursor`.
- **Respuesta:** lista de `id`, `username`, `full_name` (confidencial, solo para `BOARD_ADMIN`), `status`, `must_change_password`, `last_login_at`.
- **Ejemplo:** `GET /api/v1/users?status=ACTIVE&limit=20`

### 3.2 `POST /api/v1/users`

- **Permiso:** `USER_MANAGE`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `username` | texto | Sí | 3 a 32, regex de login (`FieldLimits.USERNAME`) |
| `full_name` | texto | Sí | 2 a 80, letras, espacio, `'`, `-`, `.` (`FieldLimits.PERSON_NAME`) |
| `aqueduct_id` | UUID | Sí | Acueducto existente |
| `role` | texto | Sí | `BOARD_ADMIN`, `BOARD_MEMBER`, `OPERATOR`, `PROJECT_TEAM`, `SUPPORT_ENTITY` |

- **Respuesta 201:** usuario creado con `must_change_password = true` y sin contraseña conocida. Se guarda un hash Argon2id de 32 bytes aleatorios, que no sirve para entrar. Además el sistema emite un código de activación de un solo uso (`iam.password_reset_grants`): la respuesta trae `activation_code` y `expires_at`, y el código se muestra una sola vez. El usuario fija su contraseña con `POST /auth/reset-password`.
- **Errores:** `409 CONFLICT` (usuario ya existe). Los errores de formato responden `400 VALIDATION_ERROR`.
- **Ejemplo:**

```json
{ "username": "operador.ejemplo", "full_name": "Persona de Ejemplo", "aqueduct_id": "0190f3a2-0000-7000-8000-0000000000aa", "role": "OPERATOR" }
```

### 3.3 `PATCH /api/v1/users/{id}`

- **Permiso:** `USER_MANAGE`.
- **Cuerpo (todos opcionales):** `full_name` (2 a 80), `status` (`ACTIVE`, `LOCKED`, `DISABLED`).
- **Respuesta 200:** usuario actualizado.
- **Efecto:** desactivar o bloquear revoca sus refresh tokens e incrementa `token_version`.
- **Errores:** `404 NOT_FOUND`, `409 INVALID_STATE_TRANSITION` (estado no permitido).
- **Ejemplo:** `{ "status": "DISABLED" }`

### 3.4 `POST /api/v1/users/{id}/reset-grants`

- **Permiso:** `USER_MANAGE`.
- **Cuerpo:** `reason` (texto, 10 a 500, `FieldLimits.REASON`).
- **Respuesta 201:** `id`, `code` (se muestra una sola vez), `expires_at`. Solo se guarda el hash SHA-256 del código.
- **Errores:** `422 REASON_REQUIRED`.
- **Ejemplo:** `{ "code": "ABCD-2345", "expires_at": "2026-10-09T10:00:00-05:00" }` (valores ficticios)

### 3.5 `GET /api/v1/memberships`

- **Permiso:** `MEMBERSHIP_MANAGE`.
- **Parámetros:** `user_id`, `aqueduct_id`, `limit`, `cursor`.
- **Respuesta:** lista de `id`, `user_id`, `aqueduct_id`, `role_code`, `valid_from`, `valid_to`, `granted_by`.

### 3.6 `POST /api/v1/memberships`

- **Permiso:** `MEMBERSHIP_MANAGE`.
- **Cuerpo:** `user_id` (UUID, obligatorio), `aqueduct_id` (UUID, obligatorio), `role_code` (enum de la sección 3.2, obligatorio), `valid_from` (fecha y hora, opcional, por defecto ahora), `valid_to` (opcional, debe ser mayor que `valid_from`).
- **Respuesta 201:** la membresía.
- **Errores:** `400 VALIDATION_ERROR` (`valid_to` no mayor que `valid_from`), `409 CONFLICT` (por definir: membresía vigente del mismo rol).

### 3.7 `DELETE /api/v1/memberships/{id}`

- **Permiso:** `MEMBERSHIP_MANAGE`.
- **Respuesta 204.** No borra la fila: hace `UPDATE` de `valid_to = ahora`. Las membresías no se eliminan porque la auditoría las referencia.
- **Efecto:** incrementa `token_version` del usuario.

## 4. Meta, catálogos y aviso de privacidad

### 4.1 `GET /api/v1/meta/constraints`

- **Permiso:** público.
- **Respuesta:** los límites de `FieldLimits` que el frontend usa para armar sus esquemas Zod. Cada campo: `min`, `max`, `pattern` (si aplica).
- **Ejemplo:**

```json
{ "username": { "min": 3, "max": 32 }, "password": { "min": 12, "max": 128 }, "reading.note": { "min": 0, "max": 500, "max_lines": 10 } }
```

### 4.2 `GET /api/v1/catalogs/{catalog}`

- **Permiso:** público (solo etiquetas).
- **Parámetro de ruta:** `catalog` = `WATER_APPEARANCE` o `DAMAGE_CATEGORY`. Otro valor: `404 NOT_FOUND`.
- **Respuesta:** lista de `code`, `label_es`, `sort_order`, solo elementos activos y del acueducto o globales.
- **Ejemplo:** `{ "items": [{ "code": "NORMAL", "label_es": "Normal", "sort_order": 1 }] }`
- **Catálogos de la semilla:** `WATER_APPEARANCE` (`NORMAL` Normal, `MUDDY` Con barro, `TURBID` Turbia, `NOT_OBSERVED` Sin observar, usado por las lecturas de sensor) y `DAMAGE_CATEGORY` (`LEAK` Fuga, `BROKEN_PIPE` Tubo roto, `NO_WATER` Sin agua, `DIRTY_WATER` Agua sucia, `VALVE_FAILURE` Falla de válvula, `OTHER` Otro). Para lecturas de sensor ver `Protocolo-de-dispositivos.md`.

### 4.3 `GET /api/v1/privacy-notice/current`

- **Permiso:** autenticado.
- **Respuesta:** `version`, `text_es`, `text_sha256`, `effective_at`.

### 4.4 `POST /api/v1/privacy-notice/accept`

- **Permiso:** autenticado.
- **Cuerpo:** `version` (entero, obligatorio, debe ser la versión vigente).
- **Respuesta 201:** `user_id`, `version`, `accepted_at`.
- **Errores:** `409 CONFLICT` (la versión ya no es la vigente).

## 5. Red: tanques, sectores y válvulas

La red (tanques, sectores y válvulas) la gestiona `BOARD_ADMIN` (`NETWORK_MANAGE`). Los textos siguen `FieldLimits.PLACE_NAME` (2 a 60).

### 5.1 `GET /api/v1/tanks`

- **Permiso:** autenticado.
- **Respuesta:** lista de `id`, `name`, `gauge_min`, `gauge_max`, `gauge_step`, `capacity_liters`, `is_active`.

### 5.2 `POST /api/v1/tanks`

- **Permiso:** `BOARD_ADMIN`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `name` | texto | Sí | 2 a 60 (`PLACE_NAME`) |
| `gauge_min` | decimal (2) | Sí | `numeric(6,2)`: hasta 9999.99 en valor absoluto |
| `gauge_max` | decimal (2) | Sí | Mayor que `gauge_min` |
| `gauge_step` | decimal (2) | Sí | Mayor que 0 |
| `capacity_liters` | entero | No | Mayor que 0 |

- **Respuesta 201:** tanque creado.
- **Errores:** `400 VALIDATION_ERROR` (`gauge_max <= gauge_min`).
- **Ejemplo:** `{ "name": "Tanque principal", "gauge_min": 0.00, "gauge_max": 5.00, "gauge_step": 0.01 }`

### 5.3 `GET /api/v1/sectors`

- **Permiso:** autenticado.
- **Respuesta:** lista de `id`, `code`, `name`, `parent_sector_id`, `households_count`, `is_active`. Sin datos personales.

### 5.4 `POST /api/v1/sectors`

- **Permiso:** `BOARD_ADMIN`.
- **Cuerpo:** `code` (texto, 2 a 20, `^[A-Z0-9-]+$`, único en el acueducto; `FieldLimits.SECTOR_CODE`), `name` (2 a 60), `parent_sector_id` (UUID, opcional), `households_count` (entero, mayor o igual a 0, por defecto 0).
- **Respuesta 201:** sector creado.
- **Errores:** `409 CONFLICT` (código repetido en el acueducto).
- **Ejemplo:** `{ "code": "SEC-01", "name": "Sector Ejemplo", "households_count": 12 }`

### 5.5 `PATCH /api/v1/sectors/{id}`

- **Permiso:** `BOARD_ADMIN`.
- **Cuerpo (todos opcionales):** `name` (2 a 60), `households_count` (mayor o igual a 0), `is_active`. El `code` no cambia (propuesta).
- **Respuesta 200:** sector actualizado.

### 5.6 `GET /api/v1/sectors/{id}/valves`

- **Permiso:** autenticado.
- **Respuesta:** lista de `id`, `code`, `name`, `location_hint`, `is_motorized`, `fail_safe_position`, `is_active`.

### 5.7 `POST /api/v1/sectors/{id}/valves`

- **Permiso:** `BOARD_ADMIN`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `code` | texto | Sí | 1 a 20, `^[A-Z0-9-]+$`, único por sector (`FieldLimits.VALVE_CODE`) |
| `name` | texto | Sí | 2 a 60 (`PLACE_NAME`) |
| `location_hint` | texto | No | 0 a 120, una línea (`LOCATION_HINT`) |
| `is_motorized` | booleano | No | Por defecto `false` |
| `fail_safe_position` | texto | No | `KEEP`, `OPEN` o `CLOSED`. Por defecto `KEEP`. |

- **Respuesta 201:** válvula creada.
- **Errores:** `409 CONFLICT` (código repetido en el sector).

### 5.8 `PATCH /api/v1/valves/{id}`

- **Permiso:** `BOARD_ADMIN`.
- **Cuerpo (todos opcionales):** `name`, `location_hint`, `is_motorized`, `fail_safe_position`, `is_active`. El `code` no cambia.
- **Respuesta 200:** válvula actualizada.

## 6. Reglas de la Junta (`/rule-sets`)

Las reglas se leen en la app offline para validar lecturas, así que `GET /rule-sets/current` es el endpoint más consultado. Las reglas de negocio y su validación están en `Reglas-de-negocio.md`.

### 6.1 `GET /api/v1/rule-sets/current`

- **Permiso:** autenticado (`OPERATOR`, `PROJECT_TEAM`, Junta).
- **Respuesta 200:** versión `ACTIVE` completa: `id`, `version`, `valid_from`, `reserve_level`, `reserve_policy` (`NONE`, `REDUCE_TO_CRITICAL_BAND` o `PRIORITY_ONLY`), `max_daily_service_hours`, `min_shift_hours`, `max_shift_hours`, `allocation_strategy`, `duplicate_window_minutes`, `max_level_change_per_hour`, `stale_reading_hours`, `max_backdate_days`, `forecast_horizon_days`, `forecast_context_days`, `trend_threshold_per_day`, `bands` (lista de `band`, `min_level`, `max_level`, `daily_service_hours`, `priority_only`), `sectors` (`sector_id`, `is_included`, `is_priority`, `priority_rank`), `valve_order` (`valve_id`, `sequence_order`), `operating_windows` (`day_of_week`, `start_time`, `end_time`), `gauge` (`min`, `max`, `step`) y `server_time`.
- **Errores:** `404 NOT_FOUND` (no hay versión activa).
- **Ejemplo:**

```json
{
  "version": 3,
  "reserve_level": 1.00,
  "bands": [
    { "band": "LOW", "min_level": 1.50, "max_level": 3.50, "daily_service_hours": 8.00, "priority_only": false }
  ],
  "server_time": "2026-10-09T04:30:00-05:00"
}
```

### 6.2 `GET /api/v1/rule-sets`

- **Permiso:** `RULESET_PROPOSE` o `RULESET_EDIT`.
- **Parámetros:** `status` (`DRAFT`, `ACTIVE`, `SUPERSEDED`, `DISCARDED`), `limit`, `cursor`.
- **Respuesta:** lista de `id`, `version`, `status`, `valid_from`, `valid_to`, `activated_at`, `based_on_rule_set_id`.

### 6.3 `GET /api/v1/rule-sets/{id}`

- **Permiso:** igual que 6.2.
- **Respuesta:** la versión completa, como 6.1, incluidos `change_reason`, `created_by` y `activated_by`.

### 6.4 `POST /api/v1/rule-sets`

- **Permiso:** `RULESET_PROPOSE`.
- **Cuerpo:** `change_reason` (texto, opcional en el borrador; si viene, 10 a 500). Se exige al activar (6.6). Ver `Reglas-de-negocio.md`, sección 7.
- **Respuesta 201:** borrador copia de la versión `ACTIVE` (patrón Prototype, sección 1.1 de reglas). `BOARD_ADMIN` y `BOARD_MEMBER` pueden crearlo.
- **Errores:** `409 CONFLICT` (por definir: ya existe un borrador abierto).
- **Ejemplo:** `{ "change_reason": "Sube la reserva de 1,00 a 1,20 por la temporada seca." }`

### 6.5 `PATCH /api/v1/rule-sets/{id}`

- **Permiso:** `RULESET_EDIT`.
- **Cuerpo (todos opcionales, solo en `DRAFT`):** escalares de la sección 6.1 (`reserve_level`, `max_daily_service_hours`, etc.), y listas que reemplazan a las actuales: `bands` (hasta 10 elementos), `sectors`, `valve_order`, `operating_windows` (hasta 100).
- **Respuesta 200:** borrador actualizado con `lock_version` (propuesta).
- **Errores:** `409 RULE_SET_IMMUTABLE` (no es `DRAFT`), `400 VALIDATION_ERROR` (rangos), `422 RULE_SET_INVALID` (solo al activar).
- **Ejemplo:** `{ "reserve_level": 1.20 }`

### 6.6 `POST /api/v1/rule-sets/{id}/activate`

- **Permiso:** `RULESET_ACTIVATE` (solo `BOARD_ADMIN`).
- **Cuerpo:** `change_reason` (texto, 10 a 500, obligatorio, `FieldLimits.REASON`). Ver `Reglas-de-negocio.md`, sección 7.
- **Respuesta 200:** versión `ACTIVE` con `valid_from`. La versión anterior queda `SUPERSEDED`.
- **Errores:** `422 RULE_SET_INVALID` (validación de bandas, cobertura y rangos), `422 REASON_REQUIRED`, `409 INVALID_STATE_TRANSITION` (no es `DRAFT`).
- **Ejemplo:** `{ "change_reason": "Se activa la reserva de 1,20 aprobada en la reunión de octubre." }`

## 7. Lecturas del tanque (`/readings`)

Una lectura es una observación del nivel de la regla pintada. Su estatus epistémico es `OBSERVED`. Las reglas de validación están en `Reglas-de-negocio.md`, sección 8.

### 7.1 `POST /api/v1/readings`

- **Permiso:** `READING_CREATE`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `id` | UUID | Sí | UUID generado por el cliente (idempotencia) |
| `tank_id` | UUID | Sí | Tanque del acueducto |
| `gauge_value` | decimal (2) | Sí | Entre `gauge_min` y `gauge_max` del tanque, máximo 2 decimales |
| `water_appearance_code` | texto | Sí | Código de `WATER_APPEARANCE`, `1` a 40 (`CATALOG_CODE`) |
| `damage_noticed` | booleano | No | Por defecto `false` |
| `note` | texto | No | 0 a 500, máximo 10 líneas (`NOTE`) |
| `observed_at` | fecha y hora | Sí | ISO-8601 con zona, no más de 5 minutos en el futuro, no más de `max_backdate_days` atrás |

- **Respuesta 201:** `reading` (`id`, `tank_id`, `gauge_value`, `observed_at`, `received_at`, `source` = `MANUAL_APP`, `validation_status`, `rule_set_id`), `issues` (lista de `code`, `severity`, `details`).
- **Respuesta 200:** si `id` ya existe con el mismo contenido, `idempotent_replay: true` y la lectura original.
- **Errores:** `422 GAUGE_OUT_OF_RANGE`, `422 MISSING_TIMESTAMP`, `422 FUTURE_TIMESTAMP`, `422 TOO_OLD`, `409 DUPLICATE_READING`, `404 NOT_FOUND` (tanque).
- **Ejemplo:**

```json
{
  "id": "0190f3a2-1111-7000-8000-000000000001",
  "tank_id": "0190f3a2-2222-7000-8000-000000000001",
  "gauge_value": 2.10,
  "water_appearance_code": "NORMAL",
  "damage_noticed": false,
  "note": "",
  "observed_at": "2026-10-08T18:00:00-05:00"
}
```

### 7.2 `POST /api/v1/readings/batch`

- **Permiso:** `READING_CREATE`. Es la sincronización de la cola offline.
- **Cuerpo:** `sync_batch_id` (UUID del cliente, obligatorio) y `readings` (lista de 1 a 100 objetos con los campos de 7.1).
- **Límite:** cuerpo de 64 KiB.
- **Respuesta 200:** `sync_batch_id`, `items_received`, `items_accepted`, `items_rejected`, y `results` (por cada `id`: `status` = `ACCEPTED`, `FLAGGED` o `REPLAYED`). Una lectura inválida no se guarda: su elemento trae `error` con `code` (`GAUGE_OUT_OF_RANGE`, `MISSING_TIMESTAMP`, `FUTURE_TIMESTAMP` o `TOO_OLD`) y el resto del lote sigue. La app pide corregir antes de reenviar.
- **Errores:** `413 PAYLOAD_TOO_LARGE`, `409 CONFLICT` (`sync_batch_id` existente con otro contenido).
- **Nota:** un error de una lectura no cancela el lote. Cada lectura se evalúa por separado.

### 7.3 `GET /api/v1/readings`

- **Permiso:** `READING_CREATE` o `READING_CORRECT` (verificar).
- **Parámetros:** `tank_id` (obligatorio), `from`, `to` (fechas ISO), `validation_status`, `limit`, `cursor`.
- **Respuesta:** lista de lecturas con su valor efectivo (`effective_gauge_value`) y `correction_count`.

### 7.4 `GET /api/v1/readings/{id}`

- **Permiso:** igual que 7.3.
- **Respuesta:** la lectura, sus `issues`, y el historial de `corrections` (`corrected_gauge_value`, `reason`, `corrected_by`, `created_at`).

### 7.5 `POST /api/v1/readings/{id}/corrections`

- **Permiso:** `READING_CORRECT`. Un `OPERATOR` solo corrige sus propias lecturas.
- **Cuerpo:** `corrected_gauge_value` (decimal 2, dentro del rango), `reason` (texto, 10 a 500).
- **Respuesta 201:** la corrección con `id` y el valor efectivo nuevo. El dato original no cambia.
- **Errores:** `422 GAUGE_OUT_OF_RANGE`, `422 REASON_REQUIRED`, `403 FORBIDDEN` (operador sobre lectura ajena), `404 NOT_FOUND`.
- **Ejemplo:** `{ "corrected_gauge_value": 2.00, "reason": "El fontanero leyó mal la regla; se confirmó con la foto." }`

## 8. Estado del tanque y pronóstico

### 8.1 `GET /api/v1/tanks/{id}/status`

- **Permiso:** `TANK_STATUS_READ`.
- **Respuesta 200:**
  - `level`: `value`, `observed_at`, `hours_ago`.
  - `trend`: `slope_per_day`, `label` (`DOWN`, `STABLE` o `UP`) y `label_es` ("viene bajando", "estable", "sube").
  - `stale`: `is_stale`, `stale_after_hours`.
  - `band`: banda del nivel actual.
  - `forecast`: `run_id`, `is_fallback`, `fallback_reason`, `horizon_days`, `points` (`target_date`, `p10`, `p50`, `p90`).
  - `open_anomalies`: número de anomalías abiertas.
- **Ejemplo:**

```json
{
  "level": { "value": 2.10, "observed_at": "2026-10-08T18:00:00-05:00", "hours_ago": 10.5 },
  "trend": { "slope_per_day": -0.25, "label": "DOWN", "label_es": "viene bajando" },
  "stale": { "is_stale": false, "stale_after_hours": 12 },
  "forecast": { "is_fallback": false, "points": [{ "target_date": "2026-10-09", "p10": 1.80, "p50": 2.10, "p90": 2.40 }] }
}
```

### 8.2 `GET /api/v1/tanks/{id}/forecasts/latest`

- **Permiso:** `TANK_STATUS_READ`.
- **Respuesta:** la última corrida (`run_id`, `model_name`, `model_version`, `is_fallback`, `fallback_reason`, `created_at`, `context_from`, `context_to`) y sus `points`.

### 8.3 `POST /api/v1/tanks/{id}/forecasts`

- **Permiso:** `FORECAST_RUN`.
- **Cuerpo:** `horizon_days` (entero de 1 a 3, opcional; por defecto `forecast_horizon_days` de la regla vigente).
- **Respuesta 201:** corrida nueva con `points` y `is_fallback`. Si la IA no responde o el circuito está abierto, la respuesta es igual, con `is_fallback = true` y `fallback_reason` (`IA_TIMEOUT`, `CIRCUIT_OPEN`, `IA_ERROR` o `INSUFFICIENT_HISTORY`). La estimación simple que se calcula siempre junto a la IA se guarda con `is_fallback = false` y `fallback_reason = SHADOW_BASELINE`; `is_fallback = true` solo cuando reemplaza a la IA.
- **Errores:** `429 RATE_LIMITED` (10 por hora por acueducto), `503 IA_UNAVAILABLE` (solo si también falla la estimación simple).
- **Ejemplo:** `{ "horizon_days": 1 }`

## 9. Anomalías (`/anomalies`)

Las anomalías son sospechas (`INFERRED`) hasta que alguien las confirme.

### 9.1 `GET /api/v1/anomalies`

- **Permiso:** `TANK_STATUS_READ`.
- **Parámetros:** `status` (`OPEN`, `CONFIRMED`, `DISMISSED`), `kind` (`POSSIBLE_LEAK`, `SENSOR_FAULT`, `STALE_DATA`, `SUSPECTED_DUPLICATE`, `ABNORMAL_DROP`), `limit`, `cursor`.
- **Respuesta:** lista de `id`, `kind`, `detected_at`, `status`, `evidence`, `resolved_by`, `resolved_at`, `related_incident_id`.

### 9.2 `PATCH /api/v1/anomalies/{id}`

- **Permiso:** `BOARD_ADMIN` o `BOARD_MEMBER` (verificar).
- **Cuerpo:** `status` (`CONFIRMED` o `DISMISSED`), `resolution_note` (texto, 0 a 500; obligatorio con 10 a 500 caracteres si es `DISMISSED`), `related_incident_id` (UUID, opcional).
- **Respuesta 200:** anomalía actualizada.
- **Errores:** `409 INVALID_STATE_TRANSITION` (no está `OPEN`), `422 REASON_REQUIRED` (descarte sin motivo).
- **Ejemplo:** `{ "status": "DISMISSED", "resolution_note": "Se revisó la válvula; la caída fue por la lluvia y no por fuga." }`

## 10. Propuestas de turnos (`/schedule-proposals`)

La propuesta sigue la máquina de estados de `Reglas-de-negocio.md`, sección 12. Un `lock_version` protege cada transición contra cambios simultáneos.

### 10.1 `POST /api/v1/schedule-proposals`

- **Permiso:** `PROPOSAL_GENERATE`.
- **Cuerpo:** `service_date` (fecha `YYYY-MM-DD`, obligatoria).
- **Respuesta 201:** propuesta con `status` = `PENDING_REVIEW` (`DRAFT` es solo interno mientras se construye), `available_hours`, `tank_level_snapshot`, `tank_band`, `forecast_run_id`, `strategy_code`, `lock_version`, `items` (`id`, `sector_id`, `sequence`, `start_at`, `end_at`, `origin`, `reasons`) y `unserved_sectors` (sectores sin turno con su motivo, guardado en `schedule_proposals.unserved_sectors`).
- **Errores:** `422 NO_LEVEL_DATA`, `404 NOT_FOUND` (no hay regla activa), `409 CONFLICT` (por definir: ya existe una propuesta abierta para la fecha).
- **Ejemplo:** `{ "service_date": "2026-10-09" }`

### 10.2 `GET /api/v1/schedule-proposals`

- **Permiso:** `PROPOSAL_READ`.
- **Parámetros:** `service_date_from`, `service_date_to`, `status`, `limit`, `cursor`.
- **Respuesta:** lista de propuestas (sin `items`).

### 10.3 `GET /api/v1/schedule-proposals/{id}`

- **Permiso:** `PROPOSAL_READ`.
- **Respuesta:** la propuesta completa, como 10.1, con `decisions` (historial de decisiones con motivo) y `snapshot_taken` (si hubo cambios).

### 10.4 `POST /api/v1/schedule-proposals/{id}/approve`

- **Permiso:** `PROPOSAL_DECIDE`.
- **Cuerpo:** `lock_version` (entero, obligatorio).
- **Respuesta 200:** propuesta en `APPROVED`, con `lock_version` incrementado.
- **Errores:** `409 CONFLICT` (`lock_version` distinto), `409 INVALID_STATE_TRANSITION` (no está en `PENDING_REVIEW`).
- **Ejemplo:** `{ "lock_version": 0 }`

### 10.5 `POST /api/v1/schedule-proposals/{id}/modify`

- **Permiso:** `PROPOSAL_DECIDE`.
- **Cuerpo:**
  - `lock_version` (entero, obligatorio).
  - `reason` (texto, 10 a 500, obligatorio).
  - `items` (lista de 1 a 100): `id` (UUID del turno existente, o nulo para un turno nuevo), `sector_id` (UUID), `start_at`, `end_at` (fecha y hora, `end_at > start_at`), `sequence` (entero mayor o igual a 0).
- **Respuesta 200:** propuesta en `APPROVED_WITH_CHANGES`. Antes del cambio se guarda `proposal_snapshots`. Los turnos cambiados llevan `origin` `MODIFIED`; los nuevos, `ADDED`.
- **Errores:** `422 REASON_REQUIRED`, `422 HOURS_EXCEEDED`, `422 SHIFT_LENGTH_INVALID`, `422 OUTSIDE_OPERATING_WINDOW`, `409 SCHEDULE_OVERLAP`, `409 CONFLICT`, `409 INVALID_STATE_TRANSITION`.
- **Ejemplo:**

```json
{
  "lock_version": 0,
  "reason": "La escuela tiene clase especial; se corre su turno a las 07:00.",
  "items": [
    { "id": "0190f3a2-3333-7000-8000-000000000001", "sector_id": "0190f3a2-4444-7000-8000-000000000001", "start_at": "2026-10-09T07:00:00-05:00", "end_at": "2026-10-09T10:00:00-05:00", "sequence": 1 }
  ]
}
```

### 10.6 `POST /api/v1/schedule-proposals/{id}/reject`

- **Permiso:** `PROPOSAL_DECIDE`.
- **Cuerpo:** `lock_version`, `reason` (10 a 500, obligatorio).
- **Respuesta 200:** propuesta en `REJECTED` (estado final).
- **Errores:** `422 REASON_REQUIRED`, `409 CONFLICT`, `409 INVALID_STATE_TRANSITION`.

### 10.7 `POST /api/v1/schedule-proposals/{id}/publish`

- **Permiso:** `PROPOSAL_PUBLISH`.
- **Cuerpo:** `lock_version` (entero, obligatorio).
- **Respuesta 201:** `publication` con `id`, `published_at`, `whatsapp_text`, `poster_sha256`. La propuesta pasa a `PUBLISHED`.
- **Errores:** `409 INVALID_STATE_TRANSITION` (no está aprobada), `409 CONFLICT`.
- **Nota:** la publicación nunca incluye datos personales ni motivos internos. Ver `Reglas-de-negocio.md`, sección 13.

## 11. Ejecución de turnos y cierre del día

### 11.1 `POST /api/v1/schedule-items/{id}/execution`

- **Permiso:** `EXECUTION_RECORD`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `status` | texto | Sí | `COMPLETED`, `PARTIAL` o `NOT_EXECUTED` |
| `actual_start` | fecha y hora | No | Requerido en `PARTIAL` (propuesta) |
| `actual_end` | fecha y hora | No | `actual_end > actual_start` |
| `note` | texto | No | 0 a 500, máximo 10 líneas (`NOTE`) |
| `supersedes_id` | UUID | No | Ejecución anterior que esta reemplaza |

- **Respuesta 201:** ejecución registrada. Una ejecución nunca se edita; un cambio es una fila nueva con `supersedes_id`.
- **Errores:** `400 VALIDATION_ERROR` (`actual_end <= actual_start`), `409 INVALID_STATE_TRANSITION` (propuesta no publicada o día cerrado), `409 CONFLICT` (ya reemplazada).
- **Ejemplo:** `{ "status": "PARTIAL", "actual_start": "2026-10-09T08:00:00-05:00", "actual_end": "2026-10-09T09:30:00-05:00", "note": "Se cortó el agua a las 09:30 por falla de la bomba." }`

### 11.2 `GET /api/v1/days/{date}/closure`

- **Permiso:** `EXECUTION_RECORD` o `DAY_CLOSE`.
- **Respuesta 200:** cierre (`id`, `service_date`, `notes`, `closed_by`, `closed_at`) y `executions` del día.
- **Errores:** `404 NOT_FOUND` (día sin cierre).

### 11.3 `POST /api/v1/days/{date}/closure`

- **Permiso:** `DAY_CLOSE`.
- **Cuerpo:** `notes` (texto, 0 a 500, máximo 10 líneas).
- **Respuesta 201:** cierre creado.
- **Errores:** `409 INVALID_STATE_TRANSITION` (no hay propuesta `PUBLISHED`), `409 CONFLICT` (día ya cerrado).
- **Ejemplo:** `{ "notes": "Día sin novedades mayores. Un turno a medias por la lluvia." }`

## 12. Incidentes (`/incidents`)

### 12.1 `GET /api/v1/incidents`

- **Permiso:** `INCIDENT_READ`.
- **Parámetros:** `status`, `source` (`PUBLIC_FORM`, `OPERATOR`, `ANOMALY`, `IMPORT`), `sector_id`, `from`, `to`, `limit`, `cursor`.
- **Respuesta:** lista de `id`, `source`, `category_code`, `sector_id`, `description`, `location_hint`, `status`, `reported_at`. Sin datos del reportante: el HMAC de la IP nunca sale de la base.

### 12.2 `POST /api/v1/incidents`

- **Permiso:** `INCIDENT_CREATE`. Crea con `source = OPERATOR`. Las anomalías crean incidentes por sistema y la importación con su propio origen.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `category_code` | texto | Sí | Código de `DAMAGE_CATEGORY` |
| `sector_id` | UUID | No | Sector del acueducto |
| `description` | texto | Sí | 10 a 500 (`INCIDENT_DESCRIPTION`) |
| `location_hint` | texto | No | 0 a 120, una línea (`LOCATION_HINT`) |

- **Respuesta 201:** incidente con `status = REPORTED`.
- **Ejemplo:** `{ "category_code": "LEAK", "description": "Gotea el tubo junto a la escuela.", "location_hint": "Frente a la escuela" }`

### 12.3 `PATCH /api/v1/incidents/{id}/status`

- **Permiso:** `INCIDENT_CREATE` (quien puede registrar también puede cambiar su estado; verificar).
- **Cuerpo:** `status` (`VERIFYING`, `CONFIRMED`, `RESOLVED` o `DISMISSED`), `note` (texto, 0 a 500; de 10 a 500 si el estado es `DISMISSED`).
- **Respuesta 200:** incidente actualizado. Se agrega una fila a `incident_status_history`.
- **Errores:** `409 INVALID_STATE_TRANSITION` (transición no permitida), `422 REASON_REQUIRED` (descarte sin nota).
- **Ejemplo:** `{ "status": "CONFIRMED", "note": "Se verificó en campo el 9 de octubre." }`

## 13. Actas y resúmenes para entidades

### 13.1 `POST /api/v1/minutes`

- **Permiso:** `MINUTES_GENERATE`.
- **Cuerpo:** `period_from`, `period_to` (fechas, `period_to >= period_from`).
- **Respuesta 201:** acta en `DRAFT` con `content` (secciones `observed`, `estimated`, `inferred`, `confirmed`).
- **Errores:** `400 VALIDATION_ERROR` (periodo invertido).
- **Cierre:** el acta se cierra como `FINAL` con `POST /api/v1/minutes/{id}/finalize` (sección 13.7).

### 13.2 `GET /api/v1/minutes/{id}`

- **Permiso:** `MINUTES_READ`.
- **Respuesta:** acta con `status`, `period_from`, `period_to`, `content`, `generated_by`, `generated_at`, `pdf_sha256` y `audit_head_hash` (si está `FINAL`).

### 13.3 `GET /api/v1/minutes/{id}/pdf`

- **Permiso:** `MINUTES_READ`.
- **Respuesta 200:** `application/pdf`. Un acta en `DRAFT` lleva la marca "BORRADOR". Cada descarga escribe en `audit.data_access_log` con `resource = MINUTES_PDF`.
- **Errores:** `404 NOT_FOUND`.

### 13.4 `POST /api/v1/summary-shares`

- **Permiso:** `SUMMARY_SHARE_MANAGE` (solo `BOARD_ADMIN`).
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `grantee_user_id` | UUID | Sí | Usuario con rol `SUPPORT_ENTITY` |
| `scope` | texto | Sí | `MONTHLY_SUMMARY` o `MINUTES` |
| `period_from`, `period_to` | fechas | Sí | `period_to >= period_from` |
| `reason` | texto | Sí | 10 a 500 (`REASON`) |
| `expires_at` | fecha y hora | Sí | Obligatorio, en el futuro. Límite máximo: por definir. |

- **Respuesta 201:** autorización creada.
- **Errores:** `422 REASON_REQUIRED` (motivo ausente o corto), `400 VALIDATION_ERROR` (`grantee_user_id` que no es de rol `SUPPORT_ENTITY`, o `expires_at` en el pasado).
- **Ejemplo:** `{ "grantee_user_id": "0190f3a2-5555-7000-8000-000000000001", "scope": "MONTHLY_SUMMARY", "period_from": "2026-10-01", "period_to": "2026-10-31", "reason": "Informe para la alcaldía municipal.", "expires_at": "2026-11-30T23:59:00-05:00" }`

### 13.5 `DELETE /api/v1/summary-shares/{id}`

- **Permiso:** `SUMMARY_SHARE_MANAGE`.
- **Respuesta 204.** Pone `revoked_at` y `revoked_by`. No borra la fila.
- **Errores:** `409 CONFLICT` (ya revocada).

### 13.6 `GET /api/v1/summaries`

- **Permiso:** `SUMMARY_READ`. Para `SUPPORT_ENTITY`, solo lista y permite leer las autorizaciones vigentes (ver `Reglas-de-negocio.md`, sección 16.3).
- **Parámetros:** `period_from`, `period_to`, `limit`, `cursor`.
- **Respuesta:** lista de resúmenes con `scope`, `period_from`, `period_to`, `content` (secciones) y `summary_share_id`.
- **Nota:** cada consulta de una entidad escribe en `audit.data_access_log`.

### 13.7 `POST /api/v1/minutes/{id}/finalize`

- **Permiso:** `MINUTES_GENERATE`. Sin cuerpo.
- **Respuesta 200:** acta en `FINAL` (transición `DRAFT` → `FINAL`), con `finalized_by`, `finalized_at` y `pdf_sha256`. Ya no lleva la marca "BORRADOR".
- **Errores:** `409 INVALID_STATE_TRANSITION` (el acta no está en `DRAFT`), `404 NOT_FOUND`.

## 14. Evaluación de la IA

### 14.1 `GET /api/v1/forecast-evaluation`

- **Permiso:** `EVALUATION_READ`.
- **Parámetros:** `horizon_days` (1 a 3), `from`, `to`.
- **Respuesta 200:**

| Campo | Significado |
|---|---|
| `n` | Número de pronósticos evaluados |
| `mae_ia`, `mae_simple` | Error absoluto medio del p50 |
| `wql_ia` | Pérdida cuantílica ponderada |
| `coverage_80` | Proporción de reales dentro de [p10, p90] (meta cercana a 80 %) |
| `avg_interval_width` | Ancho medio del intervalo |
| `skill` | `1 - MAE_ia / MAE_simple` |
| `helps` | `true` si `skill > 0` de forma sostenida, con al menos 30 pronósticos y cobertura entre 70 % y 90 % |

- **Ejemplo:** `{ "horizon_days": 1, "n": 34, "skill": 0.12, "coverage_80": 0.78, "helps": true }` (valores ficticios)

## 15. Público (sin login)

Estas rutas no requieren autenticación. Solo muestran datos públicos y no revelan nombres ni teléfonos. El proxy `PublicScheduleProxy` aplica la protección antes de responder.

### 15.1 `GET /api/v1/public/{aqueductSlug}/schedule`

- **Parámetros:** `date` (opcional, por defecto hoy en `America/Bogota`).
- **Respuesta 200:**
  - `aqueduct`: `name`, `is_demo`.
  - `service_date`.
  - `publication`: `id`, `published_at`.
  - `items`: `sector_name`, `start_at`, `end_at`.
  - Si `is_demo = true`, el banner "Datos simulados" se incluye en `aqueduct.banner`.
- **Errores:** `404 NOT_FOUND` (slug inexistente, página desactivada o sin publicación para la fecha).

### 15.2 `GET /api/v1/public/{aqueductSlug}/schedule/whatsapp-text`

- **Respuesta 200:** `text/plain; charset=utf-8` con el mensaje de `publications.whatsapp_text`. Sin datos personales.
- **Errores:** `404 NOT_FOUND`.

### 15.3 `GET /api/v1/public/{aqueductSlug}/schedule/poster.pdf`

- **Respuesta 200:** `application/pdf` del cartel. La cabecera `X-Content-SHA256` trae el hash que se guardó en `poster_sha256` (propuesta).
- **Errores:** `404 NOT_FOUND`.

### 15.4 `POST /api/v1/public/{aqueductSlug}/damage-reports`

- **Límite:** 3 por hora por IP. Honeypot y tiempo mínimo de llenado.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `category_code` | texto | Sí | Código de `DAMAGE_CATEGORY` |
| `sector_id` | UUID | No | Sector del acueducto |
| `description` | texto | Sí | 10 a 500 (`INCIDENT_DESCRIPTION`) |
| `location_hint` | texto | No | 0 a 120 (`LOCATION_HINT`) |
| `website` | texto | No | Campo trampa oculto. Debe llegar vacío. |
| `form_started_at` | fecha y hora | Sí | Al menos `PUBLIC_FORM_MIN_SECONDS` (3 s por defecto) antes del envío |

- **Respuesta 201:** `tracking_code` (8 caracteres Crockford base32, se muestra una sola vez) y `status = REPORTED`.
- **Errores:** `400 VALIDATION_ERROR` (tiempo mínimo no cumplido), `404 NOT_FOUND` (slug), `429 RATE_LIMITED`. Si el campo `website` llega con valor, la API responde `201` genérico, no guarda nada y registra `HONEYPOT_TRIGGERED` sin revelarlo.
- **Ejemplo:** `{ "category_code": "LEAK", "description": "Hay agua saliendo del tubo de la calle principal.", "location_hint": "Esquina de la tienda", "website": "", "form_started_at": "2026-10-09T09:00:00-05:00" }`

### 15.5 `GET /api/v1/public/damage-reports/{trackingCode}`

- **Respuesta 200:** `status`, `category_label_es`, `reported_at`, `updated_at`. No devuelve datos del reportante ni la IP.
- **Errores:** `400 VALIDATION_ERROR` (formato de código inválido), `404 NOT_FOUND` (código no existe; mensaje genérico).

## 16. Dispositivos (firmados)

Las rutas de telemetría y comandos no usan JWT. Cada petición lleva firma Ed25519. El protocolo completo, con la cadena canónica, la validación en orden y los vectores de prueba, está en `Protocolo-de-dispositivos.md`.

### 16.1 `POST /api/v1/devices/telemetry`

- **Autenticación:** firma del dispositivo (`X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`).
- **Límite:** 256 KiB de cuerpo, 60 peticiones por minuto por dispositivo.
- **Cuerpo:** `points` (lista de 1 a 100):

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `id` | UUID | Sí | Generado por el dispositivo (idempotencia) |
| `metric` | texto | Sí | `LEVEL`, `BATTERY_V`, `RSSI` o `VALVE_POSITION` |
| `value` | decimal (3) | Sí | Número finito, hasta 3 decimales. Para `LEVEL`: hasta 2 decimales (ver protocolo). |
| `observed_at` | fecha y hora | Sí | ISO-8601 con zona |

- **Respuesta 200:** `accepted`, `duplicates`, `rejected` y `results` (por `id`: `ACCEPTED` o `DUPLICATE`, con `reading_id` cuando generó lectura). Un punto inválido (por ejemplo, `LEVEL` fuera del rango del tanque) no se guarda: su elemento trae `error` con `code`, igual que en las lecturas.
- **Errores:** `401 INVALID_SIGNATURE`, `401 NONCE_REPLAY`, `401 TIMESTAMP_OUT_OF_WINDOW`, `403 DEVICE_INACTIVE`, `413 PAYLOAD_TOO_LARGE`, `400 VALIDATION_ERROR`, `429 RATE_LIMITED`.
- **Ejemplo:**

```json
{
  "points": [
    { "id": "0190f3a2-6666-7000-8000-000000000001", "metric": "LEVEL", "value": 2.10, "observed_at": "2026-10-08T18:00:00-05:00" },
    { "id": "0190f3a2-6666-7000-8000-000000000002", "metric": "BATTERY_V", "value": 12.4, "observed_at": "2026-10-08T18:00:00-05:00" }
  ]
}
```

### 16.2 `GET /api/v1/devices/commands`

- **Autenticación:** firma del dispositivo. Cuerpo vacío.
- **Respuesta 200:** `items` (hasta 100) con `id`, `valve_id`, `command` (`OPEN` o `CLOSE`), `not_before`, `expires_at`. Solo comandos `PENDING` cuyo `not_before` ya llegó y que no vencieron. Al entregarse pasan a `DELIVERED` y se registra el evento.
- **Errores:** las mismas firmas que 16.1, `403 DEVICE_INACTIVE`.

### 16.3 `POST /api/v1/devices/commands/{id}/ack`

- **Autenticación:** firma del dispositivo.
- **Cuerpo:** `result` (`ACKED` o `FAILED`, obligatorio) y `detail` (texto, 0 a 200).
- **Respuesta 200:** comando con su nuevo `status`. Es idempotente: confirmar dos veces el mismo comando responde igual.
- **Errores:** `409 INVALID_STATE_TRANSITION` (comando `EXPIRED` o `CANCELLED`), `404 NOT_FOUND` (comando de otro dispositivo).
- **Ejemplo:** `{ "result": "ACKED", "detail": "Válvula abierta; posición confirmada." }`

### 16.4 `POST /api/v1/devices`

- **Permiso:** `DEVICE_REGISTER`.
- **Cuerpo:**

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `kind` | texto | Sí | `LEVEL_SENSOR`, `VALVE_ACTUATOR` o `GATEWAY` |
| `name` | texto | Sí | 2 a 60 (`PLACE_NAME`) |
| `tank_id` | UUID | Según `kind` | Obligatorio para `LEVEL_SENSOR` |
| `valve_id` | UUID | Según `kind` | Obligatorio para `VALVE_ACTUATOR` |
| `firmware_version` | texto | No | Hasta 30 |

- **Respuesta 201:** dispositivo con `status = ACTIVE`. Todavía sin clave: no acepta firmas hasta registrar una.

### 16.5 `POST /api/v1/devices/{id}/keys`

- **Permiso:** `DEVICE_REGISTER`.
- **Cuerpo:** `public_key` (texto, base64url sin relleno, 43 caracteres que decodifican a 32 bytes).
- **Respuesta 201:** `key_id`, `fingerprint` (SHA-256 de la clave en hexadecimal, 64 caracteres) y `activated_at`. La clave anterior, si existía, sigue válida 7 días (configurable) y después queda revocada. Para revocarla antes, ver `DELETE /devices/{id}/keys/{keyId}`.
- **Errores:** `422 INVALID_KEY`, `409 CONFLICT` (la misma clave ya está registrada).

### 16.7 `PATCH /api/v1/devices/{id}`

- **Permiso:** `DEVICE_REGISTER`.
- **Cuerpo:** `status` (`ACTIVE`, `SUSPENDED` o `RETIRED`, obligatorio).
- **Respuesta 200:** dispositivo actualizado. Un dispositivo `SUSPENDED` o `RETIRED` no acepta telemetría ni entrega comandos (`403 DEVICE_INACTIVE`).
- **Ejemplo:** `{ "status": "SUSPENDED" }`

### 16.8 `DELETE /api/v1/devices/{id}/keys/{keyId}`

- **Permiso:** `DEVICE_REGISTER`.
- **Respuesta 204.** Revoca la clave: hace `UPDATE` de `revoked_at`, no borra la fila. Desde ese momento las firmas con esa clave dejan de aceptarse.
- **Errores:** `404 NOT_FOUND`, `409 CONFLICT` (la clave ya estaba revocada).

### 16.6 `POST /api/v1/valve-commands/manual`

- **Permiso:** `VALVE_COMMAND_MANUAL` (solo `BOARD_ADMIN`).
- **Cuerpo:** `valve_id` (UUID), `command` (`OPEN` o `CLOSE`), `manual_reason` (texto, 10 a 500, obligatorio), `not_before` (fecha y hora), `expires_at` (mayor que `not_before`).
- **Respuesta 201:** comando en `PENDING`.
- **Errores:** `422 REASON_REQUIRED`, `400 VALIDATION_ERROR` (`expires_at <= not_before`), `404 NOT_FOUND` (válvula sin actuador).
- **Ejemplo:** `{ "valve_id": "0190f3a2-7777-7000-8000-000000000001", "command": "CLOSE", "manual_reason": "Fuga visible en el tubo; cierre preventivo.", "not_before": "2026-10-09T10:00:00-05:00", "expires_at": "2026-10-09T10:05:00-05:00" }`

## 17. Importación de historial simulado (`/imports`)

Solo `PROJECT_TEAM` y solo para acueductos con `is_demo = true`. Cualquier otro acueducto responde `403 DEMO_ONLY`. Cada importación crea una fila en `sim.import_batches`.

Límites: cuerpo de 1 MiB o 5.000 filas por lote (lo que llegue primero). Más filas: `422 IMPORT_LIMIT_EXCEEDED`. La importación puede enviar fechas anteriores a `max_backdate_days` (excepción de la regla de antigüedad).

Campos comunes del cuerpo:

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `aqueduct_id` | UUID | Sí | Debe ser `is_demo = true` |
| `scenario_name` | texto | Sí | 1 a 60 |
| `seed` | entero (64 bits) | Sí | Semilla del simulador |
| `simulator_version` | texto | Sí | 1 a 30 |
| `file_sha256` | texto | Sí | 64 caracteres hexadecimales. Repetido: `409 CONFLICT`. |
| `rows` | lista | Sí | 1 a 5.000 |

Respuesta 201 común: `import_batch_id`, `rows_received`, `rows_accepted`, `rows_rejected` y `rejections` (lista de `row`, `code`).

### 17.1 `POST /api/v1/imports/readings`

- Cada fila tiene los campos de la lectura (7.1) sin `source`, que se fija en `IMPORT`. Las lecturas quedan con `import_batch_id`.

### 17.2 `POST /api/v1/imports/shift-executions`

- Cada fila: `schedule_item_id` (UUID existente), `status`, `actual_start`, `actual_end`, `note`. Quedan con `import_batch_id`.

### 17.3 `POST /api/v1/imports/incidents`

- Cada fila: `category_code`, `sector_id`, `description`, `location_hint`, `reported_at`. Se guardan con `source = IMPORT`.

## 18. Auditoría y seguridad

### 18.1 `GET /api/v1/audit-log`

- **Permiso:** `AUDIT_READ` (`BOARD_ADMIN`, `PROJECT_TEAM`).
- **Parámetros:** `from`, `to`, `action` (por ejemplo `RULESET_ACTIVATED`), `entity_type`, `actor_id`, `limit`, `cursor`.
- **Respuesta:** lista de `id`, `occurred_at`, `actor_type`, `actor_id`, `action`, `entity_type`, `entity_id`, `before_state`, `after_state` (redactados), `prev_hash`, `row_hash`.
- **Nota:** cada consulta escribe en `audit.data_access_log` con `resource = AUDIT_LOG`.

### 18.2 `GET /api/v1/security-events`

- **Permiso:** `AUDIT_READ` (verificar).
- **Parámetros:** `type` (`ACCOUNT_LOCKED`, `TOKEN_REUSE_DETECTED`, `PERMISSION_DENIED`, `RATE_LIMITED`, `INVALID_SIGNATURE`, `NONCE_REPLAY`, `HONEYPOT_TRIGGERED`, `IMPORT_REJECTED`, `MFA_FAILED`), `severity`, `from`, `to`, `limit`, `cursor`.
- **Respuesta:** lista de `id`, `type`, `severity`, `actor_user_id`, `device_id`, `request_id`, `created_at`, `details` (sin secretos).

### 18.3 `PUT /api/v1/retention-policies/{data_class}`

- **Permiso:** `PROJECT_TEAM` (`RETENTION_MANAGE`, propuesta).
- **Parámetro de ruta:** `data_class` = `LOGIN_ATTEMPTS`, `DEVICE_NONCES`, `SECURITY_EVENTS`, `REFRESH_TOKENS`, `DATA_ACCESS_LOG` o `TELEMETRY_POINTS`. Otro valor: `400 VALIDATION_ERROR`.
- **Cuerpo:** `retention_days` (entero, mayor que 0).
- **Respuesta 200:** política con `data_class`, `retention_days`, `updated_by` y `updated_at`.
- **Valores de la semilla:** `LOGIN_ATTEMPTS` 90 días, `DEVICE_NONCES` 1 día, `SECURITY_EVENTS` 365, `REFRESH_TOKENS` 30 días después de expirar, `DATA_ACCESS_LOG` 365, `TELEMETRY_POINTS` 730.
- **Ejemplo:** `{ "retention_days": 365 }`

## 19. Salud

### 19.1 `GET /actuator/health`

- **Permiso:** público.
- **Respuesta 200:** `{"status": "UP"}`. No muestra detalles de base de datos, IA ni disco.
- **Nota:** la sonda de la plataforma usa este endpoint. Los detalles internos quedan solo en logs.

## 20. Lista de endpoints

| Recurso | Método | Ruta | Permiso |
|---|---|---|---|
| Auth | POST | `/auth/login` | Público |
| Auth | POST | `/auth/refresh` | Cookie |
| Auth | POST | `/auth/logout` | Autenticado |
| Auth | POST | `/auth/logout-all` | Autenticado |
| Auth | POST | `/auth/change-password` | Autenticado |
| Auth | POST | `/auth/reset-password` | Público (código) |
| Auth | GET | `/auth/me` | Autenticado |
| Auth | POST | `/auth/mfa/*` | Por definir |
| Auth | POST | `/auth/switch-aqueduct` | Autenticado |
| Usuarios | GET, POST | `/users` | `USER_MANAGE` |
| Usuarios | PATCH | `/users/{id}` | `USER_MANAGE` |
| Usuarios | POST | `/users/{id}/reset-grants` | `USER_MANAGE` |
| Membresías | GET, POST | `/memberships` | `MEMBERSHIP_MANAGE` |
| Membresías | DELETE | `/memberships/{id}` | `MEMBERSHIP_MANAGE` |
| Meta | GET | `/meta/constraints` | Público |
| Catálogos | GET | `/catalogs/{catalog}` | Público (solo etiquetas) |
| Catálogos | POST, PATCH | `/catalogs/{catalog}/items` | `PROJECT_TEAM` |
| Privacidad | GET | `/privacy-notice/current` | Autenticado |
| Privacidad | POST | `/privacy-notice/accept` | Autenticado |
| Red | GET, POST | `/tanks` | Autenticado / `BOARD_ADMIN` |
| Red | GET, POST | `/sectors` | Autenticado / `BOARD_ADMIN` |
| Red | PATCH | `/sectors/{id}` | `BOARD_ADMIN` |
| Red | GET, POST | `/sectors/{id}/valves` | Autenticado / `BOARD_ADMIN` |
| Red | PATCH | `/valves/{id}` | `BOARD_ADMIN` |
| Reglas | GET | `/rule-sets/current` | Autenticado |
| Reglas | GET | `/rule-sets`, `/rule-sets/{id}` | `RULESET_PROPOSE` o `RULESET_EDIT` |
| Reglas | POST | `/rule-sets` | `RULESET_PROPOSE` |
| Reglas | PATCH | `/rule-sets/{id}` | `RULESET_EDIT` |
| Reglas | POST | `/rule-sets/{id}/activate` | `RULESET_ACTIVATE` |
| Lecturas | POST | `/readings` | `READING_CREATE` |
| Lecturas | POST | `/readings/batch` | `READING_CREATE` |
| Lecturas | GET | `/readings`, `/readings/{id}` | Lectura (verificar) |
| Lecturas | POST | `/readings/{id}/corrections` | `READING_CORRECT` |
| Estado | GET | `/tanks/{id}/status` | `TANK_STATUS_READ` |
| Estado | GET | `/tanks/{id}/forecasts/latest` | `TANK_STATUS_READ` |
| Estado | POST | `/tanks/{id}/forecasts` | `FORECAST_RUN` |
| Anomalías | GET | `/anomalies` | `TANK_STATUS_READ` |
| Anomalías | PATCH | `/anomalies/{id}` | Junta (verificar) |
| Propuestas | POST | `/schedule-proposals` | `PROPOSAL_GENERATE` |
| Propuestas | GET | `/schedule-proposals`, `/schedule-proposals/{id}` | `PROPOSAL_READ` |
| Propuestas | POST | `/schedule-proposals/{id}/approve`, `/modify`, `/reject` | `PROPOSAL_DECIDE` |
| Propuestas | POST | `/schedule-proposals/{id}/publish` | `PROPOSAL_PUBLISH` |
| Ejecución | POST | `/schedule-items/{id}/execution` | `EXECUTION_RECORD` |
| Cierre | GET | `/days/{date}/closure` | `EXECUTION_RECORD` o `DAY_CLOSE` |
| Cierre | POST | `/days/{date}/closure` | `DAY_CLOSE` |
| Incidentes | GET | `/incidents` | `INCIDENT_READ` |
| Incidentes | POST | `/incidents` | `INCIDENT_CREATE` |
| Incidentes | PATCH | `/incidents/{id}/status` | `INCIDENT_CREATE` (verificar) |
| Actas | POST | `/minutes` | `MINUTES_GENERATE` |
| Actas | POST | `/minutes/{id}/finalize` | `MINUTES_GENERATE` |
| Actas | GET | `/minutes/{id}`, `/minutes/{id}/pdf` | `MINUTES_READ` |
| Entidades | POST | `/summary-shares` | `SUMMARY_SHARE_MANAGE` |
| Entidades | DELETE | `/summary-shares/{id}` | `SUMMARY_SHARE_MANAGE` |
| Entidades | GET | `/summaries` | `SUMMARY_READ` |
| Evaluación | GET | `/forecast-evaluation` | `EVALUATION_READ` |
| Público | GET | `/public/{slug}/schedule` | Público |
| Público | GET | `/public/{slug}/schedule/whatsapp-text` | Público |
| Público | GET | `/public/{slug}/schedule/poster.pdf` | Público |
| Público | POST | `/public/{slug}/damage-reports` | Público (límite) |
| Público | GET | `/public/damage-reports/{trackingCode}` | Público |
| Dispositivos | POST | `/devices/telemetry` | Firma |
| Dispositivos | GET | `/devices/commands` | Firma |
| Dispositivos | POST | `/devices/commands/{id}/ack` | Firma |
| Dispositivos | POST | `/devices` | `DEVICE_REGISTER` |
| Dispositivos | POST | `/devices/{id}/keys` | `DEVICE_REGISTER` |
| Dispositivos | PATCH | `/devices/{id}` | `DEVICE_REGISTER` |
| Dispositivos | DELETE | `/devices/{id}/keys/{keyId}` | `DEVICE_REGISTER` |
| Válvulas | POST | `/valve-commands/manual` | `VALVE_COMMAND_MANUAL` |
| Importación | POST | `/imports/readings`, `/imports/shift-executions`, `/imports/incidents` | `IMPORT_RUN` (solo demo) |
| Auditoría | GET | `/audit-log`, `/security-events` | `AUDIT_READ` |
| Retención | PUT | `/retention-policies/{data_class}` | `PROJECT_TEAM` |
| Salud | GET | `/actuator/health` | Público |

Relacionados: [Reglas-de-negocio.md](Reglas-de-negocio.md), [Protocolo-de-dispositivos.md](Protocolo-de-dispositivos.md), [Contrato-IA.md](Contrato-IA.md), [Diccionario-de-datos.md](Diccionario-de-datos.md)
