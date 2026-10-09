# Validación de entradas: tabla canónica

Este documento es la referencia de validación de la API de `caudal-backend`. Cada campo de cada endpoint de la sección 13 de los hechos canónicos aparece aquí, con su tipo, sus límites, su normalización, la constante de `FieldLimits` que lo gobierna y el código de error que devuelve. Si un DTO contradice esta tabla, gana la tabla y se corrige el DTO (o se actualiza esta tabla con una decisión registrada).

Los campos marcados como **propuesta** o **por definir** no tienen valor cerrado en los hechos canónicos. Antes de implementarlos hay que confirmarlos con el equipo de backend (Drako Salazar).

Documentos relacionados: [`docs/Seguridad.md`](../docs/Seguridad.md) (resumen de controles), [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md) (de dónde sale cada límite) y [`.agents/security.md`](security.md) (clases que aplican estas reglas).

## 1. Reglas globales

| Regla | Valor | Dónde se aplica |
|---|---|---|
| Tamaño del cuerpo JSON (general) | 64 KiB (65.536 bytes) | `RequestSizeLimitFilter`, antes de parsear |
| Tamaño del cuerpo de telemetría | 256 KiB (262.144 bytes) | `RequestSizeLimitFilter` por ruta |
| Tamaño del cuerpo de importación | 1 MiB (1.048.576 bytes) y 5.000 filas por lote | `RequestSizeLimitFilter` y validación de lote |
| Tamaño de la cabecera `Authorization` | 2 KiB (2.048 bytes); se rechaza antes de parsear el JWT | Filtro de seguridad, antes de Spring Security |
| Profundidad JSON | 10 niveles como máximo | Parser (Jackson) configurado con límite de profundidad |
| Listas | 100 elementos como máximo, salvo que el campo diga otra cosa | Bean Validation (`@Size(max = FieldLimits.LIST_MAX_ITEMS)`) |
| Tope global de texto | 2.000 caracteres por campo de texto, aunque el campo tenga un máximo mayor | Validador común `SafeTextValidator` |
| Campos desconocidos | Se rechazan (`VALIDATION_ERROR`) | Jackson con `FAIL_ON_UNKNOWN_PROPERTIES` |
| Coerción de tipos | Ninguna: `"2.1"` no es número, `"false"` no es booleano, `1` no es texto | Jackson sin `ACCEPT_*_FROM_STRING`, con `ALLOW_COERCION_OF_SCALARS` desactivado |
| Claves JSON duplicadas | Se rechazan | Detección estricta de duplicados del parser (verificar la opción en Jackson de Spring Boot 4.1) |
| Números | Finitos; sin `NaN`, `Infinity` ni notación que pierda precisión | Parser con `BigDecimal` para decimales |
| Normalización de texto general | NFC; CRLF se convierte en LF; recorte de espacios al inicio y al final | `SafeText` |
| Normalización de contraseñas | NFKC, sin recortar | `Argon2PasswordEncoder` y `PasswordPolicy` |
| Normalización de nombres | NFC, recorte y colapso de espacios internos a uno solo | `SafeText` con opción `collapseSpaces` |
| Caracteres de control | Se rechazan U+0000 a U+001F y U+007F a U+009F. Excepción: U+000A (salto de línea) en textos largos (notas, descripciones) | `SafeTextValidator` |
| Caracteres bidi | Se rechazan U+202A a U+202E y U+2066 a U+2069 | `SafeTextValidator` |
| Caracteres de ancho cero | Se rechazan U+200B a U+200F y U+FEFF | `SafeTextValidator` |
| Separadores de línea | Se rechazan U+2028 y U+2029 en todos los campos (propuesta, verificar) | `SafeTextValidator` |
| Identificadores | UUID canónico (8-4-4-4-12, hexadecimal). Se normaliza a minúsculas | Conversor de path y DTO |
| Fechas y horas | ISO-8601 con zona obligatoria para instantes (`2026-10-09T14:30:00-05:00` o `Z`). `YYYY-MM-DD` para días | Jackson con `Instant` y `LocalDate` |
| Instantes futuros | No más de 5 minutos (300 s) en el futuro: `FUTURE_TIMESTAMP` | Validador de dominio (cadena de lecturas) |
| Instantes pasados | No más de `max_backdate_days` (de la regla vigente): `TOO_OLD`. Excepto importación en acueducto demo | Validador de dominio |
| Paginación | `limit` de 1 a 100, por defecto 20. Cursor opaco firmado | `PageRequest` |
| Enumeraciones | Coincidencia exacta, sensible a mayúsculas. No se aceptan alias | Enums de Java y `@Pattern` o `@EnumValue` |
| Booleanos | Solo `true` o `false` de JSON | Jackson |
| Errores de sintaxis en path | UUID inválido en la ruta: `VALIDATION_ERROR` (400) | Conversor |

## 2. Códigos de error de validación

Esta lista es la única fuente de códigos de validación. Los demás documentos la referencian.

| Código | HTTP | Cuándo | Ejemplo |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Entrada inválida: JSON inválido, tipo incorrecto, campo desconocido, claves duplicadas, profundidad excedida, UUID inválido en la ruta, o uno o más campos que no cumplen su regla. El detalle lista cada fallo | `"gauge_value": "2.1"` |
| `PAYLOAD_TOO_LARGE` | 413 | El cuerpo excede el límite de su ruta | Cuerpo de 100 KiB en `POST /readings` |
| `CURSOR_INVALID` | 400 | Cursor manipulado, expirado o de otro recurso | `cursor=abc` |
| `DUPLICATE_READING` | 409 | Mismo UUID de cliente con contenido distinto | Dos lecturas con el mismo `id` y valores distintos |
| `PASSWORD_POLICY_VIOLATION` | 422 | La contraseña no cumple la política | Contraseña en la lista de comunes |
| `INVALID_CREDENTIALS` | 401 | Usuario o contraseña incorrectos (mensaje genérico) | Usuario con formato inválido en el login |

Los códigos de nivel de campo viajan dentro de `error.details` (no como código principal). Todo error de entrada responde `400 VALIDATION_ERROR`:

| Código de detalle | Significado |
|---|---|
| `FIELD_REQUIRED` | Campo obligatorio ausente o nulo |
| `FIELD_TOO_SHORT` | Menor que el mínimo (después de normalizar) |
| `FIELD_TOO_LONG` | Mayor que el máximo (después de normalizar) |
| `FIELD_FORMAT_INVALID` | No cumple el patrón, el formato de fecha o de UUID, ni el número de decimales |
| `FIELD_CHARACTERS_NOT_ALLOWED` | Contiene caracteres prohibidos (sección 1) |
| `FIELD_VALUE_NOT_ALLOWED` | Enumeración o catálogo sin ese valor |
| `FIELD_OUT_OF_RANGE` | Número o fecha fuera de su rango |
| `FIELD_TOO_MANY_LINES` | Más de `NOTE_MAX_LINES` líneas |
| `FIELD_TOO_MANY_ITEMS` | Lista con más de `LIST_MAX_ITEMS` elementos |

Forma de la respuesta de validación (las demás reglas de salida están en [`docs/Seguridad.md`](../docs/Seguridad.md)):

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Hay campos que no cumplen las reglas.",
    "details": {
      "fields": [
        { "field": "full_name", "code": "FIELD_CHARACTERS_NOT_ALLOWED" }
      ]
    },
    "request_id": "..."
  }
}
```

Los `details` nunca incluyen el valor enviado si es un secreto (contraseña, código, token).

## 3. Constantes de `FieldLimits`

`FieldLimits` vive en `co.caudal.shared`. Cada grupo tiene `_MIN`, `_MAX` y `_PATTERN`. El nombre del placeholder de Flyway es el mismo nombre en minúsculas, por ejemplo `${person_name_max}` para `FieldLimits.PERSON_NAME_MAX`. Los CHECK de la base de datos se generan con esos placeholders (ver [`docs/Seguridad-de-la-base-de-datos.md`](../docs/Seguridad-de-la-base-de-datos.md)).

| Grupo (`FieldLimits.*`) | Mín | Máx | Patrón o regla | Placeholder Flyway | Columnas que lo usan |
|---|---|---|---|---|---|
| `USERNAME` | 3 | 32 | `^[a-z0-9][a-z0-9._-]{1,30}[a-z0-9]$` (ASCII, minúsculas) | `${username_max}` | `iam.users.username` |
| `PASSWORD` | 12 | 128 | Unicode NFKC; sin reglas de composición | No aplica (solo hash) | `auth/change-password`, `auth/reset-password` |
| `PERSON_NAME` | 2 | 80 | `^[\p{L}][\p{L}\p{M} .'-]*$` | `${person_name_max}` | `iam.users.full_name` |
| `AQUEDUCT_NAME` | 3 | 100 | `^[\p{L}\p{Nd} .,()-]+$` | `${aqueduct_name_max}` | `org.aqueducts.name` |
| `AQUEDUCT_SLUG` | 3 | 40 | `^[a-z0-9]+(-[a-z0-9]+)*$` | `${aqueduct_slug_max}` | `org.aqueducts.slug`, rutas `/public/{slug}` |
| `PLACE_NAME` | 2 | 60 | `^[\p{L}\p{Nd} .#()-]+$` | `${place_name_max}` | `org.tanks.name`, `org.sectors.name`, `org.valves.name`, `devices.devices.name` |
| `VALVE_CODE` | 1 | 20 | `^[A-Z0-9-]+$` | `${valve_code_max}` | `org.valves.code` |
| `SECTOR_CODE` (propuesta) | 1 (por definir) | 20 | `^[A-Z0-9-]+$` | `${sector_code_max}` (propuesta) | `org.sectors.code`. Hoy el esquema lo escribe como literal: hay que migrarlo |
| `LOCATION_HINT` | 0 | 120 | Una línea | `${location_hint_max}` | `org.valves.location_hint`, `ops.incidents.location_hint` |
| `NOTE` | 0 | 500 | Hasta `NOTE_MAX_LINES` (10) líneas; sin controles salvo LF | `${note_max}` | `ops.readings.note`, `ops.shift_executions.note`, `ops.day_closures.notes`, `ops.anomalies.resolution_note`, `ops.incident_status_history.note` |
| `INCIDENT_DESCRIPTION` | 10 | 500 | Igual que `NOTE` (hasta 10 líneas) | `${incident_description_max}` | `ops.incidents.description` |
| `REASON` | 10 | 500 | Una línea (propuesta, verificar) | `${reason_max}` | `rule_sets.change_reason`, `reading_corrections.reason`, `proposal_decisions.reason`, `valve_commands.manual_reason`, `summary_shares.reason` |
| `CATALOG_CODE` | 2 | 40 | `^[A-Z][A-Z0-9_]*$` | `${catalog_code_max}` | `org.catalog_items.code`, `ops.incidents.category_code`, `ops.readings.water_appearance_code` |
| `CATALOG_LABEL` | 2 | 60 | Texto seguro: `^[\p{L}\p{Nd} .,()/'-]+$` (propuesta, verificar) | `${catalog_label_max}` | `org.catalog_items.label_es` |
| `TRACKING_CODE` | 8 | 8 | Crockford base32: `^[0-9A-HJKMNP-TV-Z]{8}$` | No aplica (se guarda el hash) | `ops.incidents.tracking_code_hash` |

Constantes técnicas (sin placeholder de BD, se usan en código):

| Constante | Valor | Uso |
|---|---|---|
| `TEXT_GLOBAL_MAX` | 2.000 | Tope de cualquier texto |
| `NOTE_MAX_LINES` | 10 | Líneas en notas y descripciones |
| `LIST_MAX_ITEMS` | 100 | Listas de cualquier endpoint |
| `JSON_DEPTH_MAX` | 10 | Profundidad JSON |
| `BODY_MAX_BYTES` | 65.536 | Cuerpo general |
| `TELEMETRY_BODY_MAX_BYTES` | 262.144 | Telemetría |
| `IMPORT_BODY_MAX_BYTES` | 1.048.576 | Importación |
| `IMPORT_MAX_ROWS` | 5.000 | Filas por lote de importación |
| `TELEMETRY_BATCH_MAX` | 100 | Puntos por petición de telemetría (mín 1) |
| `PAGE_LIMIT_MIN` / `PAGE_LIMIT_MAX` / `PAGE_LIMIT_DEFAULT` | 1 / 100 / 20 | Paginación |
| `AUTH_HEADER_MAX_BYTES` | 2.048 | Cabecera `Authorization` |
| `FUTURE_TIMESTAMP_SKEW_SECONDS` | 300 | Tolerancia de instantes futuros |
| `DEVICE_SIGNATURE_WINDOW_SECONDS` | 300 | Ventana de firma de dispositivo (±) |
| `DEVICE_NONCE_TTL_SECONDS` | 600 | Nonce único durante 10 minutos |
| `GAUGE_DECIMALS_MAX` | 2 | Decimales de `gauge_value` y correcciones |
| `HOURS_PER_DAY` | 24 | Constante física (`co.caudal.shared`, propuesta). No es un parámetro de negocio |
| `FORECAST_HORIZON_DAYS_MIN` / `FORECAST_HORIZON_DAYS_MAX` | 1 / 3 | Ver contradicción en la sección 9 |

## 4. Tabla canónica por endpoint

Convenciones de la tabla:

- **Oblig.**: Sí, No o "según regla" cuando depende de otro campo.
- **Mín / Máx**: longitud para texto (después de normalizar), valor para números, cantidad para listas.
- **Error**: código de detalle de la sección 2 que se devuelve al violar la regla de ese campo, salvo que se indique otro.
- Los endpoints `PATCH` usan las mismas reglas que sus `POST` para cada campo, con todos los campos opcionales, salvo que se indique lo contrario.

### 4.1 Autenticación (`/api/v1/auth`)

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /auth/login` | `username` | string | Sí | 1 | 128 | Sin regla de formato en login: si no cumple se responde `INVALID_CREDENTIALS` genérico | trim, minúsculas ASCII | `USERNAME` (solo como tope de entrada) | `FIELD_REQUIRED` o `INVALID_CREDENTIALS` |
| `POST /auth/login` | `password` | string | Sí | 1 | 128 | Sin reglas de composición en login (las cuentas antiguas pueden no cumplir la política nueva) | NFKC, sin recortar | `PASSWORD` (tope de entrada) | `FIELD_TOO_LONG` (antes del hash, para evitar costo excesivo) |
| `POST /auth/refresh` | refresh (cookie `caudal_rt`) | string opaco | Sí | 43 | 43 | `^[A-Za-z0-9_-]{43}$` (256 bits en base64url sin relleno) | Ninguna | No aplica | `UNAUTHORIZED` (401) |
| `POST /auth/refresh` | cabecera `X-Requested-With` | string | Obligatoria | 10 | 10 | Igual a `caudal-web` (y `Origin` permitido) | Ninguna | No aplica | `FORBIDDEN` (403) |
| `POST /auth/change-password` | `current_password` | string | Sí | 1 | 128 | Solo tope de entrada | NFKC | `PASSWORD` | `FIELD_TOO_LONG` |
| `POST /auth/change-password` | `new_password` | string | Sí | 12 | 128 | No en la lista de comunes; no contiene el usuario; no está en las últimas 5 | NFKC | `PASSWORD` | `PASSWORD_POLICY_VIOLATION` (`FIELD_TOO_SHORT` en detalle si aplica) |
| `POST /auth/reset-password` | `username` | string | Sí | 3 | 32 | `USERNAME` | trim, minúsculas ASCII | `USERNAME` | `FIELD_FORMAT_INVALID` |
| `POST /auth/reset-password` | `code` | string | Sí | por definir | por definir | Formato del código de un solo uso (por definir; se muestra una sola vez) | trim, mayúsculas, sin espacios | por definir (`RESET_CODE`) | `FIELD_FORMAT_INVALID` |
| `POST /auth/reset-password` | `new_password` | string | Sí | 12 | 128 | Igual que `new_password` del cambio | NFKC | `PASSWORD` | `PASSWORD_POLICY_VIOLATION` |
| `POST /auth/mfa/*` (confirmación) | `totp_code` | string | Sí | 6 | 6 | `^[0-9]{6}$` (propuesta, verificar: 6 dígitos) | trim | por definir (`MFA_CODE`) | `FIELD_FORMAT_INVALID` |
| `POST /auth/mfa/*` (respaldo) | `recovery_code` | string | Según regla | por definir | por definir | Formato por definir | trim, mayúsculas, sin guiones | por definir | `FIELD_FORMAT_INVALID` |

### 4.2 Usuarios y membresías

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /users` | `username` | string | Sí | 3 | 32 | Patrón de usuario; único (`UQ`) | trim, minúsculas ASCII | `USERNAME` | `FIELD_FORMAT_INVALID` o `FIELD_TOO_SHORT` |
| `POST /users` | `full_name` | string | Sí | 2 | 80 | Letras, espacio, apóstrofo, guion y punto | NFC, recorte, colapso de espacios | `PERSON_NAME` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| `PATCH /users/{id}` | `full_name` | string | No | 2 | 80 | Igual que el alta | NFC, recorte, colapso | `PERSON_NAME` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| `PATCH /users/{id}` | `status` | enum | No | No aplica | No aplica | `ACTIVE`, `DISABLED` (`LOCKED` lo pone el sistema) | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /users/{id}/reset-grants` | `expires_in_minutes` | entero | No | por definir | por definir | "Expiración corta (configurable)": por definir | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `GET /memberships`, `DELETE /memberships/{id}` | `id` (ruta) | UUID | Sí | No aplica | No aplica | UUID canónico | minúsculas | No aplica | `VALIDATION_ERROR` |
| `POST /memberships` | `user_id` | UUID | Sí | No aplica | No aplica | Existe y está activo | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /memberships` | `aqueduct_id` | UUID | Sí | No aplica | No aplica | Existe; debe coincidir con el acueducto de la sesión (por definir si el administrador puede actuar sobre otros) | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /memberships` | `role_code` | enum | Sí | No aplica | No aplica | `BOARD_ADMIN`, `BOARD_MEMBER`, `OPERATOR`, `PROJECT_TEAM`, `SUPPORT_ENTITY` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /memberships` | `valid_from` | instante ISO-8601 | No | No aplica | No aplica | Por defecto `now()`. Con zona obligatoria | No aplica | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /memberships` | `valid_to` | instante ISO-8601 | No | No aplica | No aplica | `valid_to > valid_from` (`CHK` de la tabla) | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |

### 4.3 Metadatos, catálogos y aviso de privacidad

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `GET /meta/constraints` | No tiene campos | No aplica | No aplica | No aplica | No aplica | Público (sin login): lo usa el formulario público | No aplica | No aplica | No aplica |
| `GET /catalogs/{catalog}` | `catalog` (ruta) | enum | Sí | No aplica | No aplica | `WATER_APPEARANCE`, `DAMAGE_CATEGORY` | Exacta | No aplica | `404 NOT_FOUND` |
| `POST /privacy-notice/accept` | `version` | entero | Sí | 1 | por definir | Debe ser la versión vigente | No aplica | No aplica | `409 CONFLICT` (la versión ya no es la vigente) |
| `POST /privacy-notice/accept` | `accepted` | booleano | Sí | No aplica | No aplica | Debe ser `true` | No aplica | No aplica | `FIELD_FORMAT_INVALID` |

### 4.4 Red: tanques, sectores y válvulas

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /tanks` | `name` | string | Sí | 2 | 60 | Nombre de lugar | NFC, recorte, colapso | `PLACE_NAME` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| `POST /tanks` | `gauge_min` | decimal | Sí | por definir | por definir | Número con como máximo 2 decimales | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_FORMAT_INVALID` |
| `POST /tanks` | `gauge_max` | decimal | Sí | `gauge_min` | por definir | `gauge_max > gauge_min` | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `POST /tanks` | `gauge_step` | decimal | Sí | > 0 | por definir | Precisión de lectura; ≤ 2 decimales | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `POST /tanks` | `capacity_liters` | entero | No | 1 | por definir | Opcional; > 0 | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `POST /sectors` | `code` | string | Sí | 2 | 20 | `^[A-Z0-9-]+$`, único por acueducto (`FieldLimits.SECTOR_CODE`) | Mayúsculas ASCII | `SECTOR_CODE` | `FIELD_FORMAT_INVALID`; duplicado: `409 CONFLICT` |
| `POST /sectors` | `name` | string | Sí | 2 | 60 | Nombre de lugar | NFC, recorte, colapso | `PLACE_NAME` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| `POST /sectors` | `parent_sector_id` | UUID | No | No aplica | No aplica | Mismo acueducto; sin ciclos (propuesta, verificar) | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /sectors` | `households_count` | entero | No | 0 | por definir | `≥ 0`; dato agregado, sin datos personales | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `PATCH /sectors/{id}` | `name`, `households_count`, `parent_sector_id`, `is_active` | varios | No | según fila | según fila | Igual que el alta. `code` no se modifica (propuesta, verificar) | Igual que el alta | Igual que el alta | Igual que el alta |
| `GET /sectors/{id}/valves`, `POST /sectors/{id}/valves` | `sector_id` (ruta) | UUID | Sí | No aplica | No aplica | Existe en el acueducto | minúsculas | No aplica | `404 NOT_FOUND` |
| `POST /sectors/{id}/valves` | `code` | string | Sí | 1 | 20 | `^[A-Z0-9-]+$`, único por sector | Mayúsculas ASCII | `VALVE_CODE` | `FIELD_FORMAT_INVALID` |
| `POST /sectors/{id}/valves` | `name` | string | Sí | 2 | 60 | Nombre de lugar | NFC, recorte, colapso | `PLACE_NAME` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| `POST /sectors/{id}/valves` | `location_hint` | string | No | 0 | 120 | Una línea | NFC, recorte | `LOCATION_HINT` | `FIELD_TOO_LONG` o `FIELD_TOO_MANY_LINES` |
| `POST /sectors/{id}/valves` | `is_motorized` | booleano | No | No aplica | No aplica | Por defecto `false` | No aplica | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /sectors/{id}/valves` | `fail_safe_position` | enum | No | No aplica | No aplica | `KEEP` (por defecto), `OPEN`, `CLOSED` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `PATCH /valves/{id}` | `name`, `location_hint`, `is_motorized`, `fail_safe_position`, `is_active` | varios | No | según fila | según fila | Igual que el alta. `code` no se modifica (propuesta, verificar) | Igual que el alta | Igual que el alta | Igual que el alta |

### 4.5 Reglas de la Junta (versiones)

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /rule-sets` | `change_reason` | string | Sí | 10 | 500 | Motivo del borrador | NFC, recorte | `REASON` | `FIELD_TOO_SHORT` |
| `PATCH /rule-sets/{id}` (solo borrador) | `reserve_level` | decimal | No | 0 | por definir | `numeric(6,2)`, en unidades de la regla | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `max_daily_service_hours` | decimal | No | > 0 | `HOURS_PER_DAY` (24) | Parámetro de negocio; ≤ 2 decimales. El tope 24 es físico | No aplica | `HOURS_PER_DAY`, `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `min_shift_hours` | decimal | No | 0 | `max_shift_hours` | Duración mínima de un turno | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `max_shift_hours` | decimal | No | `min_shift_hours` | `max_daily_service_hours` | `≥ min_shift_hours` | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `allocation_strategy` | enum | No | No aplica | No aplica | `PRIORITY_THEN_LONGEST_WAIT`, `EQUAL_SPLIT` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `PATCH /rule-sets/{id}` | `duplicate_window_minutes` | entero | No | 0 | por definir | `≥ 0` | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `max_level_change_per_hour` | decimal | No | 0 | por definir | `≥ 0` | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `stale_reading_hours` | entero | No | 0 | por definir | `≥ 0` | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `max_backdate_days` | entero | No | 0 | por definir | `≥ 0` | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `forecast_horizon_days` | entero | No | `FORECAST_HORIZON_DAYS_MIN` (1) | `FORECAST_HORIZON_DAYS_MAX` (3) | Ver sección 9 (contradicción) | No aplica | `FORECAST_HORIZON_DAYS_*` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `forecast_context_days` | entero | No | 0 | por definir | `≥ 0`, historia enviada a la IA | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `trend_threshold_per_day` | decimal | No | 0 | por definir | `≥ 0` | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `bands[]` | lista | No | 0 | `LIST_MAX_ITEMS` (100) | Sin solapes; cubre el rango del tanque (validación al activar) | No aplica | `LIST_MAX_ITEMS` | `FIELD_TOO_MANY_ITEMS` |
| `PATCH /rule-sets/{id}` | `bands[].band` | enum | Sí (en cada banda) | No aplica | No aplica | `HIGH`, `LOW`, `CRITICAL` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `PATCH /rule-sets/{id}` | `bands[].min_level`, `bands[].max_level` | decimal | Sí | rango del tanque | rango del tanque | `max_level > min_level`; `min` incluido, `max` excluido | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `bands[].daily_service_hours` | decimal | Sí | 0 | `max_daily_service_hours` | `≥ 0` | No aplica | `HOURS_PER_DAY` | `FIELD_OUT_OF_RANGE` |
| `PATCH /rule-sets/{id}` | `bands[].priority_only` | booleano | No | No aplica | No aplica | Por defecto `false` | No aplica | No aplica | `FIELD_FORMAT_INVALID` |
| `PATCH /rule-sets/{id}` | `sector_settings[]` | lista | No | 0 | `LIST_MAX_ITEMS` | `sector_id`, `is_included`, `is_priority`, `priority_rank ≥ 0` | No aplica | `LIST_MAX_ITEMS` | `FIELD_TOO_MANY_ITEMS` |
| `PATCH /rule-sets/{id}` | `valve_orders[]` | lista | No | 0 | `LIST_MAX_ITEMS` | `valve_id`, `sequence_order ≥ 0`; orden único por versión | No aplica | `LIST_MAX_ITEMS` | `FIELD_TOO_MANY_ITEMS` |
| `PATCH /rule-sets/{id}` | `operating_windows[]` | lista | No | 0 | `LIST_MAX_ITEMS` | `day_of_week` entre 1 y 7 o nulo; `start_time < end_time` (`HH:mm`) | No aplica | `LIST_MAX_ITEMS` | `FIELD_OUT_OF_RANGE` |
| `POST /rule-sets/{id}/activate` | `change_reason` | string | Sí | 10 | 500 | Motivo obligatorio | NFC, recorte | `REASON` | `FIELD_TOO_SHORT` |

### 4.6 Lecturas y sincronización offline

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /readings` | `id` | UUID | Sí | No aplica | No aplica | Generado por el cliente (v4 o v7, verificar). Idempotente | minúsculas | No aplica | `FIELD_FORMAT_INVALID` o `DUPLICATE_READING` |
| `POST /readings` | `tank_id` | UUID | Sí | No aplica | No aplica | Existe en el acueducto | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /readings` | `gauge_value` | decimal (JSON number) | Sí | Mín de la regla del tanque | Máx de la regla del tanque | ≤ 2 decimales; fuera de rango responde `422 GAUGE_OUT_OF_RANGE` y no se guarda (ver `Reglas-de-negocio.md`, sección 8) | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `POST /readings` | `water_appearance_code` | código de catálogo | Sí | `CATALOG_CODE` min | `CATALOG_CODE` max | Activo en `WATER_APPEARANCE` | Exacta | `CATALOG_CODE` | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /readings` | `damage_noticed` | booleano | No | No aplica | No aplica | Por defecto `false` | No aplica | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /readings` | `note` | string | No | 0 | 500 | Hasta 10 líneas; emojis permitidos (verificar) | Ver `NOTE` | `NOTE` | `FIELD_TOO_LONG` o `FIELD_TOO_MANY_LINES` |
| `POST /readings` | `observed_at` | instante ISO-8601 | Sí | No aplica | `now + 300 s` | Con zona. No más de `max_backdate_days` hacia atrás | No aplica | `FUTURE_TIMESTAMP_SKEW_SECONDS` | `FUTURE_TIMESTAMP` o `TOO_OLD` (dominio); ausente: `MISSING_TIMESTAMP` (422) |
| `POST /readings/batch` | `batch_id` (UUID cliente de `ops.sync_batches.id`) | UUID | Sí | No aplica | No aplica | Idempotencia del lote | minúsculas | No aplica | `DUPLICATE_READING` |
| `POST /readings/batch` | `items[]` | lista de lecturas | Sí | 1 | `LIST_MAX_ITEMS` (100) | Cada elemento cumple las reglas de `POST /readings`. Verificar si el lote admite más de 100 | No aplica | `LIST_MAX_ITEMS` | `FIELD_TOO_MANY_ITEMS` |
| `GET /readings` | `tank_id` (query) | UUID | No | No aplica | No aplica | Existe | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `GET /readings` | `from`, `to` (query) | instante ISO-8601 | No | No aplica | No aplica | `from < to`; rango máximo por definir | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |
| `GET /readings`, `GET /anomalies`, `GET /schedule-proposals`, `GET /incidents`, `GET /audit-log`, `GET /security-events`, `GET /summaries` | `cursor` (query) | string opaco | No | 1 | por definir | Firmado; si no valida, `CURSOR_INVALID` | No aplica | por definir | `CURSOR_INVALID` |
| `GET /readings`, `GET /anomalies`, `GET /schedule-proposals`, `GET /incidents`, `GET /audit-log`, `GET /security-events`, `GET /summaries` | `limit` (query) | entero | No | `PAGE_LIMIT_MIN` (1) | `PAGE_LIMIT_MAX` (100) | Por defecto 20 | No aplica | `PAGE_LIMIT_*` | `FIELD_OUT_OF_RANGE` |
| `GET /readings/{id}`, `POST /readings/{id}/corrections`, `GET /tanks/{id}/status`, `GET /tanks/{id}/forecasts/latest`, `POST /tanks/{id}/forecasts`, `PATCH /anomalies/{id}`, `PATCH /incidents/{id}/status`, `GET /minutes/{id}`, `GET /minutes/{id}/pdf`, `DELETE /summary-shares/{id}`, `GET /schedule-proposals/{id}`, `POST /schedule-proposals/{id}/*`, `POST /schedule-items/{id}/execution`, `POST /devices/commands/{id}/ack`, `POST /devices/{id}/keys` | `id` o `{id}` (ruta) | UUID | Sí | No aplica | No aplica | UUID canónico; si no existe en el acueducto, `404 NOT_FOUND` (no se revela si existe en otro acueducto) | minúsculas | No aplica | `VALIDATION_ERROR` (formato) o `404 NOT_FOUND` |
| `POST /readings/{id}/corrections` | `corrected_gauge_value` | decimal | Sí | Mín de la regla | Máx de la regla | ≤ 2 decimales; se valida contra el rango del tanque (igual que la lectura original) | No aplica | `GAUGE_DECIMALS_MAX` | `FIELD_OUT_OF_RANGE` |
| `POST /readings/{id}/corrections` | `reason` | string | Sí | 10 | 500 | Motivo obligatorio | NFC, recorte | `REASON` | `FIELD_TOO_SHORT` |

### 4.7 Estado, pronóstico y anomalías

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /tanks/{id}/forecasts` | No tiene cuerpo | No aplica | No aplica | No aplica | No aplica | Límite de 10 por hora por acueducto (`RATE_LIMIT_FORECAST_PER_HOUR`) | No aplica | No aplica | `429 RATE_LIMITED` |
| `GET /anomalies` | `status`, `kind` (query) | enum | No | No aplica | No aplica | `OPEN`, `CONFIRMED`, `DISMISSED`; `POSSIBLE_LEAK`, `SENSOR_FAULT`, `STALE_DATA`, `SUSPECTED_DUPLICATE`, `ABNORMAL_DROP` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `PATCH /anomalies/{id}` | `status` | enum | Sí | No aplica | No aplica | `OPEN`, `CONFIRMED`, `DISMISSED` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `PATCH /anomalies/{id}` | `resolution_note` | string | No | 0 | 500 | Hasta 10 líneas | Ver `NOTE` | `NOTE` | `FIELD_TOO_LONG` |
| `PATCH /anomalies/{id}` | `related_incident_id` | UUID | No | No aplica | No aplica | Existe en el acueducto | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |

### 4.8 Propuestas de turnos y decisión de la Junta

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /schedule-proposals` | `service_date` | fecha `YYYY-MM-DD` | Sí | por definir | por definir | Día de servicio. Límites de antelación por definir | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `GET /schedule-proposals` | `service_date`, `status` (query) | fecha, enum | No | No aplica | No aplica | `status` en `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `APPROVED_WITH_CHANGES`, `REJECTED`, `PUBLISHED`, `CLOSED` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /schedule-proposals/{id}/approve`, `/modify`, `/reject`, `/publish` | `lock_version` | entero | Sí | 0 | No aplica | Debe coincidir con la versión actual (bloqueo optimista) | No aplica | No aplica | `VALIDATION_ERROR` con detalle `FIELD_OUT_OF_RANGE`; conflicto de versión: 409 (código de dominio propuesto `LOCK_VERSION_CONFLICT`) |
| `POST /schedule-proposals/{id}/modify` | `items[]` | lista | Sí | 1 | `LIST_MAX_ITEMS` (100) | Turnos sin solapes (ver sección 9); cada duración entre `min_shift_hours` y `max_shift_hours` | No aplica | `LIST_MAX_ITEMS` | `FIELD_TOO_MANY_ITEMS` |
| `POST /schedule-proposals/{id}/modify` | `items[].sector_id` | UUID | Sí | No aplica | No aplica | Sector de la regla vigente, incluido (`is_included`) | minúsculas | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /schedule-proposals/{id}/modify` | `items[].start_at`, `items[].end_at` | instante ISO-8601 | Sí | No aplica | No aplica | `end_at > start_at`; dentro de la ventana de operación | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |
| `POST /schedule-proposals/{id}/modify`, `/reject` | `reason` | string | Sí | 10 | 500 | Motivo obligatorio para modificar o rechazar | NFC, recorte | `REASON` | `FIELD_TOO_SHORT` |
| `POST /schedule-items/{id}/execution` | `status` | enum | Sí | No aplica | No aplica | `COMPLETED`, `PARTIAL`, `NOT_EXECUTED` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /schedule-items/{id}/execution` | `actual_start` | instante ISO-8601 | Según estado | No aplica | `now + 300 s` | Obligatorio si `COMPLETED` o `PARTIAL` (propuesta, verificar) | No aplica | `FUTURE_TIMESTAMP_SKEW_SECONDS` | `FIELD_FORMAT_INVALID` o `FIELD_OUT_OF_RANGE` |
| `POST /schedule-items/{id}/execution` | `actual_end` | instante ISO-8601 | Según estado | No aplica | `now + 300 s` | `actual_end > actual_start` | No aplica | `FUTURE_TIMESTAMP_SKEW_SECONDS` | `FIELD_OUT_OF_RANGE` |
| `POST /schedule-items/{id}/execution` | `note` | string | No | 0 | 500 | Novedades | Ver `NOTE` | `NOTE` | `FIELD_TOO_LONG` |
| `POST /schedule-items/{id}/execution` | (registro de reemplazo) | No aplica | No aplica | No aplica | No aplica | Se crea un registro nuevo con `supersedes_id`; nunca se edita | No aplica | No aplica | `404 NOT_FOUND` si no existe el turno |

### 4.9 Cierre del día

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `GET /days/{date}/closure`, `POST /days/{date}/closure` | `date` (ruta) | fecha `YYYY-MM-DD` | Sí | No aplica | No aplica | Fecha real del calendario; zona `America/Bogota` para "hoy" | No aplica | No aplica | `VALIDATION_ERROR` |
| `POST /days/{date}/closure` | `notes` | string | No | 0 | 500 | Novedades del día | Ver `NOTE` | `NOTE` | `FIELD_TOO_LONG` o `FIELD_TOO_MANY_LINES` |

### 4.10 Incidentes (uso interno)

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `GET /incidents` | `status`, `source`, `sector_id` (query) | enum, UUID | No | No aplica | No aplica | `status` en `REPORTED`, `VERIFYING`, `CONFIRMED`, `RESOLVED`, `DISMISSED`; `source` en `PUBLIC_FORM`, `OPERATOR`, `ANOMALY`, `IMPORT` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /incidents` | `source` | enum | No aplica (lo fija el servidor) | No aplica | No aplica | Según rol: `OPERATOR` para el fontanero. No se acepta del cliente | No aplica | No aplica | Campo desconocido: `VALIDATION_ERROR` |
| `POST /incidents` | `category_code` | código de catálogo | Sí | `CATALOG_CODE` min (2) | `CATALOG_CODE` max (40) | Activo en `DAMAGE_CATEGORY` | Exacta | `CATALOG_CODE` | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /incidents` | `sector_id` | UUID | No | No aplica | No aplica | Existe en el acueducto | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /incidents` | `description` | string | Sí | 10 | 500 | Hasta 10 líneas | Ver `INCIDENT_DESCRIPTION` | `INCIDENT_DESCRIPTION` | `FIELD_TOO_SHORT`, `FIELD_TOO_LONG`, `FIELD_TOO_MANY_LINES` |
| `POST /incidents` | `location_hint` | string | No | 0 | 120 | Una línea | NFC, recorte | `LOCATION_HINT` | `FIELD_TOO_LONG` |
| `PATCH /incidents/{id}/status` | `to_status` | enum | Sí | No aplica | No aplica | Transición válida según la máquina de estados (`REPORTED` → `VERIFYING` → `CONFIRMED` → `RESOLVED` o `DISMISSED`) | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` o `VALIDATION_ERROR` (transición) |
| `PATCH /incidents/{id}/status` | `note` | string | Por definir | 0 | 500 | Obligatoria al descartar (propuesta, verificar) | NFC, recorte | `NOTE` | `FIELD_TOO_LONG` |

### 4.11 Actas y resúmenes para entidades

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /minutes` | `period_from` | fecha | Sí | No aplica | No aplica | `period_from ≤ period_to` | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |
| `POST /minutes` | `period_to` | fecha | Sí | `period_from` | por definir | Tamaño máximo del periodo por definir | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `POST /summary-shares` | `grantee_user_id` | UUID | Sí | No aplica | No aplica | Usuario con rol `SUPPORT_ENTITY` en el acueducto | minúsculas | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /summary-shares` | `scope` | enum | Sí | No aplica | No aplica | `MONTHLY_SUMMARY`, `MINUTES` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /summary-shares` | `period_from`, `period_to` | fecha | Sí | No aplica | No aplica | `period_from ≤ period_to` | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |
| `POST /summary-shares` | `reason` | string | Sí | 10 | 500 | Motivo obligatorio | NFC, recorte | `REASON` | `FIELD_TOO_SHORT` |
| `POST /summary-shares` | `expires_at` | instante ISO-8601 | Sí | No aplica | por definir | Debe ser posterior a ahora; vencimiento obligatorio | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |
| `GET /summaries` | `cursor`, `limit` (query) | ver sección 4.6 | No | No aplica | No aplica | Solo resúmenes autorizados y vigentes | No aplica | No aplica | `CURSOR_INVALID` o `FIELD_OUT_OF_RANGE` |

### 4.12 Evaluación IA frente a estimación simple

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `GET /forecast-evaluation` | `horizon_days` (query) | entero | No | `FORECAST_HORIZON_DAYS_MIN` (1) | `FORECAST_HORIZON_DAYS_MAX` (3) | Horizontes de la evaluación (ver sección 9) | No aplica | `FORECAST_HORIZON_DAYS_*` | `FIELD_OUT_OF_RANGE` |
| `GET /forecast-evaluation` | `from`, `to`, `tank_id` (query) | fecha o UUID | No | No aplica | No aplica | `from ≤ to` | minúsculas (UUID) | No aplica | `FIELD_OUT_OF_RANGE` o `FIELD_FORMAT_INVALID` |

### 4.13 Público (sin login)

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `GET /public/{aqueductSlug}/schedule`, `.../schedule/whatsapp-text`, `.../schedule/poster.pdf` | `aqueductSlug` (ruta) | string | Sí | 3 | 40 | `AQUEDUCT_SLUG`; si no cumple o no existe, `404 NOT_FOUND` (no se revela nada) | minúsculas | `AQUEDUCT_SLUG` | `404 NOT_FOUND` |
| `POST /public/{aqueductSlug}/damage-reports` | `category_code` | código de catálogo | Sí | 2 | 40 | Activo en `DAMAGE_CATEGORY` | Exacta | `CATALOG_CODE` | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /public/{aqueductSlug}/damage-reports` | `sector_id` | UUID | No | No aplica | No aplica | Si se conoce; existe en el acueducto | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /public/{aqueductSlug}/damage-reports` | `description` | string | Sí | 10 | 500 | Hasta 10 líneas; sin datos personales (no pedir nombre ni teléfono) | Ver `INCIDENT_DESCRIPTION` | `INCIDENT_DESCRIPTION` | `FIELD_TOO_SHORT`, `FIELD_TOO_LONG` |
| `POST /public/{aqueductSlug}/damage-reports` | `location_hint` | string | No | 0 | 120 | Una línea | NFC, recorte | `LOCATION_HINT` | `FIELD_TOO_LONG` |
| `POST /public/{aqueductSlug}/damage-reports` | campo trampa (honeypot `website`) | string | No | 0 | 0 | Debe ir vacío. Si trae valor: se responde como éxito, no se guarda, y se registra `HONEYPOT_TRIGGERED` | No aplica | No aplica | Sin error visible |
| `POST /public/{aqueductSlug}/damage-reports` | tiempo de llenado (propuesta: token firmado emitido al cargar el formulario) | token | Sí (propuesta) | por definir | por definir | Mínimo de 3 s antes de enviar (`PUBLIC_FORM_MIN_SECONDS`) | No aplica | por definir | `VALIDATION_ERROR` |
| `GET /public/damage-reports/{trackingCode}` | `trackingCode` (ruta) | string | Sí | 8 | 8 | `TRACKING_CODE`: Crockford base32 | Mayúsculas, sin espacios ni guiones; `I` y `L` pasan a `1`; `O` pasa a `0` | `TRACKING_CODE` | `FIELD_FORMAT_INVALID` o `404 NOT_FOUND` |

### 4.14 Dispositivos (firmados con Ed25519)

Cabeceras de firma: su formato está en [`docs/Seguridad.md`](../docs/Seguridad.md) y en `DeviceSignatureVerifier`.

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| Todos los `/devices/*` firmados | `X-Device-Id` (cabecera) | UUID | Sí | 36 | 36 | UUID canónico | minúsculas | No aplica | `INVALID_SIGNATURE` (security event) |
| Todos los `/devices/*` firmados | `X-Timestamp` (cabecera) | entero (unix, segundos) | Sí | 10 | 10 | `^[0-9]{10}$` (propuesta). Dentro de ±300 s | No aplica | `DEVICE_SIGNATURE_WINDOW_SECONDS` | `INVALID_SIGNATURE` |
| Todos los `/devices/*` firmados | `X-Nonce` (cabecera) | hex | Sí | 32 | 32 | `^[0-9a-f]{32}$` (128 bits); no repetido en 10 min | minúsculas | `DEVICE_NONCE_TTL_SECONDS` | `NONCE_REPLAY` (security event) |
| Todos los `/devices/*` firmados | `X-Signature` (cabecera) | base64url | Sí | 86 | 86 | `^[A-Za-z0-9_-]{86}$` (64 bytes de Ed25519, sin relleno) | No aplica | No aplica | `INVALID_SIGNATURE` |
| `POST /devices/telemetry` | `points[]` | lista | Sí | 1 | `TELEMETRY_BATCH_MAX` (100) | Lote de puntos; cuerpo ≤ 256 KiB | No aplica | `TELEMETRY_BATCH_MAX` | `FIELD_TOO_MANY_ITEMS` |
| `POST /devices/telemetry` | `points[].id` | UUID | Sí | No aplica | No aplica | Generado por el dispositivo (idempotencia) | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /devices/telemetry` | `points[].metric` | enum | Sí | No aplica | No aplica | `LEVEL`, `BATTERY_V`, `RSSI`, `VALVE_POSITION` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /devices/telemetry` | `points[].value` | decimal | Sí | por definir por métrica | por definir por métrica | `numeric(10,3)`: ≤ 3 decimales | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `POST /devices/telemetry` | `points[].observed_at` | instante ISO-8601 | Sí | No aplica | `now + 300 s` | Con zona; no más antiguo que `max_backdate_days` | No aplica | `FUTURE_TIMESTAMP_SKEW_SECONDS` | `FUTURE_TIMESTAMP` o `TOO_OLD` |
| `GET /devices/commands` | No tiene cuerpo | No aplica | No aplica | No aplica | No aplica | Solo comandos del dispositivo firmante | No aplica | No aplica | `FORBIDDEN` (403) |
| `POST /devices/commands/{id}/ack` | `status` | enum | Sí | No aplica | No aplica | `ACKED`, `FAILED` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /devices/commands/{id}/ack` | `detail` | string | No | 0 | 200 | Texto plano | NFC, recorte | por definir (propuesta: 200 como en `valve_command_events.detail`) | `FIELD_TOO_LONG` |
| `POST /devices` | `kind` | enum | Sí | No aplica | No aplica | `LEVEL_SENSOR`, `VALVE_ACTUATOR`, `GATEWAY` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /devices` | `name` | string | Sí | 2 | 60 | Nombre de lugar | NFC, recorte, colapso | `PLACE_NAME` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| `POST /devices` | `tank_id` | UUID | Según `kind` | No aplica | No aplica | Obligatorio si `LEVEL_SENSOR`; prohibido en otros casos | minúsculas | No aplica | `VALIDATION_ERROR` (regla cruzada) |
| `POST /devices` | `valve_id` | UUID | Según `kind` | No aplica | No aplica | Obligatorio si `VALVE_ACTUATOR`; prohibido en otros casos | minúsculas | No aplica | `VALIDATION_ERROR` (regla cruzada) |
| `POST /devices` | `firmware_version` | string | No | 1 | 30 | `^[0-9A-Za-z._+-]+$` (propuesta, verificar) | Ninguna | por definir | `FIELD_FORMAT_INVALID` |
| `POST /devices/{id}/keys` | `public_key` | base64url | Sí | 43 | 43 | Decodifica a 32 bytes Ed25519, sin relleno (`^[A-Za-z0-9_-]{43}$`) | Ninguna | por definir (propuesta: `DEVICE_PUBLIC_KEY`) | `FIELD_FORMAT_INVALID` |
| `POST /valve-commands/manual` | `valve_id` | UUID | Sí | No aplica | No aplica | Existe; válvula motorizada | minúsculas | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /valve-commands/manual` | `command` | enum | Sí | No aplica | No aplica | `OPEN`, `CLOSE` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `POST /valve-commands/manual` | `manual_reason` | string | Sí | 10 | 500 | Solo `BOARD_ADMIN` | NFC, recorte | `REASON` | `FIELD_TOO_SHORT` |
| `POST /valve-commands/manual` | `not_before`, `expires_at` | instante ISO-8601 | Sí | No aplica | No aplica | `expires_at > not_before`; ventana máxima por definir | No aplica | por definir | `FIELD_OUT_OF_RANGE` |

### 4.15 Administración de importación (solo `PROJECT_TEAM`, solo acueducto demo)

Metadatos comunes de los tres endpoints:

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /imports/*` | `seed` | entero (bigint) | Sí | 0 | 9.223.372.036.854.775.807 | Semilla del escenario | No aplica | No aplica | `FIELD_OUT_OF_RANGE` |
| `POST /imports/*` | `scenario_name` | string | Sí | 1 | 60 | `^[a-z0-9-]+$` (propuesta, verificar) | Minúsculas | por definir (`SCENARIO_NAME`) | `FIELD_FORMAT_INVALID` |
| `POST /imports/*` | `simulator_version` | string | Sí | 1 | 30 | `^[0-9A-Za-z.+-]+$` (propuesta, verificar) | Ninguna | por definir | `FIELD_FORMAT_INVALID` |
| `POST /imports/*` | `file_sha256` | hex | Sí | 64 | 64 | `^[0-9a-f]{64}$` | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /imports/*` | `rows[]` | lista | Sí | 1 | `IMPORT_MAX_ROWS` (5.000) | Cuerpo ≤ 1 MiB. Si el acueducto no es demo: `NOT_DEMO_AQUEDUCT` (403, propuesta) | No aplica | `IMPORT_MAX_ROWS` | `FIELD_TOO_MANY_ITEMS` |

Campos de cada fila:

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `POST /imports/readings` (cada fila) | `id`, `tank_id`, `gauge_value`, `water_appearance_code`, `damage_noticed`, `note`, `observed_at` | varios | Igual que `POST /readings` | Igual | Igual | Igual que `POST /readings`, salvo que `observed_at` puede ser anterior a `max_backdate_days` | Igual | Igual | Igual |
| `POST /imports/shift-executions` (cada fila) | `schedule_item_id` | UUID | Sí | No aplica | No aplica | Existe y pertenece al acueducto demo | minúsculas | No aplica | `FIELD_FORMAT_INVALID` |
| `POST /imports/shift-executions` (cada fila) | `status`, `actual_start`, `actual_end`, `note` | varios | Igual que `POST /schedule-items/{id}/execution` | Igual | Igual | Igual | Igual | Igual | Igual |
| `POST /imports/incidents` (cada fila) | `category_code`, `sector_id`, `description`, `location_hint`, `reported_at` | varios | Igual que `POST /incidents`; `reported_at` como instante | Igual | Igual | Igual; `reported_at` puede ser anterior a `max_backdate_days` | Igual | Igual | Igual |

### 4.16 Auditoría y eventos de seguridad

| Endpoint | Campo | Tipo | Oblig. | Mín | Máx | Regla | Normalización | Constante | Error |
|---|---|---|---|---|---|---|---|---|---|
| `GET /audit-log` | `from`, `to` (query) | instante ISO-8601 | No | No aplica | No aplica | `from < to`; rango máximo por definir | No aplica | por definir | `FIELD_OUT_OF_RANGE` |
| `GET /audit-log` | `actor_type` (query) | enum | No | No aplica | No aplica | `USER`, `DEVICE`, `SYSTEM`, `API_KEY` | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `GET /audit-log` | `action`, `entity_type` (query) | string | No | 1 | 60 | `^[A-Z][A-Z0-9_]*$` (propuesta) | Mayúsculas | por definir | `FIELD_FORMAT_INVALID` |
| `GET /security-events` | `type`, `severity` (query) | enum | No | No aplica | No aplica | Valores de `iam.security_events` (`type`: ACCOUNT_LOCKED, TOKEN_REUSE_DETECTED, PERMISSION_DENIED, RATE_LIMITED, INVALID_SIGNATURE, NONCE_REPLAY, HONEYPOT_TRIGGERED, IMPORT_REJECTED, MFA_FAILED; `severity`: LOW, MEDIUM, HIGH, CRITICAL) | Exacta | No aplica | `FIELD_VALUE_NOT_ALLOWED` |
| `GET /security-events` | `from`, `to` (query) | instante ISO-8601 | No | No aplica | No aplica | Igual que `GET /audit-log` | No aplica | por definir | `FIELD_OUT_OF_RANGE` |

### 4.17 Salud

| Endpoint | Campo | Regla |
|---|---|---|
| `GET /actuator/health` | No tiene campos | Público, respuesta mínima (sin detalles de componentes) |

## 5. Expresiones regulares: Java y JavaScript

Las expresiones se escriben una sola vez en `FieldLimits` (Java). El frontend las recibe por `GET /api/v1/meta/constraints` y las compila en Zod. La fuente es Java; JavaScript solo las copia.

| Regla | Java (Hibernate Validator `@Pattern`) | JavaScript (Zod `.regex`) |
|---|---|---|
| Letras Unicode | `\p{L}` | `/\p{L}/u` (el flag `u` es obligatorio) |
| Marcas Unicode | `\p{M}` | `/\p{M}/u` |
| Dígitos Unicode | `\p{Nd}`. En Java `\d` es solo ASCII por defecto, así que no se usa para dígitos de nombres | `/\p{Nd}/u` |
| Coincidencia completa | `@Pattern` usa `matches()`, así que el patrón debe cubrir toda la cadena. Los `^` y `$` no son necesarios, pero no hacen daño | Se escribe `^...$`. Sin el flag `m`, `$` solo coincide al final |
| Salto de línea final | Java `$` puede coincidir antes de un `\n` final. Por eso se usa `matches()` y no `find()` | En JS `$` no admite el `\n` final sin el flag `m`. Se rechaza igual |
| Espacio | Espacio literal dentro de la clase: `[\p{L} .'-]` | Igual. No usar `\s`, que incluye tabulador, salto de línea y espacios no separables |
| Guion | Siempre al final de la clase: `[\p{L}\p{Nd} .#()-]` | Igual |
| Apóstrofo | Literal `'` dentro de la clase | Igual. En SQL se escribe `''` |
| Mayúsculas y minúsculas | Patrón explícito (`A-Z`, `a-z`). No usar el flag `CASE_INSENSITIVE` | Igual. No usar el flag `i` |
| PostgreSQL `~` | Búsqueda parcial salvo que se ancle. Se usan `^` y `$`; `$` solo coincide al final de la cadena | Igual. Los patrones con `\p{...}` no son seguros en PostgreSQL (verificar soporte en PG 18). En BD se valida longitud y ASCII; el Unicode se valida en la aplicación |

Orden de validación de un texto (siempre el mismo):

1. Tipo JSON (sin coerción).
2. Tamaño en bytes del cuerpo (antes del parse).
3. Normalización (NFC, CRLF, recorte, colapso si aplica).
4. Caracteres prohibidos (sección 1).
5. Longitud en puntos de código Unicode, no en bytes ni en unidades UTF-16 (usar `codePointCount`).
6. Líneas (si aplica).
7. Patrón.
8. Reglas de negocio (por ejemplo `FUTURE_TIMESTAMP`).

## 6. Casos de prueba maliciosos

Cada caso es una prueba (Testcontainers o pruebas de unidad, según el caso). Los nombres de las pruebas van en inglés.

| Caso | Entrada | Campo | Resultado esperado |
|---|---|---|---|
| Cadena larga | 10.000 caracteres `a` | `full_name` | `VALIDATION_ERROR` con `FIELD_TOO_LONG` (cuerpo ≤ 64 KiB, así que no es `PAYLOAD_TOO_LARGE`) |
| Cuerpo enorme | 100 KiB de JSON | `POST /readings` | `413 PAYLOAD_TOO_LARGE` antes de parsear |
| Bidi (Trojan Source) | `"Juan‮ecaf"` | `full_name` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| Bidi en nota | `"Ok⁧ texto"` | `note` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| NUL | `"Ana\u0000"` | `full_name`, `note` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| Ancho cero | `"Ana​Maria"` | `full_name` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| Inyección SQL | `Robert'); DROP TABLE iam.users;--` | `full_name` | `FIELD_FORMAT_INVALID` (`;`, `(` y `)` no están en `PERSON_NAME`) y, si pasara, la consulta sigue parametrizada |
| Inyección SQL en filtro | `tank_id=1 OR 1=1` | `GET /readings` | `FIELD_FORMAT_INVALID` |
| HTML y script | `<script>alert(1)</script>` | `note` | Se guarda como texto (la nota admite `<`). La UI lo muestra escapado. Prueba de frontend: el texto aparece literal |
| HTML y script | `<img src=x onerror=alert(1)>` | `full_name` | `FIELD_FORMAT_INVALID` |
| Emoji en nombre | `"Ana"` seguido de U+1F600 (emoji) | `full_name` | `FIELD_FORMAT_INVALID` |
| Emoji en nota | `"Llegó"` seguido de U+1F4A7 (emoji) | `note` | Aceptado (verificar la decisión) |
| Número como texto | `"gauge_value": "2.1"` | `POST /readings` | `VALIDATION_ERROR` |
| Booleano como texto | `"damage_noticed": "false"` | `POST /readings` | `VALIDATION_ERROR` |
| Entero como texto en JSON | `"limit": "20"` en el cuerpo de un `POST` | `POST` con paginación en cuerpo (no aplica hoy) | `VALIDATION_ERROR`. En query, `limit` es texto por naturaleza: `limit=20` es válido y `limit=abc` da `FIELD_FORMAT_INVALID` |
| Campo extra | `"role_code": "BOARD_ADMIN"` en un `PATCH` de usuario | `PATCH /users/{id}` | `VALIDATION_ERROR` (campo desconocido) |
| Escalación de rol | `"status": "ACTIVE"` en un alta | `POST /users` | `VALIDATION_ERROR` |
| Profundidad | JSON anidado de 11 niveles | Cualquier `POST` | `VALIDATION_ERROR` |
| Lista de 101 elementos | `items` con 101 lecturas | `POST /readings/batch` | `VALIDATION_ERROR` con `FIELD_TOO_MANY_ITEMS` |
| Claves duplicadas | `{"gauge_value": 1, "gauge_value": 8}` | `POST /readings` | `VALIDATION_ERROR` (no gana el último valor) |
| NFC | `"José"` (e + acento combinado) | `full_name` | Se normaliza a `José` (NFC) y cuenta 4 caracteres |
| NFKC en contraseña | `"Ｐａｓｓｗｏｒｄ"` (ancho completo) | `new_password` | Se normaliza a ASCII antes del hash y de la lista de comunes |
| Solo espacios | `"   "` | `full_name` | `FIELD_TOO_SHORT` (después del recorte) |
| Espacios internos | `"Ana      Maria"` | `full_name` | Se guarda `Ana Maria` |
| Separador de línea | `"Ana Maria"` | `full_name` | `FIELD_CHARACTERS_NOT_ALLOWED` |
| Surrogate suelto | `"\uD800"` | cualquier texto | `VALIDATION_ERROR` o `FIELD_CHARACTERS_NOT_ALLOWED` (verificar qué captura el parser) |
| Número fuera de rango | `1e309` | `gauge_value` | `VALIDATION_ERROR` |
| Número con demasiados decimales | `2.345` | `gauge_value` | `FIELD_FORMAT_INVALID` |
| Instante sin zona | `"2026-10-09T10:00:00"` | `observed_at` | `FIELD_FORMAT_INVALID` |
| Instante futuro | `now + 10 min` | `observed_at` | `FUTURE_TIMESTAMP` |
| Instante antiguo | `now - (max_backdate_days + 1) días` | `observed_at` | `TOO_OLD` |
| Cursor manipulado | `cursor=eyJ...` alterado | `GET /readings` | `CURSOR_INVALID` |
| Límite excesivo | `limit=1000` | `GET /readings` | `FIELD_OUT_OF_RANGE` |
| UUID inválido en ruta | `/readings/abc` | `GET /readings/{id}` | `VALIDATION_ERROR` |
| Existe en otro acueducto | UUID válido de otro acueducto | `GET /readings/{id}` | `404 NOT_FOUND` (igual que si no existe) |
| Slug con mayúsculas | `Guaitarilla` | `/public/{slug}/schedule` | Se normaliza a minúsculas. Si no existe, `404 NOT_FOUND` |
| Código de seguimiento ambiguo | `0O1IL...` | `GET /public/damage-reports/{trackingCode}` | Se normaliza (`O` a `0`, `I` y `L` a `1`) antes de buscar |
| Honeypot lleno | `website: "http://spam"` | `POST /public/.../damage-reports` | Respuesta de éxito sin guardar; `HONEYPOT_TRIGGERED` |
| Cabecera de firma repetida | `X-Nonce` igual a uno usado en 10 min | `POST /devices/telemetry` | `NONCE_REPLAY` (401 o 403, verificar) |
| Firma con reloj desfasado | `X-Timestamp` con 301 s de diferencia | `POST /devices/telemetry` | `INVALID_SIGNATURE` |
| Authorization enorme | Cabecera de 3 KiB | Cualquier endpoint | Se rechaza antes de parsear el JWT |
| Refresh con formato inválido | Cookie con 44 caracteres | `POST /auth/refresh` | `INVALID_CREDENTIALS` sin consultar la BD |

## 7. Pruebas obligatorias de validación

- Una prueba por fila de la sección 6 que tenga código de error (se pueden agrupar por campo con datos parametrizados).
- Prueba de deriva: cada `FieldLimits.*_MAX` coincide con el placeholder que ve Flyway y con la longitud de la columna (ver [`docs/Seguridad-de-la-base-de-datos.md`](../docs/Seguridad-de-la-base-de-datos.md)).
- Prueba de deriva: `GET /api/v1/meta/constraints` devuelve los mismos valores que `FieldLimits`.
- Prueba de paridad Java y JS: los mismos casos válidos e inválidos pasan en Java y en Zod (la prueba vive en el frontend y lee los casos de un archivo compartido, por definir).
- ArchUnit: ninguna clase de `api` define patrones de validación propios; todas usan `FieldLimits`.

## 8. Reglas para agregar un campo

1. Agregar el campo a esta tabla, con su constante. Si no hay regla en los hechos canónicos, marcarlo "por definir" y preguntar al equipo.
2. Agregar la constante en `FieldLimits` (nunca un literal en el DTO).
3. Añadir `@Size` y `@Pattern` usando la constante.
4. Si el campo se guarda en la BD, agregar el `CHECK` con el placeholder de Flyway y la prueba de deriva.
5. Agregar el caso malicioso que aplique (sección 6).
6. Si el campo es de negocio (un número que la Junta puede cambiar), no va en `FieldLimits`: va en la BD (ver [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md)).

## 9. Pendientes y contradicciones detectadas

Estas decisiones no están cerradas en los hechos canónicos. Cada una necesita una respuesta del equipo antes de cerrar la implementación.

1. **Rango de `forecast_horizon_days`.** La sección 12 lo trata como parámetro de negocio (1 a 3). La sección 14 fija las métricas de evaluación en 1, 2 y 3 días. Propuesta: la API acepta 1 a 3 (`FORECAST_HORIZON_DAYS_MAX = 3`) como límite de producto, y la BD guarda el valor de la regla.
2. **Valores por definir**: longitud del código de restablecimiento, formato del código TOTP y de respaldo, rango de `gauge_min` y `gauge_max`, máximo de `households_count`, `capacity_liters`, ventana de comandos manuales, límite de `period_to` en actas, y límites de `expires_at` en las autorizaciones de resúmenes.
3. **Espacios en nombres.** La decisión de aceptar U+2019 (apóstrofo tipográfico) en `full_name` y normalizarlo a `'` no está cerrada.
4. **Emojis en notas.** La sección 17 solo prohíbe controles en las notas, así que los emojis entran. Confirmar.
5. **Formato de `id` del cliente.** Confirmar si el cliente puede usar UUID v4 o si debe usar v7.

Relacionados: [`docs/Seguridad.md`](../docs/Seguridad.md), [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md), [`docs/Seguridad-de-la-base-de-datos.md`](../docs/Seguridad-de-la-base-de-datos.md), [`.agents/security.md`](security.md)
