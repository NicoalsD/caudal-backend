# Requerimientos

Este documento lista los requisitos funcionales (RF) y no funcionales (RNF) de CAUDAL en el modelo B2B. Cada RF corresponde a una capacidad concreta y se enlaza con una historia de usuario de [Historias-de-usuario.md](Historias-de-usuario.md). Los RNF aplican a todo el sistema o a varios módulos.

Cambio de alcance B2B (2026-11-20): CAUDAL da de alta cada institución y le configura su tanque, su sensor ultrasónico y sus válvulas. Se quitan las reglas versionadas, las propuestas de turnos, la aprobación de la Junta, la publicación pública, las lecturas manuales y sin conexión, los reportes de daño, las entidades de apoyo y las regiones. Se agregan la configuración por institución, el sensor en vivo, el control de válvulas con confirmación, las observaciones manuales y la maqueta con Arduino UNO.

Convenciones:

- Las cifras de seguridad (RNF-01 a RNF-10) son canónicas: vienen de los hechos del proyecto y no se cambian sin una decisión registrada.
- Las metas de rendimiento y usabilidad son **objetivos**, marcados "objetivo, verificar". Se confirman con mediciones en la fase de pruebas.
- "Por definir" significa que el dato no existe todavía y debe decidirse antes de implementar esa parte.
- Repositorios: `B` = `caudal-backend`, `F` = `caudal-frontend`, `IA` = `caudal-ia`, `S` = `caudal-simulador`, `M` = `caudal-maqueta`.
- Módulos: M1 Login e instituciones; M2 Configuración de la institución; M3 Sensor y tanque en vivo; M4 Válvulas y comandos; M5 Pronóstico y eventos; M6 Piloto y asistente; M7 Observaciones, cierre del día y actas; M8 Dispositivos y maqueta; M9 Auditoría; M10 Instituciones simuladas.

## 1. Requisitos funcionales

| ID | Requisito | Módulo | Historia | Repos |
|---|---|---|---|---|
| RF-01 | El usuario elige primero su institución (directorio con `{slug, name}` o ruta `/i/{slug}/login`) y luego entra con usuario y contraseña. El superadmin entra con institución nula. | M1 | HU-01 | B, F |
| RF-02 | Cada rol entra a su pantalla inicial (`/inicio`, `/plataforma/instituciones`). El lector ve la misma vista que el operador, sin controles de mutación. | M1 | HU-02 | B, F |
| RF-03 | Una cuenta nueva o restablecida debe cambiar su contraseña en el primer ingreso antes de usar la API. | M1 | HU-03 | B, F |
| RF-04 | El sistema bloquea una cuenta tras fallos repetidos, con bloqueo escalonado, y limita los intentos por IP e institución. | M1 | HU-04 | B |
| RF-05 | Un usuario cierra sus sesiones en todos los dispositivos, y el sistema revoca familias de tokens reutilizados. | M1 | HU-05 | B, F |
| RF-06 | Los administradores activan un segundo factor (TOTP) con códigos de respaldo de un solo uso. | M1 | HU-06 | B, F |
| RF-07 | El superadmin crea una institución desde una plantilla, con su primer administrador y sus dispositivos, y puede entrar a configurarla con un token temporal auditado (`act_as=platform`). | M1 | HU-07 | B, F |
| RF-08 | El administrador de la institución crea usuarios con rol, y el usuario es único dentro de su institución. | M2 | HU-08 | B, F |
| RF-09 | El administrador configura la forma del tanque (`CYLINDER` o `RECTANGULAR`) y sus medidas. | M2 | HU-09 | B, F |
| RF-10 | El administrador calibra el sensor (vacío y lleno) con la lectura en vivo, con validación de la calibración. | M2 | HU-10 | B, F |
| RF-11 | El administrador define las líneas de servicio, su válvula, el canal del servo y los ángulos abierto y cerrado. Los cambios llegan al puente sin reiniciarlo. | M2 | HU-11 | B, S, F |
| RF-12 | El administrador ajusta los parámetros de la institución (`org.institution_settings`), con auditoría y sin versiones. | M2 | HU-12 | B, F |
| RF-13 | La pantalla muestra en vivo la distancia cruda en mm, el nivel, el porcentaje, los litros y el estado del sensor (`OK`, `NO_ECHO`, `STALE`), con refresco de ~2 s. | M3 | HU-13 | B, F |
| RF-14 | La escena 3D a todo el ancho muestra el tanque, las líneas, las válvulas y el nivel, con polling de ~2 s. | M3 | HU-14 | F, B |
| RF-15 | Un operador o administrador abre o cierra una válvula; el comando pasa por `PENDING`, `DELIVERED` y `ACKED`, y la pantalla lo muestra. | M4 | HU-15 | B, F, S |
| RF-16 | Cada comando muestra su origen, su estado y su vigencia (`not_before`, `expires_at`). Un comando `FAILED` o `EXPIRED` no cambia el estado de la válvula. | M4 | HU-16 | B, S |
| RF-17 | `GET /tanks/{id}/outlook` entrega tres salidas separadas: agua que queda, tiempo hasta la reserva o hasta vaciarse, y banda Chronos p10/p50/p90, cada una con su origen. | M5 | HU-17 | B, IA, F |
| RF-18 | Si la IA falla, responde tarde o su circuito está abierto, el sistema usa la estimación simple y lo indica (`is_fallback`, `fallback_reason`). | M5 | HU-18 | B, IA |
| RF-19 | El sistema detecta sensor pegado, caída anormal, datos viejos y posible fuga, y los administradores confirman o descartan cada anomalía con motivo. | M5 | HU-19 | B, F |
| RF-20 | `GET /events` lista alertas de nivel bajo y crítico, comandos fallidos, anomalías y observaciones. | M5 | HU-20 | B, F |
| RF-21 | El equipo compara la banda Chronos con la estimación simple con MAE, WQL, cobertura y skill por horizonte. | M5 | HU-21 | B, IA, F |
| RF-22 | El administrador elige el modo del piloto (`MANUAL`, `ASSISTED` o `AUTOMATIC`) con el permiso `AUTOPILOT_CONFIGURE`. | M6 | HU-22 | B, F |
| RF-23 | En modo `ASSISTED` se muestran las recomendaciones del piloto (`OPEN`, `CLOSE`, `WAIT`, `INSPECT`); el usuario las acepta o las rechaza, y cada decisión queda en `ops.autopilot_decisions`. | M6 | HU-23 | B, F |
| RF-24 | En modo `AUTOMATIC`, si el operador no actúa en `operator_absence_minutes`, el piloto ejecuta su recomendación. Solo hay dos guardas: el piso de reserva (`reserve_floor_pct`) y la prioridad humana. Cada decisión queda auditada. | M6 | HU-24 | B |
| RF-25 | Un usuario con `ASSISTANT_USE` pregunta en lenguaje natural y recibe una respuesta sobre el estado actual, con su origen (LLM o reglas). El asistente no envía comandos. | M6 | HU-25 | IA, B, F |
| RF-26 | El sistema arma los agregados de un periodo y genera un reporte narrativo (resumen, hallazgos y recomendaciones); se ve en la app y se imprime desde el navegador, sin PDF de servidor. | M6 | HU-26 | B, IA, F |
| RF-27 | El operador registra una observación manual (`WATER_DIRTY`, `WATER_MUDDY`, `VISIBLE_DAMAGE`, `OTHER`) con texto de 10 a 500 caracteres. | M7 | HU-27 | B, F |
| RF-28 | El sistema resume el día (válvulas con su origen, niveles, eventos y observaciones); el operador agrega novedades y cierra el día una sola vez. | M7 | HU-28 | B, F |
| RF-29 | El sistema genera actas en PDF con cuatro secciones: observado, estimado, inferido y confirmado. Una acta final es inmutable. | M7 | HU-29 | B, F |
| RF-30 | El administrador registra dispositivos y sus claves públicas Ed25519; la base de datos nunca guarda claves privadas. | M8 | HU-30 | B, S |
| RF-31 | Un dispositivo envía telemetría firmada con la métrica `DISTANCE` en mm (`-1` sin eco), con ventana de tiempo y nonce único. | M8 | HU-31 | B, S |
| RF-32 | La maqueta física (Arduino UNO por USB, HC-SR04 y dos servos MG90S) envía telemetría y ejecuta órdenes de válvula con ack, a través del puente `caudal-sim maqueta-usb`. | M8 | HU-32 | M, B |
| RF-33 | El simulador incluye una maqueta virtual que usa el mismo protocolo firmado (telemetría, `GET /devices/commands` y ack). | M8 | HU-33 | S, B |
| RF-34 | Quien tiene `AUDIT_VIEW` consulta la bitácora de la institución y los eventos de seguridad, sin secretos. | M9 | HU-34 | B, F |
| RF-35 | El superadmin importa historial simulado solo en instituciones con `is_simulated` verdadero. | M10 | HU-35 | B, S |
| RF-36 | Todo dato simulado se marca "Datos simulados" en pantallas, PDF y mensajes, y en la API (`simulated`). | M10 | HU-36 | B, F, S |
| RF-37 | Toda operación se autoriza por permiso (leído de la BD), por membresía vigente y por institución (transversal). | M1, M2 | HU-02, HU-08, HU-15, HU-19, HU-22, HU-28 | B |
| RF-38 | Toda entrada se valida con los límites de `FieldLimits` y la normalización de texto (transversal). | M2, M7 | HU-09, HU-15, HU-27 | B, F |
| RF-39 | Cada recurso pertenece a una institución: un recurso de otra institución responde `404` y el login no revela usuarios de otras instituciones (transversal). | M1, M2 | HU-01, HU-07, HU-08 | B, F |

## 2. Requisitos no funcionales

### RNF-01 Seguridad de contraseñas

- Argon2id con `Argon2PasswordEncoder` (BouncyCastle): m = 19456 KiB (19 MiB), t = 2, p = 1, sal de 16 bytes, hash de 32 bytes. Los parámetros se leen de variables de entorno; si cambian, se rehace el hash al iniciar sesión.
- Longitud de 12 a 128 caracteres, normalizados NFKC. Sin reglas de composición.
- Se rechazan las contraseñas comunes (lista local) y las que contienen el usuario.
- Historial de las últimas 5 contraseñas (configurable).
- Cambio obligatorio en el primer ingreso (contraseña temporal o de activación).
- Se guardan hashes; no se cifran y no se recuperan. Nunca aparecen en logs, respuestas, URLs ni en claro en la base de datos.

### RNF-02 Tokens y sesiones

- Access token JWT HS256 de 15 minutos. Secreto de al menos 256 bits. Claims `iss`, `aud`, `sub`, `role`, `institution_id`, `tv` (token_version), `jti`, `iat`, `exp`. El token de plataforma lleva `scope=platform` y no lleva `institution_id`.
- Validación de `alg` explícito, `iss`, `aud`, `exp` y `nbf`, con tolerancia de 30 segundos.
- Refresh token opaco de 256 bits, guardado como SHA-256. Rotación en cada uso. Reutilizar un token revoca toda su familia.
- Cookie `caudal_rt` con `HttpOnly; Secure; SameSite=None; Path=/api/v1/auth`. Frontend y API están en sitios distintos, así que se exige la cabecera `X-Requested-With: caudal-web` y la verificación de `Origin`.
- Duración del refresh token: 7 días, y 30 días para `OPERATOR` (riesgo aceptado, ver [Seguridad.md](Seguridad.md)). Configurable.
- Cambiar contraseña, rol o desactivar la cuenta incrementa `token_version` y revoca los refresh tokens.
- El access token vive solo en la memoria del frontend.
- Una cabecera `Authorization` de más de 2 KiB se rechaza antes de parsearla.
- Token de `act_as=platform`: 15 minutos, no renovable (la regla de reingreso está por definir).

### RNF-03 Protección contra fuerza bruta y abuso

- Bloqueo por cuenta: 5 fallos en 15 minutos bloquean la cuenta 15 minutos. Cada bloqueo nuevo dura el doble, con máximo de 24 horas.
- Límite en login: 20 intentos en 15 minutos por IP e institución.
- Rate limits configurables: login 5 por minuto por (IP, institución); API 120 por minuto por usuario; telemetría 60 por minuto por dispositivo (`RATE_LIMIT_TELEMETRY_PER_MINUTE`); recálculo de pronóstico 10 por hora por institución; directorio público por IP (valor por definir).
- Respuestas `429` con `Retry-After`.

### RNF-04 Validación de entradas

- Cuerpo JSON de hasta 64 KiB. Telemetría hasta 256 KiB. Importación hasta 1 MiB o 5.000 filas por lote.
- Profundidad JSON máxima de 10. Listas de hasta 100 elementos, salvo que el campo indique otro límite. Texto global de hasta 2.000 caracteres.
- Campos desconocidos se rechazan (`FAIL_ON_UNKNOWN_PROPERTIES`). Sin coerción de tipos.
- Normalización NFC. Recorte de espacios y colapso de espacios internos en nombres.
- Se rechazan caracteres de control (salvo salto de línea en textos largos), caracteres bidi (U+202A a U+202E, U+2066 a U+2069) y de ancho cero (U+200B a U+200F, U+FEFF).
- Identificadores en UUID. Paginación de 1 a 100 (por defecto 20) con cursor opaco firmado.
- Fechas en ISO-8601 con zona horaria, no futuras (más de 5 minutos).
- Tabla canónica de campos (valores en `FieldLimits`):

| Campo | Mínimo | Máximo | Regla |
|---|---|---|---|
| `username` | 3 | 32 | `^[a-z0-9][a-z0-9._-]{1,30}[a-z0-9]$`, en minúsculas, único por institución |
| `password` | 12 | 128 | NFKC, lista de comunes, sin el usuario |
| `full_name` | 2 | 80 | `^[\p{L}][\p{L}\p{M} .'-]*$` |
| `institution.name` | 3 | 100 | letras, dígitos, espacio, `-.,()` |
| `institution.slug` | 3 | 40 | `^[a-z0-9]+(-[a-z0-9]+)*$` |
| `service_line.name`, `tank.name`, `valve.name` | 2 | 60 | letras, dígitos, espacio, `-.#()` |
| `valve.code` | 1 | 20 | `^[A-Z0-9-]+$` |
| `valve.servo_channel` | 1 | por definir | entero; único por institución (propuesto) |
| `valve.open_angle_deg`, `valve.closed_angle_deg` | 0 | 180 | entero; rango físico del servo |
| `tank.height_mm`, `tank.diameter_mm`, `tank.length_mm`, `tank.width_mm` | por definir | por definir | milímetros, entero positivo |
| `distance_mm` (telemetría) | -1 | 4000 | entero; `-1` es sin eco; alcance del HC-SR04 en `co.caudal.shared` |
| `observation.text` | 10 | 500 | sin control salvo `\n`, máximo 10 líneas |
| `day_closure.notes` | 0 | 500 | sin control salvo `\n`, máximo 10 líneas |
| `anomaly.dismiss_reason` | 10 | 500 | obligatorio al descartar una anomalía |
| `valve_command.manual_reason` | 10 | 500 | opcional; si falta se guarda el texto por defecto |
| `assistant.question` | 1 | por definir | texto no vacío |
| `catalog_item.code` | 2 | 40 | `^[A-Z][A-Z0-9_]*$` |
| `catalog_item.label_es` | 2 | 60 | texto seguro |
| `institution_settings.*_pct` | 0 | 100 | porcentaje |
| `page.limit` | 1 | 100 | entero |
| `telemetry.batch` | 1 | 100 | puntos por petición |

### RNF-05 Salida y errores

- Formato de error: `{"error":{"code":"ENGLISH_CODE","message":"Mensaje en español","details":{},"request_id":"..."}}`.
- Sin trazas de pila en las respuestas.
- Los mensajes no revelan si un usuario existe ni en qué institución.

### RNF-06 Cabeceras HTTP, CORS y CSP

- API: HTTPS con HSTS; `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` (en `/swagger-ui/**`: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'`); `X-Content-Type-Options: nosniff`; `Referrer-Policy: no-referrer`; `Permissions-Policy`.
- CORS con lista de orígenes desde una variable de entorno. Nunca `*` con credenciales.
- Frontend (cabecera HTTP en `vercel.json`): `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; worker-src 'self'; manifest-src 'self'; connect-src 'self' <API_ORIGIN>; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`.

### RNF-07 Autorización y RLS

- Deny-by-default. Los permisos se leen de `iam.role_permissions`; el código solo conoce códigos.
- Chequeo por objeto (institución) en cada recurso. Un recurso de otra institución responde 404.
- RLS con `FORCE ROW LEVEL SECURITY` en las tablas con `rls: true`, filtrado por `app.institution_id` (función `audit.current_institution_id()`).
- Privilegios por columna para `password_hash`, `token_hash` y secretos MFA.
- Operaciones entre instituciones del superadmin solo por funciones `SECURITY DEFINER` del esquema `platform`.
- Pruebas: cada permiso tiene al menos una prueba de 403 para un rol que no lo tiene, y una prueba de RLS que intenta leer otra institución.
- Detalle de roles y reglas por objeto: [Actores-y-permisos.md](Actores-y-permisos.md).

### RNF-08 Dispositivos firmados

- Ed25519 por dispositivo. La base de datos guarda solo la clave pública, con rotación.
- Cabeceras: `X-Device-Id`, `X-Timestamp` (unix en segundos), `X-Nonce` (128 bits en hexadecimal), `X-Signature` (Ed25519 en base64url).
- La firma cubre `método\nruta\ntimestamp\nnonce\nsha256(cuerpo)`.
- Ventana de tiempo de ±300 segundos. Cada nonce es único durante 600 segundos (`DEVICE_NONCE_TTL_SECONDS`).
- Los comandos de válvula salen solo de órdenes manuales con motivo (permiso `VALVE_COMMAND_MANUAL`) o de una decisión del piloto automático (`autopilot_decision_id`), con `not_before` y `expires_at`.
- La configuración del puente (`GET /devices/config`) también va firmada.

### RNF-09 Secretos y datos en logs

- Ningún secreto en git. `.env.example` con marcadores. Escaneo con gitleaks, secret scanning y push protection activos.
- Rotación de secretos documentada.
- Logs en JSON con `request_id`. Redacción de `Authorization`, `Cookie`, `password`, `token` y `secret`.
- La IP se guarda como HMAC.

### RNF-10 Seguridad del servicio de IA

- Token de servicio de al menos 32 bytes aleatorios en `Authorization: Bearer`. Comparación en tiempo constante. Rotación: se acepta el token actual y el anterior.
- Series de hasta 2.000 puntos, valores finitos, timestamps crecientes. `prediction_length` de hasta 30 (límite técnico).
- El servicio no almacena datos y no accede a la base de datos. Los endpoints del asistente (`/v1/insights/*`) usan el mismo token de servicio (ver RNF-29 y RNF-30).
- Timeouts desde el backend: conexión de 2 segundos, lectura de 10 segundos para el pronóstico y de 20 segundos para el asistente (`INSIGHTS_READ_TIMEOUT_MS`). Circuit breaker (ver RNF-16).

### RNF-11 Seguridad del frontend

- CSP estricta (RNF-06). El access token va en memoria. Nada sensible en `localStorage`.
- El frontend no guarda contraseñas, tokens ni secretos en el almacenamiento del navegador.
- Prohibido `dangerouslySetInnerHTML` (verificado con ESLint). React escapa el contenido por defecto.
- Al cerrar sesión no queda en el navegador ningún dato operativo de la institución (no hay cola sin conexión, ver RNF-14).

### RNF-12 Usabilidad del operador

Cambio de alcance B2B (2026-11-20): el operador usa la app desde el celular junto al tanque y en una pantalla de escritorio. Se diseña para pocos toques y lectura rápida.

- Diseño mobile first. El operador abre o cierra una válvula en dos toques o menos (objetivo, verificar).
- Textos en español sencillo. Las siglas técnicas (`ack`, `p10`, `MAE`) no aparecen en la pantalla principal; se explican en la ayuda.
- Objetivos táctiles de al menos 44 × 44 px (criterio de diseño propio, verificar).
- Estado de conexión con el servidor siempre visible. Ninguna acción queda sin respuesta visible: el comando muestra `PENDING`, `DELIVERED` o `ACKED`.
- Números con coma decimal ("2,1").

### RNF-13 Accesibilidad

- Cumplimiento de WCAG 2.1 nivel AA.
- Contraste de 4,5:1 para texto normal y de 3:1 para texto grande.
- Navegación completa con teclado y foco visible.
- La información nunca depende solo del color: la tendencia usa flecha y texto, la banda usa etiqueta y color, y el estado de la válvula usa texto.
- Formularios con etiquetas asociadas y mensajes de error ligados al campo.
- Atributo `lang="es"` en la página.
- Auditoría automatizada con axe en las pruebas de Playwright.

### RNF-14 Funcionamiento sin conexión

Estado: **eliminado en el alcance B2B** (2026-11-20). Se quitan la cola sin conexión en IndexedDB, la sincronización por lotes, la caché de reglas para validar sin señal y el aviso de cola pendiente. El tanque en vivo y la escena necesitan conexión (RNF-34). Si el servidor no responde, la pantalla muestra el último dato con su antigüedad y no inventa un nivel (RNF-16).

### RNF-15 Rendimiento (objetivos, verificar)

| Operación | Objetivo p95 | Notas |
|---|---|---|
| `GET /tanks/{id}/live` | 500 ms | Lee la última muestra válida. Sin llamar a la IA. |
| `GET /tanks/{id}/outlook` (dos primeras salidas) | 500 ms | Agua que queda y tiempo a la reserva son cálculos deterministas. |
| `POST /devices/telemetry` con 100 puntos | 3 s | Incluye verificación de firma y cadena de validación. |
| `POST /valve-commands/manual` | 800 ms | Solo crea el comando. El ack del actuador tiene su propio objetivo (RNF-33). |
| Pronóstico de la IA (banda Chronos) | 10 s | Límite de lectura de RNF-10. |
| Escena 3D en vivo | Actualización cada ~2 s | Polling. El rendimiento de dibujo no es prioridad en esta fase. |
| Recomendación o pregunta al asistente | 15 s (por definir) | Con respaldo por reglas si pasa `INSIGHTS_READ_TIMEOUT_MS` (20 s). |
| Acta en PDF | 10 s | |
| Resumen del día (`GET /days/{date}/closure`) | por definir | Depende del número de eventos del día. |

Todas las metas son objetivos hasta medirlas en la fase de pruebas.

### RNF-16 Disponibilidad y degradación

- Si la IA falla, responde tarde o su circuito está abierto, el sistema usa la estimación simple. Ninguna operación se bloquea por la IA, y el piloto decide sin el LLM. Lo mismo vale para el asistente: ver RNF-29.
- Circuit breaker con estados `Cerrado`, `Abierto` y `Semiabierto`.
- Hosting gratuito: el backend en Render puede dormirse. La app muestra un indicador de arranque y la primera petición tiene un tiempo de espera mayor (objetivo, verificar). Monitoreo externo de salud (por definir).
- Copias y recuperación: PITR de Neon. La retención depende del plan (verificar).
- Si el servidor no responde, el tanque en vivo muestra el último dato con su antigüedad y no inventa un nivel.

### RNF-17 Mantenibilidad y arquitectura

- Backend hexagonal: `api` → `application` → `domain` ← `infrastructure`.
- ArchUnit: `domain` no depende de Spring, JPA ni Jackson. Sin ciclos entre paquetes.
- Linters: Checkstyle (`MagicNumber`), SpotBugs con FindSecBugs, Spotless (google-java-format). Frontend: ESLint (`no-magic-numbers`), Prettier. IA: Ruff (`PLR2004`), mypy.
- Frontend por capas: `src/app`, `src/features`, `src/state`, `src/services`, `src/core`, `src/ui`.
- Cobertura mínima con JaCoCo y Vitest: por definir.

### RNF-18 Patrones de diseño explícitos

- Cada patrón del catálogo (P01 a P23) tiene interfaz y clases propias, un caso de uso real, una prueba y un dueño.
- Se documenta qué ofrece el framework que no reemplaza el patrón (por ejemplo, los beans singleton de Spring no sustituyen a `SystemClock`).
- El catálogo completo vive en [Patrones-de-diseno.md](Patrones-de-diseno.md).

### RNF-19 Trazabilidad y auditoría

- Las tablas de auditoría y de decisiones (`audit.audit_log`, `ops.autopilot_decisions`, `ops.observations`) solo admiten `INSERT` y `SELECT` para `caudal_app`. No hay `DELETE` salvo purgas por funciones `SECURITY DEFINER`.
- Cada acción relevante deja registro en `audit.audit_log` con actor, acción, recurso, institución y motivo cuando aplica.
- La cadena de hashes de auditoría se ancla en `audit.audit_anchors` con un trigger.
- Los cambios de configuración (tanque, calibración, líneas, válvulas y parámetros) guardan el valor anterior, el valor nuevo y quién lo hizo.
- Los comandos de válvula y las decisiones del piloto guardan quién los emitió (usuario o `AUTOPILOT`) y el motivo cuando aplica.

### RNF-20 Privacidad (Ley 1581 de 2012)

- Minimización: no se guardan teléfonos ni datos de personas que el servicio no necesite. Solo se pide lo que el servicio necesita.
- El directorio público solo expone `{slug, name}` de las instituciones activas (`PublicInstitutionDirectoryProxy`). No expone tanques, usuarios ni datos de operación.
- Aviso de privacidad versionado, aceptado por cada usuario (`POST /privacy-notice/accept`).
- Clasificación de cada columna: pública, interna, confidencial o secreta (`schema.yaml`).
- Retención configurable en `audit.retention_policies`. Plazos por definir.
- Procedimiento para que el titular consulte o corrija sus datos: por definir.

### RNF-21 Idioma

- La interfaz, los mensajes para el usuario, los documentos y los diagramas van en español.
- El código, las rutas, los campos JSON, los enums, los códigos de error, los logs y los nombres de tests van en inglés.
- Los textos de la interfaz viven en `src/i18n/es.ts` y `messages_es.properties`, con claves en inglés e interpolación ("Máximo {max}").

### RNF-22 Sin valores quemados

- Parámetros de negocio en la base de datos: `org.tanks` (geometría y calibración), `org.valves` y `org.service_lines` (canal, ángulos y nombre), y `org.institution_settings` (umbrales, pronóstico y piloto). Se leen con `GET /api/v1/tanks`, `GET /api/v1/service-lines` y `GET /api/v1/institution/settings`. El catálogo se lee con `GET /api/v1/catalogs/{catalog}`.
- Límites técnicos y de seguridad en la clase `FieldLimits` (constantes `public static final`). Alimentan `@Size`, `@Pattern`, `@Column(length)`, placeholders de Flyway y `GET /api/v1/meta/constraints`.
- Pruebas de deriva: base de datos contra `FieldLimits`, y endpoint contra `FieldLimits`.
- Entorno en variables (`application.yml` con `${VAR}`, pydantic-settings, `VITE_API_BASE_URL`), documentadas en `.env.example`.
- Escenarios del simulador en YAML, sin secretos.
- Solo constantes físicas con nombre (`HOURS_PER_DAY = 24`, alcance del HC-SR04 de 4000 mm).

### RNF-23 Portabilidad y despliegue

- Entorno local con Docker Compose (PostgreSQL 18 local).
- Despliegue: Render (backend, Docker), Neon (PostgreSQL), Vercel (frontend), Render free (IA, Docker, con `amazon/chronos-bolt-tiny`).
- Cambiar de proveedor debe requerir cambiar variables de entorno y no código (objetivo, verificar).

### RNF-24 Observabilidad

- Logs en JSON con `request_id`, sin datos sensibles (RNF-09).
- `GET /actuator/health` público y mínimo.
- Swagger en `/swagger-ui.html` y OpenAPI en `/v3/api-docs`, activos en todos los entornos y apagables con `API_DOCS_ENABLED=false`. Ejecutar requiere JWT.
- Métricas de negocio (pronósticos por respaldo, comandos fallidos): por definir.

### RNF-25 Tiempo y formato

- La base de datos guarda la hora en UTC (`timestamptz`). La interfaz la muestra en `America/Bogota`.
- Números decimales con coma en la interfaz ("2,1"). En la API, el punto decimal (JSON estándar).
- "Hace X segundos" y la antigüedad de la lectura se calculan en el servidor con el reloj inyectable (`SystemClock`).

### RNF-26 Base de datos

- PostgreSQL 18 con ocho esquemas: `iam`, `org`, `ops`, `reporting`, `devices`, `audit`, `sim` y `platform` (este último solo con funciones `SECURITY DEFINER`).
- Roles: `caudal_migrator` (dueño, único con DDL), `caudal_app` (DML mínimo), `caudal_readonly` (vistas de reportes).
- Tiempos límite del rol de la aplicación (`caudal_app`): `statement_timeout` de 5 s, `lock_timeout` de 3 s, `idle_in_transaction_session_timeout` de 10 s. `caudal_readonly` tiene `statement_timeout` de 15 s.
- Conexión TLS con `sslmode=verify-full`.
- Solo consultas parametrizadas (JPA, criteria o parámetros nombrados). Nunca concatenación de SQL.
- Identificadores `uuidv7()`. Claves foráneas con `ON DELETE RESTRICT`.
- Restricciones `UNIQUE` (por ejemplo, `(institution_id, username)`) y `CHECK` (`manual_reason XOR autopilot_decision_id` en `devices.valve_commands`).

### RNF-27 Pruebas automatizadas

- Backend: JUnit 5 o 6, AssertJ, Mockito, Testcontainers (Docker), ArchUnit, JaCoCo.
- Frontend: Vitest, Testing Library, MSW, Playwright con axe.
- IA: pytest, Hypothesis.
- Pruebas de contrato: el OpenAPI del backend genera los tipos del frontend (openapi-typescript).
- Cobertura objetivo: por definir.

### RNF-28 Transparencia de los datos

- Cada dato lleva su estatus epistémico: `OBSERVED`, `ESTIMATED`, `INFERRED` o `CONFIRMED`. Ninguna pantalla presenta un estimado como observado.
- Lo simulado se declara con la marca "Datos simulados" y con el campo `simulated` en la API (RF-36).
- El pronóstico siempre muestra su rango y el modelo usado (o que es la estimación simple).
- El acta y la pantalla dicen que `ACKED` significa que el actuador respondió, no que el agua fluyó.

### RNF-29 Respaldo determinista del asistente

- El asistente de IA (preguntas y redacción de reportes) usa un LLM compatible con OpenAI (por defecto Nemotron de NVIDIA, gratis en OpenRouter; alternativas Gemini Flash o Groq), configurado con `LLM_BASE_URL`, `LLM_MODEL` y `LLM_API_KEY`.
- El LLM no decide ni envía comandos. Las recomendaciones del piloto las genera el piloto con sus reglas, y no dependen del LLM.
- Si el LLM falla, responde tarde o agota su cuota, se aplican reglas simples y textos de plantilla. El asistente siempre responde.
- Cada respuesta indica su origen (`source`: LLM o RULES). Las salidas del LLM se validan contra un esquema (Pydantic); si no cumplen, se usa el respaldo.
- El backend llama a `caudal-ia` con el mismo cliente con circuit breaker del pronóstico.

### RNF-30 Secretos y datos enviados al LLM

- `LLM_API_KEY` se guarda como secreto del servicio (Render) y nunca va en el repositorio ni en los logs (ver RNF-09).
- Al LLM solo se envía la foto del estado necesaria: `outlook`, eventos y válvulas con el nombre de su línea. No se envían contraseñas, tokens, teléfonos ni nombres de personas (RNF-20).
- Los límites de cuota y las cifras del proveedor se verifican en su panel antes de implementar (por definir).

### RNF-31 Interfaz a todo el ancho y escena 3D

- El contenido usa todo el ancho de la pantalla en cualquier tamaño; solo el texto largo conserva una medida de lectura.
- La escena 3D (three.js) se usa en la pantalla de inicio. Las válvulas se pulsan con el puntero y también tienen botones equivalentes para el teclado y los lectores de pantalla (RNF-13).
- La vista en vivo hace polling cada ~2 s; no se usa SSE en esta versión.
- El rendimiento de dibujo no es una prioridad en esta fase.

### RNF-32 Guardas del piloto y posición de seguridad

- El piloto automático solo tiene dos guardas: el piso de reserva (`reserve_floor_pct`) y la prioridad humana (cualquier acción manual lo pausa).
- Todo comando del piloto queda ligado a una decisión en `ops.autopilot_decisions` y a `audit.audit_log`.
- Ante pérdida de contacto, la válvula aplica `fail_safe_position` (valor y disparador por definir).
- Un comando sin motivo manual y sin decisión del piloto se rechaza (restricción de la base de datos).

### RNF-33 Latencia del lazo de válvulas (nuevo)

- Desde `POST /valve-commands/manual` hasta `ACKED`, con el puente conectado y la placa respondiendo, el comando termina en 5 segundos o menos (objetivo, verificar en la prueba de banco de la fase 0).
- Se mide en la pantalla: el estado pasa por `PENDING`, `DELIVERED` y `ACKED` sin recargar.

### RNF-34 Refresco del sensor en vivo (nuevo)

- El puente envía la distancia cada ~2 segundos y la pantalla consulta `GET /tanks/{id}/live` cada ~2 segundos (objetivo, verificar).
- La distancia cruda en mm se muestra siempre junto al nivel, para que una calibración errónea se detecte.

### RNF-35 Aislamiento entre instituciones (nuevo)

- Cada tabla con `institution_id` tiene RLS con `FORCE`. La aplicación fija `app.institution_id` al inicio de cada transacción a partir del claim `institution_id`.
- Las pruebas de CI cubren dos instituciones: un usuario de una no lee ni modifica datos de la otra (404), y el login no revela si un usuario existe en otra institución.
- El superadmin opera entre instituciones solo con funciones `SECURITY DEFINER` (`platform.create_institution`, `platform.list_institutions`) y su token de plataforma.

## 3. Trazabilidad

Cada RF sale de una historia (HU) y de un módulo. La tabla enlaza el requisito con los repositorios donde se implementa.

| RF | HU | Módulo | Repositorio principal | Repositorios secundarios | RNF principales |
|---|---|---|---|---|---|
| RF-01 | HU-01 | M1 Login e instituciones | B | F | RNF-01, RNF-02, RNF-05, RNF-11 |
| RF-02 | HU-02 | M1 Login e instituciones | F | B | RNF-07, RNF-31 |
| RF-03 | HU-03 | M1 Login e instituciones | B | F | RNF-01, RNF-04 |
| RF-04 | HU-04 | M1 Login e instituciones | B | | RNF-03, RNF-19 |
| RF-05 | HU-05 | M1 Login e instituciones | B | F | RNF-02, RNF-19 |
| RF-06 | HU-06 | M1 Login e instituciones | B | F | RNF-01, RNF-02, RNF-09 |
| RF-07 | HU-07 | M1 Login e instituciones | B | F | RNF-07, RNF-19, RNF-35 |
| RF-08 | HU-08 | M2 Configuración | B | F | RNF-07, RNF-19 |
| RF-09 | HU-09 | M2 Configuración | B | F | RNF-04, RNF-17, RNF-22 |
| RF-10 | HU-10 | M2 Configuración | B | F | RNF-19, RNF-22, RNF-34 |
| RF-11 | HU-11 | M2 Configuración | B | S, F | RNF-04, RNF-08, RNF-22 |
| RF-12 | HU-12 | M2 Configuración | B | F | RNF-19, RNF-22 |
| RF-13 | HU-13 | M3 Sensor y tanque en vivo | B | F | RNF-12, RNF-13, RNF-15, RNF-25, RNF-34 |
| RF-14 | HU-14 | M3 Sensor y tanque en vivo | F | B | RNF-13, RNF-31 |
| RF-15 | HU-15 | M4 Válvulas y comandos | B | F, S | RNF-08, RNF-12, RNF-19, RNF-32, RNF-33 |
| RF-16 | HU-16 | M4 Válvulas y comandos | B | S | RNF-08, RNF-19, RNF-33 |
| RF-17 | HU-17 | M5 Pronóstico y eventos | B | IA, F | RNF-10, RNF-25, RNF-28 |
| RF-18 | HU-18 | M5 Pronóstico y eventos | B | IA | RNF-10, RNF-16 |
| RF-19 | HU-19 | M5 Pronóstico y eventos | B | F | RNF-19, RNF-28 |
| RF-20 | HU-20 | M5 Pronóstico y eventos | B | F | RNF-04, RNF-28 |
| RF-21 | HU-21 | M5 Pronóstico y eventos | B | IA, F | RNF-18, RNF-25 |
| RF-22 | HU-22 | M6 Piloto y asistente | B | F | RNF-07, RNF-19, RNF-22 |
| RF-23 | HU-23 | M6 Piloto y asistente | B | F | RNF-08, RNF-19, RNF-29 |
| RF-24 | HU-24 | M6 Piloto y asistente | B | IA | RNF-08, RNF-19, RNF-29, RNF-32 |
| RF-25 | HU-25 | M6 Piloto y asistente | IA | B, F | RNF-04, RNF-29, RNF-30 |
| RF-26 | HU-26 | M6 Piloto y asistente | B | IA, F | RNF-20, RNF-28, RNF-29 |
| RF-27 | HU-27 | M7 Observaciones, cierre y actas | B | F | RNF-04, RNF-19 |
| RF-28 | HU-28 | M7 Observaciones, cierre y actas | B | F | RNF-19, RNF-22 |
| RF-29 | HU-29 | M7 Observaciones, cierre y actas | B | F | RNF-19, RNF-22, RNF-28 |
| RF-30 | HU-30 | M8 Dispositivos y maqueta | B | S | RNF-08, RNF-19 |
| RF-31 | HU-31 | M8 Dispositivos y maqueta | B | S | RNF-04, RNF-08, RNF-15 |
| RF-32 | HU-32 | M8 Dispositivos y maqueta | M | B | RNF-08, RNF-32, RNF-33 |
| RF-33 | HU-33 | M8 Dispositivos y maqueta | S | B | RNF-08, RNF-22 |
| RF-34 | HU-34 | M9 Auditoría | B | F | RNF-09, RNF-19, RNF-24 |
| RF-35 | HU-35 | M10 Instituciones simuladas | B | S | RNF-04, RNF-07 |
| RF-36 | HU-36 | M10 Instituciones simuladas | B | F, S | RNF-21, RNF-28 |
| RF-37 | HU-02, HU-08, HU-15, HU-19, HU-22, HU-28 | M1, M2 | B | | RNF-07 |
| RF-38 | HU-09, HU-15, HU-27 | M2, M7 | B | F | RNF-04, RNF-22 |
| RF-39 | HU-01, HU-07, HU-08 | M1, M2 | B | F | RNF-07, RNF-35 |

Resumen: las 36 historias cubren los 39 RF. RF-37, RF-38 y RF-39 son transversales y se reflejan en varias historias. Los RNF-33, RNF-34 y RNF-35 son nuevos en el alcance B2B.

Relacionados: [Historias-de-usuario.md](Historias-de-usuario.md) · [Actores-y-permisos.md](Actores-y-permisos.md) · [Riesgos.md](Riesgos.md) · [Caudal.md](Caudal.md) · [Reglas-de-negocio.md](Reglas-de-negocio.md)
