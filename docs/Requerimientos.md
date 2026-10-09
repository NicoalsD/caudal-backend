# Requerimientos

Este documento lista los requisitos funcionales (RF) y no funcionales (RNF) de CAUDAL. Cada RF corresponde a una capacidad concreta y se enlaza con una historia de usuario de [Historias-de-usuario.md](Historias-de-usuario.md). Los RNF aplican a todo el sistema o a varios módulos.

Convenciones:

- Las cifras de seguridad (RNF-01 a RNF-10) son canónicas: vienen de los hechos del proyecto y no se cambian sin una decisión registrada.
- Las metas de rendimiento y usabilidad son **objetivos**, marcados "objetivo, verificar". Se confirman con mediciones en la fase 12.
- "Por definir" significa que el dato no existe todavía y debe decidirse antes de implementar esa parte.
- Repositorios: `B` = `caudal-backend`, `F` = `caudal-frontend`, `IA` = `caudal-ia`, `S` = `caudal-simulador`.

## 1. Requisitos funcionales

| ID | Requisito | Módulo | Historia | Repos |
|---|---|---|---|---|
| RF-01 | El sistema autentica con usuario y contraseña, y emite un access token de 15 minutos y un refresh token opaco. | M1 | HU-01 | B, F |
| RF-02 | Una cuenta nueva o restablecida debe cambiar su contraseña en el primer ingreso antes de usar la API. | M1 | HU-02 | B, F |
| RF-03 | El sistema bloquea una cuenta tras fallos repetidos, con bloqueo escalonado, y limita los intentos por IP. | M1 | HU-03 | B |
| RF-04 | La Junta administradora crea usuarios y membresías con rol y vigencia por acueducto. | M1 | HU-04 | B, F |
| RF-05 | Un usuario cierra sus sesiones en todos los dispositivos, y el sistema revoca familias de tokens reutilizados. | M1 | HU-05 | B, F |
| RF-06 | Los usuarios con MFA (TOTP) activan un segundo factor con códigos de respaldo de un solo uso. | M1 | HU-06 | B, F |
| RF-07 | La regla de la Junta se edita solo como borrador versionado, con bandas válidas que cubren todo el rango. | M2 | HU-07 | B, F |
| RF-08 | Una versión de reglas se activa con motivo, y una regla activa es inmutable. | M2 | HU-08 | B, F |
| RF-09 | El fontanero registra una lectura sin conexión; el envío es idempotente por UUID del cliente. | M3 | HU-09 | F, B |
| RF-10 | Las lecturas pendientes se envían por lotes al recuperar la conexión, con resultado por cada lectura. | M3 | HU-10 | B, F |
| RF-11 | Una lectura fuera de la regla no se guarda (`422 GAUGE_OUT_OF_RANGE`) y la app pide corregirla antes de reenviar. Las correcciones no borran el dato original. | M3 | HU-11 | B, F |
| RF-12 | El sistema marca duplicados y saltos bruscos de nivel como `FLAGGED`, sin ocultarlos. | M3 | HU-12 | B |
| RF-13 | Una corrección crea un registro nuevo con motivo y apunta a la lectura original. | M3 | HU-13 | B, F |
| RF-14 | El estado del tanque muestra el último nivel, su antigüedad, la tendencia y un aviso de dato viejo. | M4 | HU-14 | B, F |
| RF-15 | El pronóstico muestra el nivel más probable (p50) y el rango (p10 a p90) para 1 a 3 días. | M4 | HU-15 | B, IA, F |
| RF-16 | Si la IA falla, responde tarde o su circuito está abierto, el sistema usa la estimación simple y lo indica. | M4 | HU-16 | B, IA |
| RF-17 | La Junta confirma o descarta anomalías con motivo. | M4 | HU-17 | B, F |
| RF-18 | El sistema propone turnos y explica cada decisión con un código y sus parámetros. | M5 | HU-18 | B, F |
| RF-19 | La propuesta respeta reserva, horas máximas por turno y por día, ventana de operación y no solapa turnos. | M5 | HU-19 | B |
| RF-20 | La Junta aprueba una propuesta sin cambios. | M6 | HU-20 | B, F |
| RF-21 | La Junta modifica una propuesta y el motivo es obligatorio; la versión original queda guardada. | M6 | HU-21 | B, F |
| RF-22 | La Junta rechaza una propuesta con motivo. | M6 | HU-22 | B, F |
| RF-23 | Solo una propuesta aprobada se publica; la publicación genera los comandos de válvula de sus turnos. | M7 | HU-23 | B, F |
| RF-24 | La página pública muestra el horario publicado sin nombres, teléfonos ni identificadores de usuario. | M7 | HU-24 | B, F |
| RF-25 | El sistema genera un texto de WhatsApp con el horario y un botón lo copia. | M7 | HU-25 | B, F |
| RF-26 | El sistema genera un cartel en PDF con el horario vigente, sin datos personales. | M7 | HU-26 | B |
| RF-27 | Cualquier persona reporta un daño sin login y recibe un código de seguimiento de 8 caracteres. | M7 | HU-27 | B, F |
| RF-28 | La Junta cambia el estado de un reporte de daño por transiciones permitidas, con historial. | M7 | HU-28 | B, F |
| RF-29 | El fontanero registra la ejecución de cada turno y cierra el día una sola vez. | M8 | HU-29 | B, F |
| RF-30 | El sistema genera actas en PDF con cuatro secciones: observado, estimado, inferido y confirmado. | M9 | HU-30 | B, F |
| RF-31 | La Junta autoriza a una entidad de apoyo a ver resúmenes por un plazo obligatorio, y puede revocarlo. | M9 | HU-31 | B, F |
| RF-32 | Una entidad de apoyo ve solo los resúmenes autorizados y vigentes. | M9 | HU-32 | B, F |
| RF-33 | El equipo compara la IA con la estimación simple con MAE, WQL, cobertura y skill por horizonte. | M10 | HU-33 | B, IA, F |
| RF-34 | El equipo registra dispositivos y sus claves públicas Ed25519; la base de datos nunca guarda claves privadas. | M11 | HU-34 | B, S |
| RF-35 | Un dispositivo envía telemetría firmada, con ventana de tiempo y nonce único. | M11 | HU-35 | B, S |
| RF-36 | Las válvulas reciben comandos solo de turnos publicados (o manuales con motivo), con vigencia. | M11 | HU-36 | B, S |
| RF-37 | Quien tiene el permiso consulta la bitácora de auditoría y los eventos de seguridad, sin secretos. | M12 | HU-37 | B, F |
| RF-38 | El equipo importa historial simulado solo en el acueducto demo. | M13 | HU-38 | B, S |
| RF-39 | Todo dato simulado se marca "Datos simulados" en pantallas, PDF, mensajes y en la API (`simulated`). | M13 | HU-39 | B, F, S |
| RF-40 | Toda operación se autoriza por permiso (leído de la BD), por membresía vigente y por acueducto. | M1, M9 | HU-04, HU-32 | B |
| RF-41 | Toda entrada se valida con los límites de `FieldLimits` y la normalización de texto. | M3 | HU-09, HU-11 | B, F |

## 2. Requisitos no funcionales

### RNF-01 Seguridad de contraseñas

- Argon2id con `Argon2PasswordEncoder` (BouncyCastle): m = 19456 KiB (19 MiB), t = 2, p = 1, sal de 16 bytes, hash de 32 bytes. Los parámetros se leen de variables de entorno; si cambian, se rehace el hash al iniciar sesión.
- Longitud de 12 a 128 caracteres, normalizados NFKC. Sin reglas de composición.
- Se rechazan las contraseñas comunes (lista local) y las que contienen el usuario.
- Historial de las últimas 5 contraseñas (configurable).
- Cambio obligatorio en el primer ingreso.
- Se guardan hashes; no se cifran y no se recuperan. Nunca aparecen en logs, respuestas, URLs ni en claro en la base de datos.

### RNF-02 Tokens y sesiones

- Access token JWT HS256 de 15 minutos. Secreto de al menos 256 bits. Claims `iss`, `aud`, `sub`, `role`, `aqueduct_id`, `tv` (token_version), `jti`, `iat`, `exp`.
- Validación de `alg` explícito, `iss`, `aud`, `exp` y `nbf`, con tolerancia de 30 segundos.
- Refresh token opaco de 256 bits, guardado como SHA-256. Rotación en cada uso. Reutilizar un token revoca toda su familia.
- Cookie `caudal_rt` con `HttpOnly; Secure; SameSite=None; Path=/api/v1/auth`. Frontend y API están en sitios distintos, así que se exige la cabecera `X-Requested-With: caudal-web` y la verificación de `Origin`.
- Duración del refresh token: 7 días, y 30 días para `OPERATOR`. Configurable.
- Cambiar contraseña, rol o desactivar la cuenta incrementa `token_version` y revoca los refresh tokens.
- El access token vive solo en la memoria del frontend.
- Una cabecera `Authorization` de más de 2 KiB se rechaza antes de parsearla.

### RNF-03 Protección contra fuerza bruta y abuso

- Bloqueo por cuenta: 5 fallos en 15 minutos bloquean la cuenta 15 minutos. Cada bloqueo nuevo dura el doble, con máximo de 24 horas.
- Límite por IP en login: 20 intentos en 15 minutos.
- Rate limits configurables: login 5 por minuto por IP; reportes públicos 3 por hora por IP; API 120 por minuto por usuario; telemetría 60 por minuto por dispositivo; recálculo de pronóstico 10 por hora por acueducto.
- Formulario público con campo oculto (honeypot `website`, debe llegar vacío) y tiempo mínimo de llenado de 3 s (`PUBLIC_FORM_MIN_SECONDS`).
- Respuestas `429` con `Retry-After`.

### RNF-04 Validación de entradas

- Cuerpo JSON de hasta 64 KiB. Telemetría hasta 256 KiB. Importación hasta 1 MiB o 5.000 filas por lote.
- Profundidad JSON máxima de 10. Listas de hasta 100 elementos, salvo que el campo indique otro límite. Texto global de hasta 2.000 caracteres.
- Campos desconocidos se rechazan (`FAIL_ON_UNKNOWN_PROPERTIES`). Sin coerción de tipos.
- Normalización NFC. Recorte de espacios y colapso de espacios internos en nombres.
- Se rechazan caracteres de control (salvo salto de línea en textos largos), caracteres bidi (U+202A a U+202E, U+2066 a U+2069) y de ancho cero (U+200B a U+200F, U+FEFF).
- Identificadores en UUID. Paginación de 1 a 100 (por defecto 20) con cursor opaco firmado.
- Fechas en ISO-8601 con zona horaria, no futuras (más de 5 minutos) y no más antiguas que `max_backdate_days` de la regla, salvo la importación en el acueducto demo.
- Tabla canónica de campos (valores en `FieldLimits`):

| Campo | Mínimo | Máximo | Regla |
|---|---|---|---|
| `username` | 3 | 32 | `^[a-z0-9][a-z0-9._-]{1,30}[a-z0-9]$`, en minúsculas |
| `password` | 12 | 128 | NFKC, lista de comunes, sin el usuario |
| `full_name` | 2 | 80 | `^[\p{L}][\p{L}\p{M} .'-]*$` |
| `aqueduct.name` | 3 | 100 | letras, dígitos, espacio, `-.,()` |
| `aqueduct.slug` | 3 | 40 | `^[a-z0-9]+(-[a-z0-9]+)*$` |
| `sector.name`, `tank.name`, `valve.name` | 2 | 60 | letras, dígitos, espacio, `-.#()` |
| `valve.code` | 1 | 20 | `^[A-Z0-9-]+$` |
| `reading.note`, `shift_execution.note`, `day_closure.notes` | 0 | 500 | sin control salvo `\n`, máximo 10 líneas |
| `incident.description` (público) | 10 | 500 | igual que nota |
| `incident.location_hint` | 0 | 120 | una línea |
| `change_reason`, `decision.reason`, `correction.reason` | 10 | 500 | obligatorio cuando aplica |
| `catalog_item.code` | 2 | 40 | `^[A-Z][A-Z0-9_]*$` |
| `catalog_item.label_es` | 2 | 60 | texto seguro |
| `tracking_code` | 8 | 8 | Crockford base32 |
| `gauge_value` | según tanque | según tanque | decimal con hasta 2 decimales, rango `tanks.gauge_min` a `tanks.gauge_max` |
| `page.limit` | 1 | 100 | entero |
| `telemetry.batch` | 1 | 100 | puntos por petición |

### RNF-05 Salida y errores

- Formato de error: `{"error":{"code":"ENGLISH_CODE","message":"Mensaje en español","details":{},"request_id":"..."}}`.
- Sin trazas de pila en las respuestas.
- Los mensajes no revelan si un usuario existe.

### RNF-06 Cabeceras HTTP, CORS y CSP

- API: HTTPS con HSTS; `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` (en `/swagger-ui/**`: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'`); `X-Content-Type-Options: nosniff`; `Referrer-Policy: no-referrer`; `Permissions-Policy`.
- CORS con lista de orígenes desde una variable de entorno. Nunca `*` con credenciales.
- Frontend (cabecera HTTP en `vercel.json`): `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; worker-src 'self'; manifest-src 'self'; connect-src 'self' <API_ORIGIN>; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`.

### RNF-07 Autorización y RLS

- Deny-by-default. Los permisos se leen de `iam.role_permissions`; el código solo conoce códigos.
- Chequeo por objeto (acueducto) en cada recurso. Un recurso de otro acueducto responde 404.
- RLS con `FORCE ROW LEVEL SECURITY` en las tablas con `rls: true`, filtrado por `app.aqueduct_id`.
- Privilegios por columna para `password_hash`, `token_hash` y secretos MFA.
- Pruebas: cada permiso tiene al menos una prueba de 403 para un rol que no lo tiene, y una prueba de RLS que intenta leer otro acueducto.
- Detalle de roles y reglas por objeto: [Actores-y-permisos.md](Actores-y-permisos.md).

### RNF-08 Dispositivos firmados

- Ed25519 por dispositivo. La base de datos guarda solo la clave pública, con rotación.
- Cabeceras: `X-Device-Id`, `X-Timestamp` (unix en segundos), `X-Nonce` (128 bits en hexadecimal), `X-Signature` (Ed25519 en base64url).
- La firma cubre `método\nruta\ntimestamp\nnonce\nsha256(cuerpo)`.
- Ventana de tiempo de ±300 segundos. Cada nonce es único durante 10 minutos.
- Los comandos de válvula salen solo de turnos aprobados (o manuales con motivo de `BOARD_ADMIN`), con `not_before` y `expires_at`.

### RNF-09 Secretos y datos en logs

- Ningún secreto en git. `.env.example` con marcadores. Escaneo con gitleaks, secret scanning y push protection activos.
- Rotación de secretos documentada.
- Logs en JSON con `request_id`. Redacción de `Authorization`, `Cookie`, `password`, `token` y `secret`.
- La IP se guarda como HMAC.

### RNF-10 Seguridad del servicio de IA

- Token de servicio de al menos 32 bytes aleatorios en `Authorization: Bearer`. Comparación en tiempo constante. Rotación: se acepta el token actual y el anterior.
- Series de hasta 2.000 puntos, valores finitos, timestamps crecientes. `prediction_length` de hasta 30 (límite técnico).
- El servicio no almacena datos y no accede a la base de datos.
- Timeouts desde el backend: conexión de 2 segundos, lectura de 10 segundos. Circuit breaker (ver RNF-16).

### RNF-11 Seguridad del frontend

- CSP estricta (RNF-06). El access token va en memoria. Nada sensible en `localStorage`.
- La cola offline (IndexedDB) no guarda contraseñas, tokens ni secretos.
- Prohibido `dangerouslySetInnerHTML` (verificado con ESLint). React escapa el contenido por defecto.
- Cerrar sesión con lecturas pendientes muestra un aviso antes de hacerlo.

### RNF-12 Usabilidad en campo

- Diseño mobile first. El fontanero registra una lectura con pocos toques (objetivo: cuatro toques o menos, verificar).
- Textos en español sencillo, sin siglas técnicas en pantalla.
- Objetivos táctiles de al menos 44 × 44 px (criterio de diseño propio, verificar).
- Estado de conexión siempre visible. Ninguna acción queda sin respuesta visible.
- Números con coma decimal ("2,1").

### RNF-13 Accesibilidad

- Cumplimiento de WCAG 2.1 nivel AA.
- Contraste de 4,5:1 para texto normal y de 3:1 para texto grande.
- Navegación completa con teclado y foco visible.
- La información nunca depende solo del color: la tendencia usa flecha y texto, y la banda usa etiqueta y color.
- Formularios con etiquetas asociadas y mensajes de error ligados al campo.
- Atributo `lang="es"` en la página.
- Auditoría automatizada con axe en las pruebas de Playwright.

### RNF-14 Funcionamiento sin conexión

- Lecturas en IndexedDB (Dexie) con UUID generado en el cliente.
- Reenvío idempotente: el servidor responde lo mismo para el mismo UUID.
- Sincronización por lotes de hasta 100 lecturas.
- Estado de conexión: `En línea`, `Sin conexión`, `Sincronizando`.
- Caché de reglas y catálogos para validar en el celular sin señal.
- Si la sesión vence con lecturas pendientes, la app pide volver a entrar sin borrar la cola.

### RNF-15 Rendimiento (objetivos, verificar)

| Operación | Objetivo p95 | Notas |
|---|---|---|
| `GET /tanks/{id}/status` | 500 ms | Sin llamar a la IA. |
| `POST /readings` | 800 ms | Incluye validación en cadena. |
| `POST /readings/batch` con 100 lecturas | 3 s | |
| Página pública | 1 s | Respuesta cacheable. |
| Generar propuesta | 5 s | Sin IA en el camino crítico. |
| Pronóstico de la IA | 10 s | Límite de lectura de RNF-10. |
| Cartel PDF | 10 s | |

Todas las metas son objetivos hasta medirlas en la fase 12.

### RNF-16 Disponibilidad y degradación

- Si la IA falla, responde tarde o su circuito está abierto, el sistema usa la estimación simple. Ninguna operación se bloquea por la IA.
- Circuit breaker con estados `Cerrado`, `Abierto` y `Semiabierto`.
- Hosting gratuito: el backend en Render puede dormirse. La app muestra un indicador de arranque y la primera petición tiene un tiempo de espera mayor (objetivo, verificar). Monitoreo externo de salud (por definir).
- Copias y recuperación: PITR de Neon. La retención depende del plan (verificar).
- Los datos de campo se guardan en el celular hasta que el servidor responde.

### RNF-17 Mantenibilidad y arquitectura

- Backend hexagonal: `api` → `application` → `domain` ← `infrastructure`.
- ArchUnit: `domain` no depende de Spring, JPA ni Jackson. Sin ciclos entre paquetes.
- Linters: Checkstyle (`MagicNumber`), SpotBugs con FindSecBugs, Spotless (google-java-format). Frontend: ESLint (`no-magic-numbers`), Prettier. IA: Ruff (`PLR2004`), mypy.
- Frontend por capas: `src/app`, `src/features`, `src/state`, `src/services`, `src/core`, `src/ui`.
- Cobertura mínima con JaCoCo y Vitest: por definir.

### RNF-18 Patrones de diseño explícitos

- Cada patrón del catálogo (P01 a P23) tiene interfaz y clases propias, un caso de uso real, una prueba y un dueño.
- Se documenta qué ofrece el framework que no reemplaza el patrón (por ejemplo, los beans singleton de Spring no sustituyen a `SystemClock`).
- El catálogo completo vive en `caudal-backend/docs/images/catalogo-patrones.drawio` y en la sección 16 de los hechos.

### RNF-19 Trazabilidad y auditoría

- Las tablas `append-only` solo admiten `INSERT` y `SELECT` para `caudal_app`. No hay `DELETE` salvo purgas por funciones `SECURITY DEFINER`.
- Cada acción relevante deja registro en `audit.audit_log` con actor, acción, recurso y motivo cuando aplica.
- La cadena de hashes de auditoría se ancla en `audit.audit_anchors` con un trigger.
- Las correcciones, decisiones y cambios de reglas guardan el motivo y quién los hizo.
- Los turnos reemplazados usan `supersedes_id`; nunca se editan.

### RNF-20 Privacidad (Ley 1581 de 2012)

- Minimización: no se guardan teléfonos de familias. Solo se pide lo que el servicio necesita.
- Página pública, WhatsApp y cartel sin nombres ni teléfonos (`PublicScheduleProxy`).
- Aviso de privacidad versionado, aceptado por cada usuario (`POST /privacy-notice/accept`).
- Resúmenes a entidades solo con autorización vigente.
- Clasificación de cada columna: pública, interna, confidencial o secreta (`schema.yaml`).
- Retención configurable en `audit.retention_policies`. Plazos por definir.
- Procedimiento para que el titular consulte o corrija sus datos: por definir.

### RNF-21 Idioma

- La interfaz, los mensajes para el usuario, los documentos y los diagramas van en español.
- El código, las rutas, los campos JSON, los enums, los códigos de error, los logs y los nombres de tests van en inglés.
- Los textos de la interfaz viven en `src/i18n/es.ts` y `messages_es.properties`, con claves en inglés e interpolación ("Máximo {max}").

### RNF-22 Sin valores quemados

- Parámetros de negocio en la base de datos: reglas versionadas, catálogos y tanques. Se leen con `GET /api/v1/rule-sets/current` y `GET /api/v1/catalogs/{catalog}`.
- Límites técnicos y de seguridad en la clase `FieldLimits` (constantes `public static final`). Alimentan `@Size`, `@Pattern`, `@Column(length)`, placeholders de Flyway y `GET /api/v1/meta/constraints`.
- Pruebas de deriva: base de datos contra `FieldLimits`, y endpoint contra `FieldLimits`.
- Entorno en variables (`application.yml` con `${VAR}`, pydantic-settings, `VITE_API_BASE_URL`), documentadas en `.env.example`.
- Escenarios del simulador en YAML, sin secretos.
- Solo constantes físicas con nombre (`HOURS_PER_DAY = 24`).

### RNF-23 Portabilidad y despliegue

- Entorno local con Docker Compose (PostgreSQL 18 local).
- Despliegue: Render (backend, Docker), Neon (PostgreSQL), Vercel (frontend), Hugging Face Spaces o Render (IA, por verificar).
- Cambiar de proveedor debe requerir cambiar variables de entorno y no código (objetivo, verificar).

### RNF-24 Observabilidad

- Logs en JSON con `request_id`, sin datos sensibles (RNF-09).
- `GET /actuator/health` público y mínimo.
- Swagger en `/swagger-ui.html` y OpenAPI en `/v3/api-docs`, activos en todos los entornos y apagables con `API_DOCS_ENABLED=false`. Ejecutar requiere JWT.
- Métricas de negocio (pronósticos por respaldo, lecturas rechazadas): por definir.

### RNF-25 Tiempo y formato

- La base de datos guarda la hora en UTC (`timestamptz`). La interfaz la muestra en `America/Bogota`.
- Números decimales con coma en la interfaz ("2,1"). En la API, el punto decimal (JSON estándar).
- "Hace X horas" se calcula en el servidor con el reloj inyectable (`SystemClock`).

### RNF-26 Base de datos

- PostgreSQL 18 con siete esquemas: `iam`, `org`, `ops`, `reporting`, `devices`, `audit`, `sim`.
- Roles: `caudal_migrator` (dueño, único con DDL), `caudal_app` (DML mínimo), `caudal_readonly` (vistas de reportes).
- Tiempos límite del rol de la aplicación (`caudal_app`): `statement_timeout` de 5 s, `lock_timeout` de 3 s, `idle_in_transaction_session_timeout` de 10 s. `caudal_readonly` tiene `statement_timeout` de 15 s.
- Conexión TLS con `sslmode=verify-full`.
- Solo consultas parametrizadas (JPA, criteria o parámetros nombrados). Nunca concatenación de SQL.
- Identificadores `uuidv7()`. Claves foráneas con `ON DELETE RESTRICT`.
- Restricciones `UNIQUE` y `EXCLUDE` (bandas y turnos sin solapes con `btree_gist`).

### RNF-27 Pruebas automatizadas

- Backend: JUnit 5 o 6, AssertJ, Mockito, Testcontainers (Docker), ArchUnit, JaCoCo.
- Frontend: Vitest, Testing Library, MSW, Playwright con axe.
- IA: pytest, Hypothesis.
- Pruebas de contrato: el OpenAPI del backend genera los tipos del frontend (openapi-typescript).
- Cobertura objetivo: por definir.

### RNF-28 Transparencia de los datos

- Cada dato lleva su estatus epistémico: `OBSERVED`, `ESTIMATED`, `INFERRED` o `CONFIRMED`. Ninguna pantalla presenta un estimado como observado.
- Lo simulado se declara con la marca "Datos simulados" y con el campo `simulated` en la API (RF-39).
- El pronóstico siempre muestra su rango y el modelo usado (o que es la estimación simple).

## 3. Trazabilidad

Cada RF sale de una historia (HU) y de un módulo. La tabla enlaza el requisito con los repositorios donde se implementa.

| RF | HU | Módulo | Repositorio principal | Repositorios secundarios | RNF principales |
|---|---|---|---|---|---|
| RF-01 | HU-01 | M1 Login y usuarios | B | F | RNF-01, RNF-02, RNF-05 |
| RF-02 | HU-02 | M1 Login y usuarios | B | F | RNF-01, RNF-04 |
| RF-03 | HU-03 | M1 Login y usuarios | B | | RNF-03, RNF-19 |
| RF-04 | HU-04 | M1 Login y usuarios | B | F | RNF-07, RNF-19 |
| RF-05 | HU-05 | M1 Login y usuarios | B | F | RNF-02, RNF-19 |
| RF-06 | HU-06 | M1 Login y usuarios | B | F | RNF-01, RNF-02, RNF-09 |
| RF-07 | HU-07 | M2 Reglas | B | F | RNF-04, RNF-07, RNF-22 |
| RF-08 | HU-08 | M2 Reglas | B | F | RNF-19, RNF-22 |
| RF-09 | HU-09 | M3 Lecturas offline | F | B | RNF-04, RNF-12, RNF-14 |
| RF-10 | HU-10 | M3 Lecturas offline | B | F | RNF-04, RNF-14 |
| RF-11 | HU-11 | M3 Lecturas offline | B | F | RNF-04, RNF-22 |
| RF-12 | HU-12 | M3 Lecturas offline | B | | RNF-04, RNF-19 |
| RF-13 | HU-13 | M3 Lecturas offline | B | F | RNF-07, RNF-19 |
| RF-14 | HU-14 | M4 Estado y pronóstico | B | F | RNF-12, RNF-13, RNF-25 |
| RF-15 | HU-15 | M4 Estado y pronóstico | B | IA, F | RNF-10, RNF-13, RNF-25 |
| RF-16 | HU-16 | M4 Estado y pronóstico | B | IA | RNF-10, RNF-16 |
| RF-17 | HU-17 | M4 Estado y pronóstico | B | F | RNF-19 |
| RF-18 | HU-18 | M5 Propuesta | B | F | RNF-18, RNF-22, RNF-25 |
| RF-19 | HU-19 | M5 Propuesta | B | | RNF-22, RNF-26 |
| RF-20 | HU-20 | M6 Decisión | B | F | RNF-07, RNF-19 |
| RF-21 | HU-21 | M6 Decisión | B | F | RNF-18, RNF-19 |
| RF-22 | HU-22 | M6 Decisión | B | F | RNF-19 |
| RF-23 | HU-23 | M7 Publicación y reportes | B | F | RNF-08, RNF-19 |
| RF-24 | HU-24 | M7 Publicación y reportes | B | F | RNF-06, RNF-11, RNF-20 |
| RF-25 | HU-25 | M7 Publicación y reportes | B | F | RNF-12, RNF-21 |
| RF-26 | HU-26 | M7 Publicación y reportes | B | | RNF-20, RNF-21 |
| RF-27 | HU-27 | M7 Publicación y reportes | B | F | RNF-03, RNF-04, RNF-20 |
| RF-28 | HU-28 | M7 Publicación y reportes | B | F | RNF-19 |
| RF-29 | HU-29 | M8 Cierre del día | B | F | RNF-19, RNF-22 |
| RF-30 | HU-30 | M9 Actas y entidades | B | F | RNF-19, RNF-22, RNF-28 |
| RF-31 | HU-31 | M9 Actas y entidades | B | F | RNF-19, RNF-20 |
| RF-32 | HU-32 | M9 Actas y entidades | B | F | RNF-07, RNF-20 |
| RF-33 | HU-33 | M10 Evaluación | B | IA, F | RNF-18, RNF-25 |
| RF-34 | HU-34 | M11 Dispositivos | B | S | RNF-08, RNF-19 |
| RF-35 | HU-35 | M11 Dispositivos | B | S | RNF-04, RNF-08 |
| RF-36 | HU-36 | M11 Dispositivos | B | S | RNF-08, RNF-19 |
| RF-37 | HU-37 | M12 Auditoría | B | F | RNF-09, RNF-19, RNF-24 |
| RF-38 | HU-38 | M13 Datos simulados | B | S | RNF-04, RNF-07 |
| RF-39 | HU-39 | M13 Datos simulados | B | F, S | RNF-21, RNF-28 |
| RF-40 | HU-04, HU-32 | M1, M9 | B | | RNF-07 |
| RF-41 | HU-09, HU-11 | M3 Lecturas offline | B | F | RNF-04, RNF-22 |

Resumen: las 39 historias cubren los 41 RF. RF-40 y RF-41 son transversales y se reflejan en varias historias.

Relacionados: [Historias-de-usuario.md](Historias-de-usuario.md) · [Actores-y-permisos.md](Actores-y-permisos.md) · [Riesgos.md](Riesgos.md) · [Caudal.md](Caudal.md)
