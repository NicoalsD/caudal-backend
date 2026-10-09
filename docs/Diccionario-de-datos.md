# Diccionario de datos

> Documento generado desde la especificación del esquema. No se edita a mano: si cambia una columna, se cambia la especificación y se regenera junto con la ERD.

PostgreSQL 18 · 7 esquemas · 57 tablas. Convenciones: nombres en inglés y `snake_case`; IDs `uuidv7()`; fechas `timestamptz` en UTC; los tamaños `${...}` son placeholders de Flyway que salen de `FieldLimits` (única fuente de los límites técnicos).

**Implementación:** las tablas se crean con las migraciones Flyway `V2` a `V38` (generadas de esta misma especificación) y están desplegadas en Neon (rama `production`, base `caudal`). Además de lo que lista cada tabla, la base agrega: llaves foráneas compuestas `(x_id, aqueduct_id)` cuando las dos tablas tienen acueducto (una FK no pasa por RLS), `UNIQUE (id, aqueduct_id)` en esas tablas, índices en cada llave foránea y la vista `ops.effective_readings`. Detalle en [Seguridad de la base de datos](Seguridad-de-la-base-de-datos.md), sección 13.

**Clasificación de datos:** P = pública, I = interna, C = confidencial, S = secreta (nunca sale en respuestas, logs ni exportes; sin lectura para `caudal_readonly`).

![Modelo de datos](images/modelo-de-datos.png)

## Índice

- **`iam`** (16): Identidad, acceso y seguridad de las cuentas. [`users`](#iamusers), [`roles`](#iamroles), [`permissions`](#iampermissions), [`role_permissions`](#iamrolepermissions), [`memberships`](#iammemberships), [`refresh_tokens`](#iamrefreshtokens), [`login_attempts`](#iamloginattempts), [`password_history`](#iampasswordhistory), [`password_reset_grants`](#iampasswordresetgrants), [`mfa_factors`](#iammfafactors), [`mfa_recovery_codes`](#iammfarecoverycodes), [`api_keys`](#iamapikeys), [`privacy_notice_versions`](#iamprivacynoticeversions), [`privacy_acceptances`](#iamprivacyacceptances), [`security_events`](#iamsecurityevents), [`rate_limit_buckets`](#iamratelimitbuckets)
- **`org`** (10): Organización del acueducto y reglas versionadas de la Junta. [`aqueducts`](#orgaqueducts), [`tanks`](#orgtanks), [`sectors`](#orgsectors), [`valves`](#orgvalves), [`catalog_items`](#orgcatalogitems), [`rule_sets`](#orgrulesets), [`rule_level_bands`](#orgrulelevelbands), [`rule_sector_settings`](#orgrulesectorsettings), [`rule_valve_orders`](#orgrulevalveorders), [`rule_operating_windows`](#orgruleoperatingwindows)
- **`ops`** (18): Operación diaria (lecturas, pronósticos, turnos, publicación, cierre e incidentes). [`sync_batches`](#opssyncbatches), [`readings`](#opsreadings), [`reading_issues`](#opsreadingissues), [`reading_corrections`](#opsreadingcorrections), [`anomalies`](#opsanomalies), [`forecast_runs`](#opsforecastruns), [`forecast_points`](#opsforecastpoints), [`forecast_evaluations`](#opsforecastevaluations), [`schedule_proposals`](#opsscheduleproposals), [`schedule_items`](#opsscheduleitems), [`schedule_item_reasons`](#opsscheduleitemreasons), [`proposal_snapshots`](#opsproposalsnapshots), [`proposal_decisions`](#opsproposaldecisions), [`publications`](#opspublications), [`shift_executions`](#opsshiftexecutions), [`day_closures`](#opsdayclosures), [`incidents`](#opsincidents), [`incident_status_history`](#opsincidentstatushistory)
- **`reporting`** (2): Actas y resúmenes autorizados para entidades de apoyo. [`minutes`](#reportingminutes), [`summary_shares`](#reportingsummaryshares)
- **`devices`** (6): Dispositivos de campo (reales a futuro, simulados por ahora). [`devices`](#devicesdevices), [`device_keys`](#devicesdevicekeys), [`device_nonces`](#devicesdevicenonces), [`telemetry_points`](#devicestelemetrypoints), [`valve_commands`](#devicesvalvecommands), [`valve_command_events`](#devicesvalvecommandevents)
- **`audit`** (4): Auditoría y retención. [`audit_log`](#auditauditlog), [`audit_anchors`](#auditauditanchors), [`data_access_log`](#auditdataaccesslog), [`retention_policies`](#auditretentionpolicies)
- **`sim`** (1): Trazabilidad de los datos simulados. [`import_batches`](#simimportbatches)

## Esquema `iam`

Identidad, acceso y seguridad de las cuentas.

<a id="iamusers"></a>
### `iam.users`

Cuentas de personas que inician sesión (Junta, fontanero, equipo, entidades).

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `username` | `varchar(${username_max})` | NOT NULL; UNIQUE; CHECK: FieldLimits.USERNAME (minúsculas, regex) | I | Nombre de usuario para iniciar sesión. |
| `full_name` | `varchar(${person_name_max})` | NOT NULL; CHECK: FieldLimits.PERSON_NAME | C | Nombre completo; nunca se muestra en lo público. |
| `password_hash` | `varchar(255)` | NOT NULL | S | Hash Argon2id en formato PHC. Sin permiso de lectura para caudal_readonly. |
| `status` | `varchar(20)` | NOT NULL; por defecto `'ACTIVE'`; CHECK: IN (ACTIVE, LOCKED, DISABLED) | I | Estado de la cuenta. |
| `must_change_password` | `boolean` | NOT NULL; por defecto `true` | I | Obliga a cambiar la contraseña al ingresar. |
| `password_changed_at` | `timestamptz` |  | I | Último cambio de contraseña. |
| `failed_login_count` | `integer` | NOT NULL; por defecto `0`; CHECK: >= 0 | I | Fallos consecutivos de inicio de sesión. |
| `locked_until` | `timestamptz` |  | I | Fin del bloqueo temporal. |
| `lockout_level` | `smallint` | NOT NULL; por defecto `0`; CHECK: >= 0 | I | Nivel de escalamiento del bloqueo. |
| `token_version` | `integer` | NOT NULL; por defecto `0` | I | Se incrementa para invalidar todos los JWT. |
| `last_login_at` | `timestamptz` |  | I | Último ingreso exitoso. |
| `last_login_ip_hmac` | `varchar(64)` |  | C | HMAC de la IP del último ingreso. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |
| `created_by` | `uuid` | FK → `iam.users` | I | Quién creó la cuenta. |
| `updated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Última modificación (trigger). |
| `disabled_at` | `timestamptz` |  | I | Desactivación. |

<a id="iamroles"></a>
### `iam.roles`

Catálogo de roles del sistema.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `code` | `varchar(30)` | PK; CHECK: IN (BOARD_ADMIN, BOARD_MEMBER, OPERATOR, PROJECT_TEAM, SUPPORT_ENTITY) | P | Código del rol. |
| `label_es` | `varchar(60)` | NOT NULL | P | Nombre visible en español. |
| `description_es` | `varchar(255)` | NOT NULL | P | Descripción. |

<a id="iampermissions"></a>
### `iam.permissions`

Permisos finos que el código verifica.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `code` | `varchar(50)` | PK; CHECK: ^[A-Z][A-Z0-9_]*$ | P | Código del permiso (READING_CREATE, RULESET_ACTIVATE, ...). |
| `description_es` | `varchar(255)` | NOT NULL | P | Descripción. |

<a id="iamrolepermissions"></a>
### `iam.role_permissions`

Matriz rol-permiso guardada en datos (sin quemar en código).

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `role_code` | `varchar(30)` | PK; FK → `iam.roles` | P | Rol. |
| `permission_code` | `varchar(50)` | PK; FK → `iam.permissions` | P | Permiso. |

<a id="iammemberships"></a>
### `iam.memberships`

Pertenencia de un usuario a un acueducto con un rol y una vigencia. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `role_code` | `varchar(30)` | NOT NULL; FK → `iam.roles` | I | Rol en ese acueducto. |
| `valid_from` | `timestamptz` | NOT NULL; por defecto `now()` | I | Inicio de vigencia. |
| `valid_to` | `timestamptz` | CHECK: valid_to > valid_from | I | Fin de vigencia. |
| `granted_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién otorgó la membresía. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="iamrefreshtokens"></a>
### `iam.refresh_tokens`

Sesiones (refresh tokens opacos con rotación y detección de reutilización).

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Dueño de la sesión. |
| `family_id` | `uuid` | NOT NULL | I | Familia de rotación. |
| `token_hash` | `char(64)` | NOT NULL; UNIQUE | S | SHA-256 del token; el token nunca se guarda. |
| `issued_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Emisión. |
| `expires_at` | `timestamptz` | NOT NULL; CHECK: expires_at > issued_at | I | Expiración. |
| `last_used_at` | `timestamptz` |  | I | Último uso. |
| `revoked_at` | `timestamptz` |  | I | Revocación. |
| `revoked_reason` | `varchar(30)` | CHECK: IN (ROTATED, REUSE_DETECTED, LOGOUT, LOGOUT_ALL, PASSWORD_CHANGED, ROLE_CHANGED, ADMIN) | I | Motivo. |
| `replaced_by_id` | `uuid` | FK → `iam.refresh_tokens` | I | Token que lo reemplazó. |
| `created_ip_hmac` | `varchar(64)` |  | C | HMAC de la IP. |
| `user_agent` | `varchar(200)` |  | I | Agente de usuario recortado. |

<a id="iamloginattempts"></a>
### `iam.login_attempts`

Intentos de inicio de sesión para bloqueo y monitoreo (retención limitada). (append-only)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `bigint` | PK; identity | I | Identificador. |
| `user_id` | `uuid` | FK → `iam.users` | I | Usuario si existe. |
| `username_hmac` | `varchar(64)` | NOT NULL | C | HMAC del usuario intentado (no se guarda texto arbitrario). |
| `ip_hmac` | `varchar(64)` | NOT NULL | C | HMAC de la IP. |
| `succeeded` | `boolean` | NOT NULL | I | Resultado. |
| `failure_reason` | `varchar(30)` | CHECK: IN (INVALID_CREDENTIALS, LOCKED, DISABLED, RATE_LIMITED, MFA_FAILED) | I | Motivo del fallo. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="iampasswordhistory"></a>
### `iam.password_history`

Hashes anteriores para impedir reutilizar contraseñas. (append-only)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `bigint` | PK; identity | I | Identificador. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario. |
| `password_hash` | `varchar(255)` | NOT NULL | S | Hash Argon2id anterior. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="iampasswordresetgrants"></a>
### `iam.password_reset_grants`

Códigos de un solo uso que emite la Junta para restablecer una contraseña.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario beneficiado. |
| `code_hash` | `char(64)` | NOT NULL; UNIQUE | S | SHA-256 del código (el código se muestra una sola vez). |
| `issued_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién lo emitió. |
| `expires_at` | `timestamptz` | NOT NULL | I | Expiración corta (configurable). |
| `used_at` | `timestamptz` |  | I | Uso. |
| `revoked_at` | `timestamptz` |  | I | Revocación. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="iammfafactors"></a>
### `iam.mfa_factors`

Segundo factor TOTP opcional.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `user_id` | `uuid` | NOT NULL; UNIQUE; FK → `iam.users` | I | Usuario. |
| `type` | `varchar(10)` | NOT NULL; por defecto `'TOTP'`; CHECK: IN (TOTP) | I | Tipo. |
| `secret_ciphertext` | `bytea` | NOT NULL | S | Secreto cifrado con AES-256-GCM. |
| `key_id` | `varchar(40)` | NOT NULL | I | Identificador de la llave de cifrado. |
| `confirmed_at` | `timestamptz` |  | I | Confirmación. |
| `last_used_step` | `bigint` |  | I | Último paso TOTP usado (anti-repetición). |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="iammfarecoverycodes"></a>
### `iam.mfa_recovery_codes`

Códigos de respaldo del segundo factor.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario. |
| `code_hash` | `char(64)` | NOT NULL; UNIQUE | S | SHA-256 del código. |
| `used_at` | `timestamptz` |  | I | Uso. |

<a id="iamapikeys"></a>
### `iam.api_keys`

Llaves para tareas internas (por ejemplo, cron de evaluación).

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `name` | `varchar(60)` | NOT NULL; UNIQUE | I | Nombre descriptivo. |
| `key_prefix` | `varchar(12)` | NOT NULL | I | Prefijo visible para identificarla. |
| `key_hash` | `char(64)` | NOT NULL; UNIQUE | S | SHA-256 de la llave. |
| `scopes` | `varchar(200)` | NOT NULL | I | Alcances separados por espacio. |
| `expires_at` | `timestamptz` | NOT NULL | I | Expiración obligatoria. |
| `last_used_at` | `timestamptz` |  | I | Último uso. |
| `revoked_at` | `timestamptz` |  | I | Revocación. |
| `created_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Creador. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="iamprivacynoticeversions"></a>
### `iam.privacy_notice_versions`

Versiones del aviso de privacidad (Ley 1581 de 2012). (append-only)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `version` | `integer` | PK | P | Número de versión. |
| `text_es` | `text` | NOT NULL | P | Texto del aviso. |
| `text_sha256` | `char(64)` | NOT NULL | P | Hash del texto. |
| `effective_at` | `timestamptz` | NOT NULL | P | Entrada en vigencia. |

<a id="iamprivacyacceptances"></a>
### `iam.privacy_acceptances`

Aceptación del aviso de privacidad por usuario. (append-only)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `user_id` | `uuid` | PK; FK → `iam.users` | I | Usuario. |
| `version` | `integer` | PK; FK → `iam.privacy_notice_versions` | I | Versión aceptada. |
| `accepted_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |
| `ip_hmac` | `varchar(64)` |  | C | HMAC de la IP. |

<a id="iamsecurityevents"></a>
### `iam.security_events`

Eventos de seguridad para monitoreo (bloqueos, reutilización de token, firma inválida, etc.). (append-only)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `bigint` | PK; identity | I | Identificador. |
| `aqueduct_id` | `uuid` | FK → `org.aqueducts` | I | Acueducto si aplica. |
| `type` | `varchar(40)` | NOT NULL; CHECK: IN (ACCOUNT_LOCKED, TOKEN_REUSE_DETECTED, PERMISSION_DENIED, RATE_LIMITED, INVALID_SIGNATURE, NONCE_REPLAY, HONEYPOT_TRIGGERED, IMPORT_REJECTED, MFA_FAILED) | I | Tipo de evento. |
| `severity` | `varchar(10)` | NOT NULL; CHECK: IN (LOW, MEDIUM, HIGH, CRITICAL) | I | Severidad. |
| `actor_user_id` | `uuid` | FK → `iam.users` | I | Usuario involucrado. |
| `device_id` | `uuid` | FK → `devices.devices` | I | Dispositivo involucrado. |
| `ip_hmac` | `varchar(64)` |  | C | HMAC de la IP. |
| `request_id` | `varchar(40)` |  | I | Correlación con los logs. |
| `details` | `jsonb` | NOT NULL; por defecto `'{}'` | C | Detalles redactados (sin secretos). |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="iamratelimitbuckets"></a>
### `iam.rate_limit_buckets`

Estado de Bucket4j en PostgreSQL para límites compartidos entre instancias.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `varchar(200)` | PK | I | Clave del bucket (tipo:identificador). |
| `state` | `bytea` | NOT NULL | I | Estado serializado por Bucket4j. |
| `expires_at` | `bigint` |  | I | Expiración en milisegundos. |

## Esquema `org`

Organización del acueducto y reglas versionadas de la Junta.

<a id="orgaqueducts"></a>
### `org.aqueducts`

Acueductos veredales. El acueducto demo (is_demo) contiene solo datos simulados.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | P | Identificador. |
| `slug` | `varchar(${aqueduct_slug_max})` | NOT NULL; UNIQUE; CHECK: FieldLimits.AQUEDUCT_SLUG | P | Identificador público para URLs. |
| `name` | `varchar(${aqueduct_name_max})` | NOT NULL; CHECK: FieldLimits.AQUEDUCT_NAME | P | Nombre. |
| `municipality` | `varchar(60)` | NOT NULL | P | Municipio (Guaitarilla). |
| `department` | `varchar(60)` | NOT NULL | P | Departamento (Nariño). |
| `timezone` | `varchar(40)` | NOT NULL; por defecto `'America/Bogota'` | P | Zona horaria IANA. |
| `is_demo` | `boolean` | NOT NULL; por defecto `false` | P | Acueducto con datos simulados. |
| `public_page_enabled` | `boolean` | NOT NULL; por defecto `true` | P | Página pública activa. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |
| `updated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Última modificación. |

<a id="orgtanks"></a>
### `org.tanks`

Tanques y el rango de su regla pintada (fuente única del rango válido de una lectura). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `name` | `varchar(${place_name_max})` | NOT NULL; CHECK: FieldLimits.PLACE_NAME | P | Nombre. |
| `gauge_min` | `numeric(6,2)` | NOT NULL | P | Valor mínimo de la regla. |
| `gauge_max` | `numeric(6,2)` | NOT NULL; CHECK: gauge_max > gauge_min | P | Valor máximo de la regla. |
| `gauge_step` | `numeric(4,2)` | NOT NULL; CHECK: gauge_step > 0 | P | Precisión de lectura. |
| `capacity_liters` | `integer` | CHECK: > 0 | I | Capacidad (opcional). |
| `is_active` | `boolean` | NOT NULL; por defecto `true` | I | Activo. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |
| `updated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Última modificación. |

<a id="orgsectors"></a>
### `org.sectors`

Sectores que reciben agua por turnos (jerarquía para el patrón Composite). Sin datos personales. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | P | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `parent_sector_id` | `uuid` | FK → `org.sectors` | P | Sector padre. |
| `code` | `varchar(20)` | NOT NULL; CHECK: ^[A-Z0-9-]+$ | P | Código corto, único por acueducto. |
| `name` | `varchar(${place_name_max})` | NOT NULL; CHECK: FieldLimits.PLACE_NAME | P | Nombre visible. |
| `households_count` | `integer` | NOT NULL; por defecto `0`; CHECK: >= 0 | I | Número de hogares (agregado). |
| `is_active` | `boolean` | NOT NULL; por defecto `true` | P | Activo. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |
| `updated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Última modificación. |

<a id="orgvalves"></a>
### `org.valves`

Válvulas físicas de cada sector. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `sector_id` | `uuid` | NOT NULL; FK → `org.sectors` | I | Sector. |
| `code` | `varchar(${valve_code_max})` | NOT NULL; CHECK: FieldLimits.VALVE_CODE | I | Código, único por sector. |
| `name` | `varchar(${place_name_max})` | NOT NULL; CHECK: FieldLimits.PLACE_NAME | I | Nombre. |
| `location_hint` | `varchar(${location_hint_max})` | CHECK: FieldLimits.LOCATION_HINT | I | Referencia de ubicación. |
| `is_motorized` | `boolean` | NOT NULL; por defecto `false` | I | Tiene actuador. |
| `fail_safe_position` | `varchar(10)` | NOT NULL; por defecto `'KEEP'`; CHECK: IN (KEEP, OPEN, CLOSED) | I | Posición segura ante fallas. |
| `is_active` | `boolean` | NOT NULL; por defecto `true` | I | Activa. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="orgcatalogitems"></a>
### `org.catalog_items`

Opciones de listas (aspecto del agua, categorías de daño) con etiqueta en español.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | P | Identificador. |
| `aqueduct_id` | `uuid` | FK → `org.aqueducts` | P | Nulo = catálogo global. |
| `catalog` | `varchar(40)` | NOT NULL; CHECK: IN (WATER_APPEARANCE, DAMAGE_CATEGORY) | P | Catálogo. |
| `code` | `varchar(${catalog_code_max})` | NOT NULL; CHECK: FieldLimits.CATALOG_CODE | P | Código en inglés. |
| `label_es` | `varchar(${catalog_label_max})` | NOT NULL; CHECK: FieldLimits.CATALOG_LABEL | P | Etiqueta visible. |
| `sort_order` | `smallint` | NOT NULL; por defecto `0` | P | Orden. |
| `is_active` | `boolean` | NOT NULL; por defecto `true` | P | Activo. |

<a id="orgrulesets"></a>
### `org.rule_sets`

Versiones de las reglas de la Junta. Inmutables una vez activas (trigger). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `version` | `integer` | NOT NULL; CHECK: > 0 | P | Versión (única por acueducto). |
| `status` | `varchar(12)` | NOT NULL; por defecto `'DRAFT'`; CHECK: IN (DRAFT, ACTIVE, SUPERSEDED, DISCARDED) | P | Estado. |
| `based_on_rule_set_id` | `uuid` | FK → `org.rule_sets` | I | Versión de la que se copió (Prototype). |
| `valid_from` | `timestamptz` |  | P | Inicio de vigencia. |
| `valid_to` | `timestamptz` |  | P | Fin de vigencia. |
| `reserve_level` | `numeric(6,2)` | NOT NULL | P | Reserva mínima en unidades de la regla. |
| `max_daily_service_hours` | `numeric(4,2)` | NOT NULL; CHECK: > 0 AND <= HOURS_PER_DAY | P | Máximo de horas de servicio por día. |
| `min_shift_hours` | `numeric(4,2)` | NOT NULL; CHECK: > 0 | P | Duración mínima de un turno. |
| `max_shift_hours` | `numeric(4,2)` | NOT NULL; CHECK: >= min_shift_hours | P | Duración máxima de un turno. |
| `reserve_policy` | `varchar(30)` | NOT NULL; por defecto `'REDUCE_TO_CRITICAL_BAND'`; CHECK: IN (NONE, REDUCE_TO_CRITICAL_BAND, PRIORITY_ONLY) | P | Qué hacer si el p10 del pronóstico cae bajo la reserva. |
| `allocation_strategy` | `varchar(40)` | NOT NULL; por defecto `'PRIORITY_THEN_LONGEST_WAIT'`; CHECK: IN (PRIORITY_THEN_LONGEST_WAIT, EQUAL_SPLIT) | P | Estrategia de asignación. |
| `duplicate_window_minutes` | `integer` | NOT NULL; CHECK: > 0 | I | Ventana para detectar lecturas repetidas. |
| `max_level_change_per_hour` | `numeric(6,2)` | NOT NULL; CHECK: > 0 | I | Salto brusco máximo. |
| `stale_reading_hours` | `integer` | NOT NULL; CHECK: > 0 | I | Horas tras las que un dato se considera viejo. |
| `max_backdate_days` | `integer` | NOT NULL; CHECK: > 0 | I | Días máximos hacia atrás para una lectura. |
| `forecast_horizon_days` | `smallint` | NOT NULL; CHECK: > 0 | P | Horizonte de pronóstico (1 a 3). |
| `forecast_context_days` | `smallint` | NOT NULL; CHECK: > 0 | I | Días de historia enviados a la IA. |
| `trend_threshold_per_day` | `numeric(6,2)` | NOT NULL; CHECK: >= 0 | I | Umbral para decir "viene bajando". |
| `change_reason` | `varchar(${reason_max})` | CHECK: obligatorio si status <> DRAFT; FieldLimits.REASON | I | Motivo del cambio (se exige al activar). |
| `created_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Autor del borrador. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |
| `activated_by` | `uuid` | FK → `iam.users` | I | Quién la activó. |
| `activated_at` | `timestamptz` |  | I | Activación. |

<a id="orgrulelevelbands"></a>
### `org.rule_level_bands`

Bandas de nivel con horas de servicio. Sin solapes (EXCLUDE con numrange). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `rule_set_id` | `uuid` | NOT NULL; FK → `org.rule_sets` | I | Versión de reglas. |
| `band` | `varchar(10)` | NOT NULL; CHECK: IN (HIGH, LOW, CRITICAL) | P | Banda. |
| `min_level` | `numeric(6,2)` | NOT NULL | P | Nivel mínimo (incluido). |
| `max_level` | `numeric(6,2)` | NOT NULL; CHECK: max_level > min_level | P | Nivel máximo (excluido, salvo la banda superior, que incluye gauge_max). |
| `daily_service_hours` | `numeric(4,2)` | NOT NULL; CHECK: >= 0 | P | Horas de servicio al día. |
| `priority_only` | `boolean` | NOT NULL; por defecto `false` | P | Solo sectores prioritarios. |

<a id="orgrulesectorsettings"></a>
### `org.rule_sector_settings`

Prioridad e inclusión de cada sector por versión de reglas. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `rule_set_id` | `uuid` | PK; FK → `org.rule_sets` | I | Versión. |
| `sector_id` | `uuid` | PK; FK → `org.sectors` | I | Sector. |
| `is_included` | `boolean` | NOT NULL; por defecto `true` | P | Recibe turnos. |
| `is_priority` | `boolean` | NOT NULL; por defecto `false` | P | Prioritario (por ejemplo, la escuela). |
| `priority_rank` | `smallint` | CHECK: > 0 | P | Orden entre prioritarios. |

<a id="orgrulevalveorders"></a>
### `org.rule_valve_orders`

Orden de apertura de las válvulas por versión de reglas. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `rule_set_id` | `uuid` | PK; FK → `org.rule_sets` | I | Versión. |
| `valve_id` | `uuid` | PK; FK → `org.valves` | I | Válvula. |
| `sequence_order` | `smallint` | NOT NULL; CHECK: > 0 | P | Posición en la secuencia. |

<a id="orgruleoperatingwindows"></a>
### `org.rule_operating_windows`

Horarios en los que se puede operar, por versión de reglas. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `rule_set_id` | `uuid` | NOT NULL; FK → `org.rule_sets` | I | Versión. |
| `day_of_week` | `smallint` | CHECK: BETWEEN 1 AND 7 | P | Día (nulo = todos). |
| `start_time` | `time` | NOT NULL | P | Inicio. |
| `end_time` | `time` | NOT NULL; CHECK: end_time > start_time | P | Fin. |

## Esquema `ops`

Operación diaria (lecturas, pronósticos, turnos, publicación, cierre e incidentes).

<a id="opssyncbatches"></a>
### `ops.sync_batches`

Lotes de sincronización enviados por la app sin conexión. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK | I | UUID generado por el cliente (idempotencia). |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién sincronizó. |
| `received_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Recepción. |
| `items_received` | `integer` | NOT NULL; CHECK: >= 0 | I | Elementos recibidos. |
| `items_accepted` | `integer` | NOT NULL; CHECK: >= 0 | I | Aceptados. |
| `items_rejected` | `integer` | NOT NULL; CHECK: >= 0 | I | Rechazados. |

<a id="opsreadings"></a>
### `ops.readings`

Lecturas del tanque (append-only). Estatus epistémico OBSERVED. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK | I | UUID generado por el cliente (idempotencia). |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `tank_id` | `uuid` | NOT NULL; FK → `org.tanks` | I | Tanque. |
| `gauge_value` | `numeric(6,2)` | NOT NULL; CHECK: trigger contra tanks.gauge_min/gauge_max | I | Valor leído en la regla. |
| `water_appearance_code` | `varchar(${catalog_code_max})` | NOT NULL | I | Aspecto del agua (catálogo). |
| `damage_noticed` | `boolean` | NOT NULL; por defecto `false` | I | Se notó algún daño. |
| `note` | `varchar(${note_max})` | CHECK: FieldLimits.NOTE | I | Nota libre. |
| `observed_at` | `timestamptz` | NOT NULL | I | Momento de la observación (reloj del celular). |
| `received_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Llegada al servidor. |
| `source` | `varchar(12)` | NOT NULL; CHECK: IN (MANUAL_APP, SENSOR, IMPORT) | I | Origen. |
| `recorded_by` | `uuid` | FK → `iam.users` | I | Usuario (nulo si viene de un sensor). |
| `device_id` | `uuid` | FK → `devices.devices` | I | Dispositivo si aplica. |
| `sync_batch_id` | `uuid` | FK → `ops.sync_batches` | I | Lote offline si aplica. |
| `import_batch_id` | `uuid` | FK → `sim.import_batches` | I | Importación si aplica. |
| `rule_set_id` | `uuid` | NOT NULL; FK → `org.rule_sets` | I | Reglas vigentes al validar. |
| `validation_status` | `varchar(20)` | NOT NULL; CHECK: IN (ACCEPTED, FLAGGED) | I | Resultado de la validación (los datos inválidos se rechazan con 422 y no se guardan). |

<a id="opsreadingissues"></a>
### `ops.reading_issues`

Problemas que detectó la cadena de validación. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `reading_id` | `uuid` | NOT NULL; FK → `ops.readings` | I | Lectura. |
| `issue_code` | `varchar(30)` | NOT NULL; CHECK: IN (DUPLICATE, SUDDEN_JUMP) | I | Código de la marca. |
| `severity` | `varchar(10)` | NOT NULL; CHECK: IN (INFO, WARNING) | I | Severidad. |
| `details` | `jsonb` | NOT NULL; por defecto `'{}'` | I | Parámetros (valor, rango, ventana). |

<a id="opsreadingcorrections"></a>
### `ops.reading_corrections`

Correcciones sin borrar el dato original (la vista effective_readings usa la última). (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `reading_id` | `uuid` | NOT NULL; FK → `ops.readings` | I | Lectura corregida. |
| `corrected_gauge_value` | `numeric(6,2)` | NOT NULL | I | Valor corregido. |
| `reason` | `varchar(${reason_max})` | NOT NULL; CHECK: FieldLimits.REASON | I | Motivo. |
| `corrected_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién corrigió. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsanomalies"></a>
### `ops.anomalies`

Sospechas inferidas por reglas (estatus INFERRED hasta confirmarse). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `kind` | `varchar(30)` | NOT NULL; CHECK: IN (POSSIBLE_LEAK, SENSOR_FAULT, STALE_DATA, SUSPECTED_DUPLICATE, ABNORMAL_DROP) | I | Tipo. |
| `detected_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Detección. |
| `evidence` | `jsonb` | NOT NULL; por defecto `'{}'` | I | Evidencia (lecturas, pendiente). |
| `status` | `varchar(12)` | NOT NULL; por defecto `'OPEN'`; CHECK: IN (OPEN, CONFIRMED, DISMISSED) | I | Estado. |
| `resolved_by` | `uuid` | FK → `iam.users` | I | Quién resolvió. |
| `resolved_at` | `timestamptz` |  | I | Resolución. |
| `resolution_note` | `varchar(${note_max})` | CHECK: FieldLimits.NOTE | I | Nota. |
| `related_incident_id` | `uuid` | FK → `ops.incidents` | I | Incidente relacionado. |

<a id="opsforecastruns"></a>
### `ops.forecast_runs`

Cada pronóstico calculado (IA o estimación simple). Estatus ESTIMATED. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `tank_id` | `uuid` | NOT NULL; FK → `org.tanks` | I | Tanque. |
| `model_name` | `varchar(60)` | NOT NULL | I | Modelo (chronos-2-small, naive-persistence). |
| `model_version` | `varchar(40)` | NOT NULL | I | Versión. |
| `is_fallback` | `boolean` | NOT NULL | I | Fue la estimación simple de respaldo. |
| `fallback_reason` | `varchar(30)` | CHECK: IN (IA_TIMEOUT, CIRCUIT_OPEN, IA_ERROR, INSUFFICIENT_HISTORY, SHADOW_BASELINE) | I | Motivo del respaldo. |
| `horizon_days` | `smallint` | NOT NULL; CHECK: > 0 | I | Horizonte. |
| `context_from` | `timestamptz` | NOT NULL | I | Inicio de la historia usada. |
| `context_to` | `timestamptz` | NOT NULL | I | Fin de la historia usada. |
| `input_points` | `integer` | NOT NULL; CHECK: >= 0 | I | Puntos enviados. |
| `latency_ms` | `integer` | CHECK: >= 0 | I | Latencia. |
| `rule_set_id` | `uuid` | NOT NULL; FK → `org.rule_sets` | I | Reglas vigentes. |
| `requested_by` | `uuid` | FK → `iam.users` | I | Usuario o nulo si fue el sistema. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsforecastpoints"></a>
### `ops.forecast_points`

Cuantiles p10, p50 y p90 por día pronosticado. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `forecast_run_id` | `uuid` | PK; FK → `ops.forecast_runs` | I | Pronóstico. |
| `target_date` | `date` | PK | I | Día pronosticado. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `p10` | `numeric(6,2)` | NOT NULL | I | Cuantil 10 %. |
| `p50` | `numeric(6,2)` | NOT NULL; CHECK: p50 >= p10 | I | Mediana (lo más probable). |
| `p90` | `numeric(6,2)` | NOT NULL; CHECK: p90 >= p50 | I | Cuantil 90 %. |

<a id="opsforecastevaluations"></a>
### `ops.forecast_evaluations`

Error de cada punto cuando llega el dato real (IA y estimación simple). (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `forecast_run_id` | `uuid` | NOT NULL; FK → `ops.forecast_runs` | I | Pronóstico. |
| `target_date` | `date` | NOT NULL | I | Día. |
| `actual_reading_id` | `uuid` | NOT NULL; FK → `ops.readings` | I | Lectura real usada. |
| `actual_value` | `numeric(6,2)` | NOT NULL | I | Valor real. |
| `abs_error` | `numeric(6,2)` | NOT NULL; CHECK: >= 0 | I | Error absoluto del p50. |
| `within_interval` | `boolean` | NOT NULL | I | El real cayó entre p10 y p90. |
| `evaluated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsscheduleproposals"></a>
### `ops.schedule_proposals`

Propuesta de turnos de un día (máquina de estados). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `service_date` | `date` | NOT NULL | P | Día de servicio. |
| `rule_set_id` | `uuid` | NOT NULL; FK → `org.rule_sets` | I | Reglas usadas. |
| `status` | `varchar(25)` | NOT NULL; por defecto `'DRAFT'`; CHECK: IN (DRAFT, PENDING_REVIEW, APPROVED, APPROVED_WITH_CHANGES, REJECTED, PUBLISHED, CLOSED) | I | Estado. |
| `tank_level_snapshot` | `numeric(6,2)` | NOT NULL | I | Nivel usado. |
| `tank_band` | `varchar(10)` | NOT NULL; CHECK: IN (HIGH, LOW, CRITICAL) | I | Banda. |
| `available_hours` | `numeric(4,2)` | NOT NULL; CHECK: >= 0 | I | Horas disponibles (ESTIMATED). |
| `forecast_run_id` | `uuid` | FK → `ops.forecast_runs` | I | Pronóstico considerado. |
| `unserved_sectors` | `jsonb` | NOT NULL; por defecto `'[]'` | P | Sectores sin turno y su motivo (código y parámetros). |
| `strategy_code` | `varchar(40)` | NOT NULL | I | Estrategia usada. |
| `generated_by` | `uuid` | FK → `iam.users` | I | Quién la generó. |
| `generated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |
| `lock_version` | `integer` | NOT NULL; por defecto `0` | I | Bloqueo optimista. |

<a id="opsscheduleitems"></a>
### `ops.schedule_items`

Turnos por sector. Sin solapes (EXCLUDE con tstzrange). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `proposal_id` | `uuid` | NOT NULL; FK → `ops.schedule_proposals` | I | Propuesta. |
| `sector_id` | `uuid` | NOT NULL; FK → `org.sectors` | P | Sector. |
| `sequence` | `smallint` | NOT NULL; CHECK: > 0 | P | Orden. |
| `start_at` | `timestamptz` | NOT NULL | P | Inicio. |
| `end_at` | `timestamptz` | NOT NULL; CHECK: end_at > start_at | P | Fin. |
| `origin` | `varchar(10)` | NOT NULL; por defecto `'PROPOSED'`; CHECK: IN (PROPOSED, MODIFIED, ADDED) | I | Origen. |

<a id="opsscheduleitemreasons"></a>
### `ops.schedule_item_reasons`

Explicaciones estructuradas de cada turno (código y parámetros). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `item_id` | `uuid` | NOT NULL; FK → `ops.schedule_items` | I | Turno. |
| `reason_code` | `varchar(30)` | NOT NULL; CHECK: IN (PRIORITY_SECTOR, HOURS_WITHOUT_SERVICE, TANK_BAND, FORECAST_RANGE, RESERVE_GUARD, BOARD_CHANGE) | P | Código. |
| `params` | `jsonb` | NOT NULL; por defecto `'{}'` | P | Parámetros (sector, horas, nivel). |
| `sort_order` | `smallint` | NOT NULL; por defecto `0` | P | Orden. |

<a id="opsproposalsnapshots"></a>
### `ops.proposal_snapshots`

Memento de la propuesta original antes de que la Junta la cambie. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `proposal_id` | `uuid` | NOT NULL; FK → `ops.schedule_proposals` | I | Propuesta. |
| `snapshot` | `jsonb` | NOT NULL | I | Contenido completo. |
| `taken_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsproposaldecisions"></a>
### `ops.proposal_decisions`

Decisión de la Junta (append-only). Modificar o rechazar exige motivo. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `proposal_id` | `uuid` | NOT NULL; FK → `ops.schedule_proposals` | I | Propuesta. |
| `decision` | `varchar(25)` | NOT NULL; CHECK: IN (APPROVED, APPROVED_WITH_CHANGES, REJECTED) | I | Decisión. |
| `reason` | `varchar(${reason_max})` | CHECK: obligatorio si decision <> APPROVED; FieldLimits.REASON | I | Motivo. |
| `decided_by` | `uuid` | NOT NULL; FK → `iam.users` | C | Miembro de la Junta (nunca público). |
| `decided_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opspublications"></a>
### `ops.publications`

Publicación de un horario aprobado. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `proposal_id` | `uuid` | NOT NULL; UNIQUE; FK → `ops.schedule_proposals` | I | Propuesta. |
| `published_by` | `uuid` | NOT NULL; FK → `iam.users` | C | Quién publicó. |
| `published_at` | `timestamptz` | NOT NULL; por defecto `now()` | P | Momento. |
| `whatsapp_text` | `text` | NOT NULL | P | Mensaje generado (sin datos personales). |
| `poster_sha256` | `char(64)` |  | P | Hash del cartel PDF. |

<a id="opsshiftexecutions"></a>
### `ops.shift_executions`

Lo que pasó realmente en cada turno (CONFIRMED). Se reemplaza con supersedes_id. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `schedule_item_id` | `uuid` | NOT NULL; FK → `ops.schedule_items` | I | Turno. |
| `status` | `varchar(15)` | NOT NULL; CHECK: IN (COMPLETED, PARTIAL, NOT_EXECUTED) | I | Resultado. |
| `actual_start` | `timestamptz` |  | I | Inicio real. |
| `actual_end` | `timestamptz` | CHECK: actual_end > actual_start | I | Fin real. |
| `note` | `varchar(${note_max})` | CHECK: FieldLimits.NOTE | I | Novedades. |
| `supersedes_id` | `uuid` | FK → `ops.shift_executions` | I | Registro que reemplaza. |
| `import_batch_id` | `uuid` | FK → `sim.import_batches` | I | Importación si aplica. |
| `recorded_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién registró. |
| `recorded_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsdayclosures"></a>
### `ops.day_closures`

Cierre del día de servicio. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `service_date` | `date` | NOT NULL | I | Día (único por acueducto). |
| `notes` | `varchar(${note_max})` | CHECK: FieldLimits.NOTE | I | Novedades del día. |
| `closed_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién cerró. |
| `closed_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsincidents"></a>
### `ops.incidents`

Reportes de daño (públicos, del fontanero o derivados de anomalías). Sin datos personales. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `source` | `varchar(15)` | NOT NULL; CHECK: IN (PUBLIC_FORM, OPERATOR, ANOMALY, IMPORT) | I | Origen. |
| `category_code` | `varchar(${catalog_code_max})` | NOT NULL | P | Categoría (catálogo). |
| `sector_id` | `uuid` | FK → `org.sectors` | P | Sector si se conoce. |
| `description` | `varchar(${incident_description_max})` | NOT NULL; CHECK: FieldLimits.INCIDENT_DESCRIPTION | I | Descripción. |
| `location_hint` | `varchar(${location_hint_max})` | CHECK: FieldLimits.LOCATION_HINT | I | Referencia. |
| `status` | `varchar(12)` | NOT NULL; por defecto `'REPORTED'`; CHECK: IN (REPORTED, VERIFYING, CONFIRMED, RESOLVED, DISMISSED) | P | Estado. |
| `tracking_code_hash` | `char(64)` | NOT NULL; UNIQUE | S | SHA-256 del código de seguimiento. |
| `reporter_ip_hmac` | `varchar(64)` |  | C | HMAC de la IP (control de abuso). |
| `import_batch_id` | `uuid` | FK → `sim.import_batches` | I | Importación si aplica. |
| `reported_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="opsincidentstatushistory"></a>
### `ops.incident_status_history`

Historial de estados de un incidente. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `incident_id` | `uuid` | NOT NULL; FK → `ops.incidents` | I | Incidente. |
| `from_status` | `varchar(12)` |  | I | Estado anterior. |
| `to_status` | `varchar(12)` | NOT NULL | I | Estado nuevo. |
| `note` | `varchar(${note_max})` | CHECK: FieldLimits.NOTE | I | Nota. |
| `changed_by` | `uuid` | FK → `iam.users` | I | Quién lo cambió. |
| `changed_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

## Esquema `reporting`

Actas y resúmenes autorizados para entidades de apoyo.

<a id="reportingminutes"></a>
### `reporting.minutes`

Actas por periodo con secciones observado, estimado, inferido y confirmado. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `period_from` | `date` | NOT NULL | I | Inicio del periodo. |
| `period_to` | `date` | NOT NULL; CHECK: period_to >= period_from | I | Fin del periodo. |
| `status` | `varchar(10)` | NOT NULL; por defecto `'DRAFT'`; CHECK: IN (DRAFT, FINAL) | I | Estado (FINAL es inmutable). |
| `content` | `jsonb` | NOT NULL | C | Secciones generadas. |
| `pdf_sha256` | `char(64)` |  | I | Hash del PDF final. |
| `audit_head_hash` | `char(64)` |  | I | Ancla de la cadena de auditoría. |
| `generated_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién la generó. |
| `generated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |
| `finalized_by` | `uuid` | FK → `iam.users` | I | Quién la cerró. |
| `finalized_at` | `timestamptz` |  | I | Momento del cierre. |

<a id="reportingsummaryshares"></a>
### `reporting.summary_shares`

Autorizaciones de la Junta para que una entidad vea resúmenes. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `grantee_user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario de la entidad (rol SUPPORT_ENTITY). |
| `scope` | `varchar(20)` | NOT NULL; CHECK: IN (MONTHLY_SUMMARY, MINUTES) | I | Qué puede ver. |
| `period_from` | `date` | NOT NULL | I | Desde. |
| `period_to` | `date` | NOT NULL | I | Hasta. |
| `reason` | `varchar(${reason_max})` | NOT NULL; CHECK: FieldLimits.REASON | I | Motivo. |
| `authorized_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién autorizó. |
| `authorized_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |
| `expires_at` | `timestamptz` | NOT NULL | I | Vencimiento obligatorio. |
| `revoked_at` | `timestamptz` |  | I | Revocación. |
| `revoked_by` | `uuid` | FK → `iam.users` | I | Quién revocó. |

## Esquema `devices`

Dispositivos de campo (reales a futuro, simulados por ahora).

<a id="devicesdevices"></a>
### `devices.devices`

Sensores de nivel, actuadores de válvula y gateways. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `kind` | `varchar(20)` | NOT NULL; CHECK: IN (LEVEL_SENSOR, VALVE_ACTUATOR, GATEWAY) | I | Tipo. |
| `name` | `varchar(${place_name_max})` | NOT NULL; CHECK: FieldLimits.PLACE_NAME | I | Nombre. |
| `tank_id` | `uuid` | FK → `org.tanks` | I | Tanque (sensores). |
| `valve_id` | `uuid` | FK → `org.valves` | I | Válvula (actuadores). |
| `status` | `varchar(12)` | NOT NULL; por defecto `'ACTIVE'`; CHECK: IN (ACTIVE, SUSPENDED, RETIRED) | I | Estado. |
| `firmware_version` | `varchar(30)` |  | I | Versión del firmware o del simulador. |
| `last_seen_at` | `timestamptz` |  | I | Última conexión. |
| `created_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Quién lo registró. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="devicesdevicekeys"></a>
### `devices.device_keys`

Claves públicas Ed25519 con rotación (la privada nunca llega al servidor). (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `device_id` | `uuid` | NOT NULL; FK → `devices.devices` | I | Dispositivo. |
| `public_key` | `varchar(64)` | NOT NULL; UNIQUE | I | Clave pública Ed25519 en base64url (32 bytes). |
| `fingerprint` | `char(64)` | NOT NULL; UNIQUE | I | SHA-256 de la clave. |
| `activated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Activación. |
| `revoked_at` | `timestamptz` |  | I | Revocación. |

<a id="devicesdevicenonces"></a>
### `devices.device_nonces`

Nonces ya vistos (anti-repetición); se purgan según retention_policies.

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `device_id` | `uuid` | PK; FK → `devices.devices` | I | Dispositivo. |
| `nonce` | `char(32)` | PK | I | Nonce de 128 bits en hex. |
| `seen_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="devicestelemetrypoints"></a>
### `devices.telemetry_points`

Telemetría cruda de los dispositivos (idempotente por id del punto). (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK | I | UUID generado por el dispositivo. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `device_id` | `uuid` | NOT NULL; FK → `devices.devices` | I | Dispositivo. |
| `metric` | `varchar(20)` | NOT NULL; CHECK: IN (LEVEL, BATTERY_V, RSSI, VALVE_POSITION) | I | Métrica. |
| `value` | `numeric(10,3)` | NOT NULL | I | Valor. |
| `observed_at` | `timestamptz` | NOT NULL | I | Momento de la medición. |
| `received_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Llegada. |
| `reading_id` | `uuid` | FK → `ops.readings` | I | Lectura generada a partir de este punto. |

<a id="devicesvalvecommands"></a>
### `devices.valve_commands`

Comandos de válvula, solo desde turnos aprobados o manuales con motivo. (RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `valve_id` | `uuid` | NOT NULL; FK → `org.valves` | I | Válvula. |
| `device_id` | `uuid` | NOT NULL; FK → `devices.devices` | I | Actuador. |
| `command` | `varchar(10)` | NOT NULL; CHECK: IN (OPEN, CLOSE) | I | Acción. |
| `schedule_item_id` | `uuid` | FK → `ops.schedule_items` | I | Turno aprobado que lo origina. |
| `manual_reason` | `varchar(${reason_max})` | CHECK: obligatorio si schedule_item_id es nulo | I | Motivo de un comando manual. |
| `status` | `varchar(12)` | NOT NULL; por defecto `'PENDING'`; CHECK: IN (PENDING, DELIVERED, ACKED, FAILED, EXPIRED, CANCELLED) | I | Estado. |
| `not_before` | `timestamptz` | NOT NULL | I | No ejecutar antes de. |
| `expires_at` | `timestamptz` | NOT NULL; CHECK: expires_at > not_before | I | Expiración. |
| `created_by` | `uuid` | FK → `iam.users` | I | Quién lo creó (nulo si fue el sistema). |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Creación. |

<a id="devicesvalvecommandevents"></a>
### `devices.valve_command_events`

Historial de entrega y confirmación de cada comando. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `command_id` | `uuid` | NOT NULL; FK → `devices.valve_commands` | I | Comando. |
| `event` | `varchar(12)` | NOT NULL; CHECK: IN (DELIVERED, ACKED, FAILED, EXPIRED, CANCELLED) | I | Evento. |
| `detail` | `varchar(200)` |  | I | Detalle. |
| `occurred_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

## Esquema `audit`

Auditoría y retención.

<a id="auditauditlog"></a>
### `audit.audit_log`

Registro append-only con cadena de hashes (cada fila incluye el hash de la anterior). (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `bigint` | PK; identity | I | Identificador. |
| `aqueduct_id` | `uuid` | FK → `org.aqueducts` | I | Acueducto. |
| `occurred_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |
| `actor_type` | `varchar(10)` | NOT NULL; CHECK: IN (USER, DEVICE, SYSTEM, API_KEY) | I | Tipo de actor. |
| `actor_id` | `uuid` |  | I | Actor. |
| `action` | `varchar(60)` | NOT NULL | I | Acción (RULESET_ACTIVATED, PROPOSAL_APPROVED, ...). |
| `entity_type` | `varchar(60)` | NOT NULL | I | Entidad. |
| `entity_id` | `varchar(60)` | NOT NULL | I | Identificador de la entidad. |
| `before_state` | `jsonb` |  | C | Estado anterior redactado. |
| `after_state` | `jsonb` |  | C | Estado nuevo redactado. |
| `request_id` | `varchar(40)` |  | I | Correlación. |
| `ip_hmac` | `varchar(64)` |  | C | HMAC de la IP. |
| `prev_hash` | `char(64)` | NOT NULL | I | Hash de la fila anterior del mismo acueducto. |
| `row_hash` | `char(64)` | NOT NULL; UNIQUE | I | SHA-256 de prev_hash + contenido (trigger). |

<a id="auditauditanchors"></a>
### `audit.audit_anchors`

Hash cabeza diario de la cadena (se imprime en las actas). (append-only)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `aqueduct_id` | `uuid` | PK; FK → `org.aqueducts` | I | Acueducto. |
| `anchor_date` | `date` | PK | I | Día. |
| `head_hash` | `char(64)` | NOT NULL | I | Último row_hash del día. |
| `created_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

<a id="auditdataaccesslog"></a>
### `audit.data_access_log`

Quién vio o exportó datos sensibles o autorizados. (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `bigint` | PK; identity | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto. |
| `user_id` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario. |
| `resource` | `varchar(60)` | NOT NULL | I | Recurso (MINUTES_PDF, SUMMARY, USER_LIST, AUDIT_LOG). |
| `resource_id` | `varchar(60)` |  | I | Identificador. |
| `summary_share_id` | `uuid` | FK → `reporting.summary_shares` | I | Autorización usada. |
| `accessed_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |
| `ip_hmac` | `varchar(64)` |  | C | HMAC de la IP. |

<a id="auditretentionpolicies"></a>
### `audit.retention_policies`

Días de retención por tipo de dato (parámetro en BD, no en código).

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `data_class` | `varchar(40)` | PK; CHECK: IN (LOGIN_ATTEMPTS, DEVICE_NONCES, SECURITY_EVENTS, REFRESH_TOKENS, DATA_ACCESS_LOG, TELEMETRY_POINTS) | I | Tipo de dato. |
| `retention_days` | `integer` | NOT NULL; CHECK: > 0 | I | Días. |
| `updated_by` | `uuid` | FK → `iam.users` | I | Quién lo cambió. |
| `updated_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

## Esquema `sim`

Trazabilidad de los datos simulados.

<a id="simimportbatches"></a>
### `sim.import_batches`

Importaciones de historial simulado (solo acueductos con is_demo = true). (append-only, RLS por acueducto)

| Columna | Tipo | Restricciones | Clase | Descripción |
|---|---|---|---|---|
| `id` | `uuid` | PK; por defecto `uuidv7()` | I | Identificador. |
| `aqueduct_id` | `uuid` | NOT NULL; FK → `org.aqueducts` | I | Acueducto demo (trigger valida is_demo). |
| `kind` | `varchar(20)` | NOT NULL; CHECK: IN (READINGS, SHIFT_EXECUTIONS, INCIDENTS) | I | Qué se importó. |
| `scenario_name` | `varchar(60)` | NOT NULL | I | Escenario del simulador. |
| `seed` | `bigint` | NOT NULL | I | Semilla. |
| `simulator_version` | `varchar(30)` | NOT NULL | I | Versión del simulador. |
| `rows_received` | `integer` | NOT NULL; CHECK: >= 0 | I | Filas recibidas. |
| `rows_accepted` | `integer` | NOT NULL; CHECK: >= 0 | I | Aceptadas. |
| `rows_rejected` | `integer` | NOT NULL; CHECK: >= 0 | I | Rechazadas. |
| `file_sha256` | `char(64)` | NOT NULL | I | Hash del archivo. |
| `imported_by` | `uuid` | NOT NULL; FK → `iam.users` | I | Usuario PROJECT_TEAM. |
| `imported_at` | `timestamptz` | NOT NULL; por defecto `now()` | I | Momento. |

Relacionados: [Modelo de datos](Modelo-de-datos.md) · [Seguridad de la base de datos](Seguridad-de-la-base-de-datos.md) · [Configuración sin valores quemados](Configuracion-sin-valores-quemados.md)
