# Seguridad

Esta es la política global de seguridad de CAUDAL. Aplica a los cuatro repositorios: [`caudal-backend`](https://github.com/NicoalsD/caudal-backend), [`caudal-frontend`](https://github.com/NicoalsD/caudal-frontend), [`caudal-ia`](https://github.com/NicoalsD/caudal-ia) y [`caudal-simulador`](https://github.com/NicoalsD/caudal-simulador). Cuando una regla es específica de un repositorio, se indica.

Responsables: Drako Salazar (`Drako2305`) es líder de seguridad y responde por el backend y la base de datos. Nicolas Diaz (`NicoalsD`) responde por el frontend, CI y los secretos de despliegue. Nicolas Mora (`nicomora70`) responde por el servicio de IA y el simulador cuando se una al proyecto.

Las cifras de este documento son canónicas. Si algo no aparece aquí, está marcado "por definir" o "verificar". Ningún integrante lo implementa con un valor inventado.

![Secuencia de inicio de sesión](images/secuencia-login.png)

## 1. Modelo de amenazas

### 1.1 Activos

| Activo | Por qué importa | Clase de datos |
|---|---|---|
| Cuentas de la Junta, fontanero, equipo y entidades | Permiten aprobar turnos, publicar horarios y leer resúmenes | C y S (hashes) |
| Lecturas, reglas y decisiones de la Junta | Son la base de las decisiones del agua y de las actas | I |
| Actas y auditoría | Dan trazabilidad: qué se decidió, quién y cuándo | C e I |
| Horario publicado y cartel | Lo ven todas las familias; deben ser correctos | P |
| Datos personales (nombres de la Junta y del fontanero, IP en HMAC) | Ley 1581 de 2012 | C |
| Secretos (contraseñas, claves JWT, claves de dispositivos, token de la IA, clave AES de MFA) | Su filtración da acceso completo | S |
| Dispositivos y válvulas (futuro real) | Pueden abrir y cerrar agua | I |

### 1.2 Actores maliciosos

| Actor | Motivación | Capacidad |
|---|---|---|
| Atacante externo anónimo | Vandalismo, spam, robo de datos | Acceso a internet y a la página pública |
| Persona con cuenta válida de un rol bajo (fontanero, entidad de apoyo) | Ver o alterar lo que no le toca | Credenciales propias, herramientas del navegador |
| Miembro de la Junta descontento | Alterar decisiones o actas | Credenciales de la Junta, conocimiento del negocio |
| Dispositivo falsificado | Enviar telemetría falsa o recibir comandos | Acceso a la red LoRa o LTE (en el futuro) |
| Atacante con acceso a dependencias o al repositorio | Insertar código o robar secretos | Acceso de lectura al código público; riesgo en las dependencias |
| Atacante con acceso a la base de datos | Leer o modificar datos sin la aplicación | Credenciales filtradas de un servicio |

### 1.3 Superficies

| Superficie | Repositorio | Quién llega |
|---|---|---|
| Login y gestión de sesión | Backend, frontend | Todos los usuarios y atacantes |
| API REST `/api/v1/*` | Backend | Usuarios autenticados y frontend |
| Página pública y formulario de daños (`/api/v1/public/*`) | Backend, frontend | Cualquier persona en internet |
| Endpoints de dispositivos (`/api/v1/devices/*`) | Backend, simulador | Dispositivos firmados con Ed25519 |
| Servicio de pronóstico | IA, backend | Solo el backend, con token de servicio |
| Base de datos | Backend | Solo la aplicación y los migradores |
| CI, dependencias y secretos | Los cuatro | Equipo y herramientas de CI |

### 1.4 Amenaza → control

| Amenaza | Control principal | Dónde se detalla |
|---|---|---|
| Adivinación de contraseñas | Argon2id, política de longitud, lista de comunes, bloqueo por cuenta e IP | Sección 2.1 y 2.2 |
| Robo de la base de datos y lectura de contraseñas | Solo hashes Argon2id, columnas `S` sin lectura para `caudal_readonly` | Sección 2.1 y [BD](Seguridad-de-la-base-de-datos.md) |
| Robo de refresh token | Token opaco, hash en BD, rotación, detección de reutilización, cookie `HttpOnly` | Sección 2.3 |
| XSS | React escapa, CSP estricta, prohibido `dangerouslySetInnerHTML` | Secciones 2.7 y 2.8 |
| CSRF | `SameSite`, cabecera `X-Requested-With`, verificación de `Origin`; el access token no va en cookie | Sección 2.3 |
| Inyección SQL | JPA y consultas con parámetros, privilegios mínimos, RLS | Sección 2.5 y [BD](Seguridad-de-la-base-de-datos.md) |
| Ver datos de otro acueducto | RLS, chequeo por objeto, respuesta `404` | Sección 2.5 |
| Un rol bajo aprueba turnos | Permisos desde BD, deny-by-default, registro de auditoría | Sección 2.5 |
| Alterar la auditoría | Cadena de hashes y anclas diarias impresas en las actas | [BD](Seguridad-de-la-base-de-datos.md), sección 7 |
| Alterar lecturas | Correcciones como filas nuevas; el dato original se conserva | [BD](Seguridad-de-la-base-de-datos.md), sección 6 |
| Telemetría falsa o repetida | Firma Ed25519, ventana de ±300 s, nonce único durante 10 min | Sección 2.12 |
| Comando de válvula no autorizado | Solo desde turnos aprobados o con motivo de `BOARD_ADMIN`; `not_before` y `expires_at` | Sección 2.12 |
| Spam y bots en el formulario público | Honeypot, tiempo mínimo de llenado, 3 reportes por hora por IP | Sección 2.10 |
| Filtrar nombres o teléfonos al público | DTO público sin datos personales (`PublicScheduleProxy`), clasificación de columnas | Sección 2.17 |
| Enumerar usuarios | Mensaje genérico, tiempo constante con hash señuelo | Sección 2.2 |
| Payloads enormes o JSON profundo | Límites de cuerpo, profundidad, listas y campos desconocidos | [Validación](../.agents/input-validation.md) |
| Fugas de secretos en git | gitleaks, secret scanning con push protection, `.env.example` con marcadores | Sección 2.14 |
| Dependencias vulnerables | Dependabot, `pnpm audit`, `pip-audit`, SpotBugs con FindSecBugs | Sección 2.16 |
| Respuesta falsa o maliciosa de la IA | Token de servicio, validación de la respuesta, circuit breaker y respaldo | Sección 2.13 |
| Inyección de fórmulas en CSV | Escape de celdas que empiezan con `=`, `+`, `-`, `@` | Sección 2.7 |
| Clickjacking | `frame-ancestors 'none'` en API y frontend | Sección 2.8 |
| Pérdida de datos | PITR de Neon (ventana por verificar), respaldos | [BD](Seguridad-de-la-base-de-datos.md), sección 12 |

## 2. Controles

### 2.1 Contraseñas

| Parámetro | Valor canónico | Variable (nombre propuesto) |
|---|---|---|
| Algoritmo | Argon2id con `Argon2PasswordEncoder` de Spring Security (BouncyCastle) | No aplica |
| Memoria (`m`) | 19.456 KiB (19 MiB) | `ARGON2_MEMORY_KIB` |
| Iteraciones (`t`) | 2 | `ARGON2_ITERATIONS` |
| Paralelismo (`p`) | 1 | `ARGON2_PARALLELISM` |
| Sal | 16 bytes aleatorios | No configurable |
| Hash | 32 bytes, en formato PHC dentro de `iam.users.password_hash` | No configurable |
| Rehash | Al iniciar sesión, si los parámetros cambiaron | No aplica |
| Longitud | 12 a 128 caracteres Unicode, normalizados con NFKC | No aplica |
| Composición | Sin reglas de mayúsculas, números ni símbolos | No aplica |
| Rechazadas | Las de la lista local de comunes y las que contienen el nombre de usuario | No aplica |
| Historial | Las últimas 5 no se pueden reutilizar | `PASSWORD_HISTORY_SIZE` |
| Primer ingreso | `must_change_password = true` por defecto; el cambio es obligatorio | No aplica |

Por qué se hashean y no se "encriptan":

- Un hash es de una sola vía. El sistema verifica una contraseña calculando su hash y comparándolo, pero nadie puede recuperar la contraseña original, ni siquiera el equipo.
- Un cifrado sí se puede revertir, y eso exige una clave. Si la clave se filtra, todas las contraseñas quedan expuestas.
- Argon2id es lento y usa memoria a propósito. Así, un atacante que robe la tabla debe pagar ese costo para cada intento.
- Consecuencia práctica: si alguien olvida su contraseña, no se le puede enviar. La Junta emite un código de restablecimiento de un solo uso (`iam.password_reset_grants`).
- Excepción: el secreto TOTP de MFA sí se cifra con AES-256-GCM, porque el sistema necesita leerlo para calcular códigos. Esa es la diferencia.
- Las contraseñas nunca aparecen en logs, respuestas, URLs ni en claro en la base de datos. Las columnas `password_hash` tienen privilegios por columna (ver [BD](Seguridad-de-la-base-de-datos.md), sección 4).

### 2.2 Login y bloqueo

- Mensaje genérico para cualquier fallo: "Usuario o contraseña incorrectos" (`INVALID_CREDENTIALS`). El mensaje no revela si el usuario existe, si está bloqueado ni cuál campo falló.
- Tiempo constante: si el usuario no existe, se verifica la contraseña contra un hash señuelo. Así el tiempo de respuesta no delata la existencia de la cuenta.
- Bloqueo por cuenta: 5 fallos en 15 minutos bloquean la cuenta 15 minutos. El bloqueo se escala (multiplica por 2) en cada nivel, hasta 24 horas. Estado en `iam.users`: `status = LOCKED`, `locked_until`, `lockout_level`, `failed_login_count`.
- Bloqueo por IP: 20 intentos en 15 minutos.
- Límite de tasa de login: 5 por minuto por IP (ver sección 2.10).
- Dos capas: el límite de tasa frena ráfagas; los bloqueos frenan ataques sostenidos. Un atacante que hace 5 intentos por minuto llega a 20 intentos por IP en cuatro minutos, así que el bloqueo por IP se activa antes de agotar la ventana de la tasa. Comportamiento intencional; verificar con pruebas de carga.
- Los fallos son consecutivos: 5 fallos seguidos dentro de 15 minutos bloquean la cuenta. El contador se reinicia con un ingreso exitoso o al pasar la ventana.
- Cada intento queda en `iam.login_attempts` con el usuario y la IP en HMAC (nunca en claro), y con el motivo (`INVALID_CREDENTIALS`, `LOCKED`, `DISABLED`, `RATE_LIMITED`, `MFA_FAILED`).
- El bloqueo genera un evento `ACCOUNT_LOCKED` en `iam.security_events`.

### 2.3 Tokens

Access token (JWT):

- Algoritmo HS256. Clave de al menos 256 bits (`JWT_SECRET`).
- Duración: 15 minutos.
- Claims: `iss`, `aud`, `sub`, `role`, `aqueduct_id`, `tv` (token_version), `jti`, `iat`, `exp`.
- Validación: `alg` explícito (solo HS256; se rechaza `none` y cualquier otro), `iss`, `aud`, `exp` y `nbf` con tolerancia de 30 segundos.
- Se valida `tv` contra `iam.users.token_version` (propuesta: en cada petición; verificar el costo).
- Una cabecera `Authorization` de más de 2 KiB se rechaza antes de parsear el token.
- El access token vive solo en memoria del frontend. Nunca en `localStorage`, ni en `sessionStorage`, ni en cookies.

Refresh token:

- Opaco, de 256 bits aleatorios.
- La base de datos guarda solo su SHA-256 (`iam.refresh_tokens.token_hash`). El token en claro nunca se guarda.
- Rotación: cada uso emite un token nuevo y revoca el anterior (`revoked_reason = ROTATED`, `replaced_by_id`). Ambos comparten `family_id`.
- Reutilización: si se presenta un token ya revocado, se revoca toda la familia (`REUSE_DETECTED`) y se registra `TOKEN_REUSE_DETECTED` con severidad alta.
- Duración: 7 días; 30 días para `OPERATOR` (fontanero). Configurable (`REFRESH_TTL_DAYS_DEFAULT`, `REFRESH_TTL_DAYS_OPERATOR`). El plazo de 30 días para el fontanero es un riesgo aceptado: el dispositivo es compartido en campo.
- Cookie `caudal_rt`: `HttpOnly; Secure; SameSite=None; Path=/api/v1/auth`. Si algún día frontend y API comparten dominio, se pasa a `SameSite=Strict`.
- En la práctica, el frontend (Vercel) y la API (Render) son sitios distintos. Por eso se aplica `SameSite=None` más dos defensas: la cabecera `X-Requested-With: caudal-web` y la verificación de `Origin` contra `CORS_ALLOWED_ORIGINS`.
- Cierre de sesión: `logout` revoca la familia (`LOGOUT`); `logout-all` revoca todas las familias del usuario (`LOGOUT_ALL`).
- Cambio de contraseña, cambio de rol o desactivación: incrementan `token_version` y revocan todos los refresh (`PASSWORD_CHANGED`, `ROLE_CHANGED`, `ADMIN`).

### 2.4 MFA

- TOTP opcional para `BOARD_ADMIN` y `PROJECT_TEAM`. Por definir si pasa a obligatorio para `BOARD_ADMIN` (es el rol con más poder).
- El secreto TOTP se cifra con AES-256-GCM. La clave viene de `MFA_ENCRYPTION_KEY`, y `iam.mfa_factors.key_id` indica cuál usar, para permitir rotación.
- Códigos de respaldo: se guardan con SHA-256 (`iam.mfa_recovery_codes.code_hash`), son de un solo uso (`used_at`). Cantidad por definir.
- Anti-repetición: `last_used_step` impide reutilizar el mismo código dentro de su ventana.
- Un fallo de MFA genera `MFA_FAILED` y cuenta para el bloqueo.
- Parámetros TOTP (dígitos y paso): verificar con la RFC 6238 al implementar (por lo común, 6 dígitos y 30 segundos).

### 2.5 Autorización

- Deny-by-default: todo endpoint requiere autenticación y permiso, salvo los públicos: `/api/v1/public/*`, `/api/v1/meta/constraints`, `/api/v1/catalogs/{catalog}` (solo etiquetas, porque el formulario público necesita `DAMAGE_CATEGORY`) y `/actuator/health`.
- RBAC desde la base de datos: los roles (`BOARD_ADMIN`, `BOARD_MEMBER`, `OPERATOR`, `PROJECT_TEAM`, `SUPPORT_ENTITY`), los permisos y la matriz viven en `iam.roles`, `iam.permissions` y `iam.role_permissions`. El código solo conoce los códigos de permiso (`READING_CREATE`, `RULESET_ACTIVATE`, `PROPOSAL_DECIDE`, ...). Cambiar la matriz no exige un despliegue. TTL de la caché de permisos: por definir.
- Chequeo por objeto: todo recurso se filtra por acueducto según la membresía vigente (`valid_from`, `valid_to`). Un recurso de otro acueducto responde `404 NOT_FOUND`, igual que uno inexistente, para no revelar su existencia.
- RLS en la base de datos como segunda barrera: `app.aqueduct_id` se fija por transacción desde el JWT. Ver [BD](Seguridad-de-la-base-de-datos.md), sección 5.
- `SUPPORT_ENTITY` solo ve resúmenes con una autorización vigente (`reporting.summary_shares` sin `revoked_at` y con `expires_at` en el futuro).
- Un `403` genera `PERMISSION_DENIED` en `iam.security_events`.
- Un usuario con varios acueductos: el JWT lleva un solo `aqueduct_id` (el activo). `POST /api/v1/auth/switch-aqueduct` emite un token nuevo si la membresía está vigente.

### 2.6 Validación de entradas

Resumen de las reglas:

- Cuerpo JSON de hasta 64 KiB (telemetría 256 KiB; importación 1 MiB o 5.000 filas por lote).
- Campos desconocidos rechazados. Sin coerción de tipos. Profundidad JSON de hasta 10 niveles. Listas de hasta 100 elementos.
- Texto: normalización NFC (contraseñas NFKC), recorte, colapso de espacios en nombres, tope de 2.000 caracteres.
- Se rechazan caracteres de control, bidi (U+202A a U+202E, U+2066 a U+2069) y de ancho cero (U+200B a U+200F, U+FEFF).
- Fechas ISO-8601 con zona, no más de 5 minutos en el futuro ni más atrás de `max_backdate_days`.
- Paginación de 1 a 100 (por defecto 20), con cursor opaco firmado.

La tabla completa de campos, con límites, patrones y códigos de error, está en [`.agents/input-validation.md`](../.agents/input-validation.md). Los límites viven en `FieldLimits` ([configuración](Configuracion-sin-valores-quemados.md), sección 5).

### 2.7 Salida y errores

Formato de error:

```json
{ "error": { "code": "ENGLISH_CODE", "message": "Mensaje en español", "details": {}, "request_id": "..." } }
```

- El código es en inglés y estable. El mensaje es en español y es para el usuario.
- Nunca se devuelven trazas, nombres de clases, consultas SQL ni valores de configuración.
- Un error no esperado responde `500` con un mensaje genérico y un `request_id` que aparece en los logs.
- Los `details` nunca incluyen un secreto ni el valor enviado de un campo secreto.
- Frontend: React escapa el contenido por defecto. Está prohibido `dangerouslySetInnerHTML` (ESLint lo impide). La salida de Markdown o HTML, si la hubiera, se sanitiza (por definir).
- CSV: toda celda que empieza con `=`, `+`, `-`, `@`, tabulador o retorno de carro se prefija con una comilla simple para evitar la inyección de fórmulas.
- PDF (actas y cartel): el texto de usuario se escribe como texto plano con OpenPDF, nunca como HTML.

### 2.8 Cabeceras HTTP

| Cabecera | API (backend) | Frontend (Vercel) |
|---|---|---|
| `Strict-Transport-Security` | HSTS. `max-age` y `includeSubDomains` por verificar | HSTS por verificar |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'`. En `/swagger-ui/**`: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; worker-src 'self'; manifest-src 'self'; connect-src 'self' <API_ORIGIN>; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| `X-Content-Type-Options` | `nosniff` | `nosniff` |
| `Referrer-Policy` | `no-referrer` | `no-referrer` (verificar la compatibilidad con la PWA) |
| `Permissions-Policy` | Por definir (propuesta: deshabilitar cámara, micrófono y geolocalización) | Igual |
| `Cache-Control` | `no-store` en respuestas autenticadas (propuesta) | Por configurar |

Las cabeceras del frontend se configuran en la configuración de Vercel. Las de la API, en `RequestSizeLimitFilter` y en la configuración de Spring Security.

### 2.9 CORS

- Lista de orígenes permitidos desde `CORS_ALLOWED_ORIGINS`. Nunca `*` cuando hay credenciales.
- `allowCredentials` solo para los orígenes de la lista.
- Métodos y cabeceras en lista explícita. `X-Requested-With` está permitida.
- Un origen fuera de la lista no recibe cabeceras CORS, y la verificación de `Origin` del refresh lo rechaza.

### 2.10 Límites de tasa

Los límites se guardan en PostgreSQL con Bucket4j (`iam.rate_limit_buckets`), para que varias instancias compartan el conteo. Los valores vienen de variables de entorno.

| Límite | Valor canónico | Variable (nombre propuesto) |
|---|---|---|
| Login por IP | 5 por minuto | `RATE_LIMIT_LOGIN_PER_MINUTE` |
| Reportes públicos por IP | 3 por hora, más honeypot y tiempo mínimo de llenado | `RATE_LIMIT_PUBLIC_REPORTS_PER_HOUR`; tiempo mínimo `PUBLIC_FORM_MIN_SECONDS` (3 s) |
| API por usuario | 120 por minuto | `RATE_LIMIT_API_PER_MINUTE` |
| Telemetría por dispositivo | 60 por minuto | `RATE_LIMIT_TELEMETRY_PER_MINUTE` |
| Recálculo de pronóstico por acueducto | 10 por hora | `RATE_LIMIT_FORECAST_PER_HOUR` |

- Al exceder un límite: `429` con código `RATE_LIMITED` y cabecera `Retry-After` (propuesta). Se registra `RATE_LIMITED`.
- Honeypot: un campo oculto que un humano no llena. Si llega con valor, la API responde como éxito, no guarda nada y registra `HONEYPOT_TRIGGERED`.

### 2.11 Swagger y documentación de la API

- Swagger UI en `/swagger-ui.html` y la especificación en `/v3/api-docs`. Activo en todos los entornos. Se apaga con `API_DOCS_ENABLED=false`.
- Expone: rutas, métodos, esquemas, códigos de respuesta, esquema de seguridad bearer y las descripciones en español.
- No expone: secretos, configuración, datos de la base de datos, trazas ni valores de entorno.
- Ejecutar una petición desde Swagger requiere un JWT válido. Un visitante anónimo solo ve la documentación.
- Riesgo aceptado: se conoce la superficie de la API. Se mitiga porque todos los endpoints no públicos exigen autenticación y permiso.

### 2.12 Dispositivos

- Cada dispositivo tiene un par de claves Ed25519. La base de datos guarda solo la clave pública (`devices.device_keys`, con `revoked_at` para rotación). La clave privada nunca llega al servidor.
- Firma: cabeceras `X-Device-Id`, `X-Timestamp` (unix en segundos), `X-Nonce` (128 bits en hexadecimal) y `X-Signature` (Ed25519 en base64url). La firma cubre la cadena `método\nruta\ntimestamp\nnonce\nsha256(cuerpo)`. El formato exacto del hash del cuerpo (hexadecimal en minúsculas) es propuesta; confirmar en el simulador y el backend a la vez.
- Ventana de tiempo: ±300 segundos.
- Anti-repetición: cada nonce se guarda en `devices.device_nonces` y no se acepta otra vez durante 10 minutos.
- Verificación en tiempo constante (la biblioteca de Ed25519 lo garantiza; no comparar firmas con `equals` sobre texto).
- Errores: `INVALID_SIGNATURE` y `NONCE_REPLAY` en `iam.security_events`.
- Comandos de válvula: solo desde un turno aprobado (`schedule_item_id`), o manuales con motivo y por `BOARD_ADMIN`. Cada comando tiene `not_before` y `expires_at`, y un comando vencido no se entrega.
- Telemetría idempotente por el `id` que genera el dispositivo.
- Las claves del simulador viven en un directorio ignorado por git (ver sección 2.14).

### 2.13 Servicio de IA

- Token de servicio de al menos 32 bytes aleatorios en `Authorization: Bearer` (`IA_SERVICE_TOKEN`). La comparación es en tiempo constante (`MessageDigest.isEqual`).
- Rotación sin corte: el servicio acepta el token actual y el anterior (`IA_SERVICE_TOKEN_PREVIOUS`) durante la rotación.
- Solo el backend llama al servicio. Por definir si la red del servicio admite otras fuentes.
- Entrada: series de hasta 2.000 puntos, valores finitos, timestamps crecientes, `prediction_length` de 1 a 30.
- El servicio no guarda datos y no accede a la base de datos.
- Timeouts: conexión 2 s (`IA_CONNECT_TIMEOUT_MS`), lectura 10 s (`IA_READ_TIMEOUT_MS`).
- Circuit breaker (`CircuitBreakerState`: cerrado, abierto, semiabierto). Con el circuito abierto, o ante error, timeout o `503 MODEL_NOT_READY`, se usa la estimación simple y se registra el motivo (`fallback_reason`).
- El backend valida la respuesta: los cuantiles deben cumplir p10 ≤ p50 ≤ p90, los valores deben ser finitos y las fechas, válidas. Una respuesta inválida se trata como error.

### 2.14 Secretos y rotación

- Ningún secreto en git. `.env.example` contiene solo marcadores.
- Los secretos viven en el gestor de cada plataforma: variables de entorno de Render (backend, IA), de Vercel (solo variables públicas `VITE_*`) y los secretos de GitHub Actions (propuesta).
- Detección: gitleaks en CI sobre cada PR; secret scanning y push protection de GitHub activos.
- Lista de secretos y su rotación (frecuencia por definir):

| Secreto | Efecto de rotarlo | Procedimiento |
|---|---|---|
| `JWT_SECRET` | Invalida los access tokens vigentes (15 minutos de vida) | Cambiar la variable y reiniciar. Los usuarios reingresan con su refresh |
| `IP_HMAC_KEY` | Las IP guardadas dejan de ser comparables | Rotar solo ante sospecha; documentar la discontinuidad |
| `MFA_ENCRYPTION_KEY` | Requiere re-cifrar los secretos TOTP con la nueva clave (`key_id`) | Procedimiento por definir: re-cifrado con `key_id` nuevo |
| `IA_SERVICE_TOKEN` | Ninguno si se usa `IA_SERVICE_TOKEN_PREVIOUS` | Poner el nuevo en el backend y en la IA; luego retirar el anterior |
| `DATABASE_PASSWORD` y `FLYWAY_PASSWORD` | Ninguno si se reinicia el servicio | Cambiar en la base de datos y en la plataforma, en ese orden |
| Claves de dispositivo | Revocar la clave anterior (`revoked_at`) | Registrar la clave nueva con `POST /devices/{id}/keys` |

- Si un secreto se filtra: rotarlo de inmediato, revocar lo que dependa de él y abrir un incidente (sección 3).

### 2.15 Logs y redacción

- Logs en JSON con `request_id` en cada línea (correlación con `error.request_id`).
- Campos redactados: `Authorization`, `Cookie`, `password`, `token`, `secret` y cualquier clave que contenga esas palabras.
- No se registra el cuerpo de las peticiones por defecto.
- Las IP no van en claro a los logs (propuesta, verificar). En la base de datos, siempre en HMAC.
- Los eventos de seguridad van a `iam.security_events`, con `details` ya redactados.
- Retención de logs: por definir.

### 2.16 Dependencias y CI

| Control | Repos | Cuándo | Bloquea el merge |
|---|---|---|---|
| CodeQL (Java, TypeScript, Python) | Los cuatro | En cada PR y semanal | Hallazgos altos y críticos (propuesta) |
| gitleaks | Los cuatro | En cada PR | Sí |
| Secret scanning y push protection de GitHub | Los cuatro | Siempre | Sí |
| Dependabot (alertas y actualizaciones de Maven, npm/pnpm, pip, GitHub Actions) | Los cuatro | Continuo | No; se atiende por prioridad |
| SpotBugs con FindSecBugs | Backend | En cada build | Sí |
| `pnpm audit` | Frontend | En CI | Vulnerabilidades altas y críticas (propuesta). **No está en la lista del stack de los hechos: se agrega por esta política y debe confirmarse** |
| `pip-audit` | IA y simulador | En CI | Vulnerabilidades altas y críticas (propuesta). **No está en la lista del stack de los hechos: se agrega por esta política y debe confirmarse** |
| Checkstyle (`MagicNumber`), Spotless, ESLint, Ruff, mypy | Según repo | En cada PR | Sí |
| `commit-lint` | Los cuatro | En cada PR | Sí |

Reglas adicionales:

- Ramas protegidas: PR obligatorio, merge commit, sin force-push ni borrado.
- Las dependencias se fijan por versión exacta en el lockfile (Maven Wrapper, `pnpm-lock.yaml`, `uv.lock`).
- Toda GitHub Action se fija a un SHA de commit, no a una etiqueta móvil (propuesta).
- Las pruebas de integración usan Testcontainers con PostgreSQL 18 real, no una base de datos en memoria.

### 2.17 Datos personales (Ley 1581 de 2012)

- Minimización: no se guardan teléfonos de las familias. Los nombres se guardan solo en `iam.users.full_name` (C) para las cuentas del sistema.
- Página pública, WhatsApp y cartel: sin nombres ni teléfonos. El `PublicScheduleProxy` quita cualquier dato personal antes de responder.
- Aviso de privacidad versionado (`iam.privacy_notice_versions`). Cada usuario lo acepta (`iam.privacy_acceptances`) con versión, fecha y la IP en HMAC. El texto del aviso requiere revisión jurídica (por definir).
- Resúmenes para entidades de apoyo: solo con una autorización vigente (`reporting.summary_shares`), que la Junta puede revocar en cualquier momento. Cada consulta queda en `audit.data_access_log`.
- Derechos de los titulares (consulta, rectificación, supresión): procedimiento y plazos por definir con asesoría jurídica. La Ley 1581 fija los plazos; no se citan aquí para no inventar cifras.
- Responsable y encargado del tratamiento: por definir con asesoría jurídica.
- Los datos del acueducto demo son simulados. Aun así, la UI, la página pública, el mensaje de WhatsApp, el cartel y las actas muestran "Datos simulados".

### 2.18 Frontend

- CSP estricta (ver sección 2.8). Sin scripts en línea ni `eval`.
- Access token solo en memoria. Nada sensible en `localStorage`.
- La cola offline (IndexedDB) no guarda secretos: guarda lecturas, no contraseñas ni tokens.
- Al cerrar sesión con pendientes, la app avisa antes de borrar la cola.
- El frontend nunca confía en su propia validación: la API valida de nuevo.

## 3. Respuesta a incidentes

1. **Detectar.** Fuentes: eventos `CRITICAL` o `HIGH` en `iam.security_events`, alertas de GitHub, alertas de Render o Neon, o un reporte por el canal de [`SECURITY.md`](https://github.com/NicoalsD/caudal-backend/blob/develop/SECURITY.md).
2. **Contener.** Revocar sesiones (`logout-all` o incremento de `token_version`), desactivar cuentas (`DISABLED`), suspender dispositivos (`SUSPENDED`), rotar los secretos afectados (sección 2.14). Si es necesario, apagar la superficie afectada.
3. **Evaluar.** Qué pasó, desde cuándo, qué acueductos y qué clases de datos (P, I, C, S) estuvieron expuestas.
4. **Preservar evidencia.** Copia de `iam.security_events`, `audit.audit_log` (verificar la cadena con `audit.verify_chain`) y de los logs con el `request_id`. No borrar nada.
5. **Erradicar.** Corregir la causa, con una prueba que la reproduzca. Revisar si el mismo patrón existe en otros módulos.
6. **Notificar.** A la Junta del acueducto afectado, sin demora. Si hubo datos personales, seguir el procedimiento de la Ley 1581 (por definir con asesoría jurídica).
7. **Recuperar.** Restaurar el servicio. Si se requiere restauración de datos, usar PITR (ver [BD](Seguridad-de-la-base-de-datos.md), sección 12) y verificar la restauración antes de reabrir.
8. **Aprender.** Documento de incidente sin culpables en `docs/` (propuesta: `docs/incidentes/AAAA-MM-DD-titulo.md`), con causa raíz, acciones y responsables. Las acciones entran a esta lista de verificación.

Responsables: Drako Salazar coordina lo técnico de backend y base de datos. Nicolas Diaz coordina la comunicación, CI y los secretos. Los tiempos de respuesta por severidad: por definir.

## 4. Lista de verificación de seguridad para cada PR

Marcar cada punto que aplique antes de pedir revisión.

- [ ] No hay secretos, tokens ni contraseñas en el diff ni en los commits. `gitleaks` pasa.
- [ ] Todo endpoint nuevo tiene autenticación, permiso del rol en `iam.role_permissions` y chequeo por acueducto.
- [ ] Ningún endpoint nuevo se marca como público sin revisión de seguridad.
- [ ] Las entradas nuevas están en [`.agents/input-validation.md`](../.agents/input-validation.md) con su constante de `FieldLimits`.
- [ ] Las consultas usan parámetros (JPA, criteria o parámetros nombrados). No hay concatenación de SQL.
- [ ] No hay `dangerouslySetInnerHTML`, `eval`, `innerHTML` ni scripts en línea.
- [ ] Los errores devuelven el formato canónico, sin trazas ni datos internos.
- [ ] Los logs no contienen `Authorization`, cookies, contraseñas, tokens ni secretos. Las IP van en HMAC.
- [ ] Un acción sensible (aprobar, publicar, activar reglas, corregir lecturas, comandos de válvula, cambios de usuarios) escribe en la auditoría.
- [ ] Si la tabla nueva es de registro histórico, es append-only y tiene su trigger y su prueba.
- [ ] Si la tabla nueva tiene `aqueduct_id`, tiene RLS con `FORCE` y su prueba de aislamiento.
- [ ] Si hay una columna sensible nueva (contraseña, token, clave, identificador de persona), tiene privilegios por columna.
- [ ] Las dependencias nuevas tienen versión fijada y no tienen alertas de Dependabot ni de `pnpm audit` o `pip-audit`.
- [ ] Se agregó la prueba de seguridad que corresponde al control (ver [`.agents/security.md`](../.agents/security.md)).
- [ ] Ningún valor de negocio está escrito en código (ver [configuración](Configuracion-sin-valores-quemados.md)).
- [ ] Un dato personal nuevo se minimiza, se clasifica y no sale en la página pública.

## 5. Riesgos aceptados y pendientes

| Riesgo | Por qué se acepta | Revisión |
|---|---|---|
| MFA opcional para `BOARD_ADMIN` | Requisito del proyecto; reduce fricción para la Junta | Por definir si pasa a obligatorio |
| Refresh de 30 días para `OPERATOR` | El fontanero trabaja en campo con dispositivos compartidos | Revisar tras la primera operación real |
| Swagger activo en todos los entornos | La documentación no expone datos y los endpoints exigen autenticación | Revisar antes de `v1.0.0` |
| Duración de la retención de logs y de la BD | Sin cifras en los hechos | Por definir |

Relacionados: [`Seguridad-de-la-base-de-datos.md`](Seguridad-de-la-base-de-datos.md), [`Configuracion-sin-valores-quemados.md`](Configuracion-sin-valores-quemados.md), [`../.agents/security.md`](../.agents/security.md), [`../.agents/input-validation.md`](../.agents/input-validation.md), [`../SECURITY.md`](../SECURITY.md)
