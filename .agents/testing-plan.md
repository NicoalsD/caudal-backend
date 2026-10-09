# Plan de pruebas del backend

> Estrategia de pruebas de `caudal-backend` para agentes y para el equipo. Define qué se prueba en cada nivel, qué casos cubre cada módulo, cómo se nombran las pruebas, con qué datos y con qué comandos. Los comandos se marcan como disponibles desde la Fase 1 porque dependen del esqueleto del proyecto.

## 1. Pirámide de pruebas

| Nivel | Qué prueba | Herramientas | Arranca Spring | Arranca Docker | Ejecución típica |
|---|---|---|---|---|---|
| Unitaria de dominio | Reglas, estados, patrones, cálculos. | JUnit 5/6, AssertJ | No | No | Segundos. Siempre. |
| Unitaria de aplicación | Casos de uso con puertos simulados (Mockito solo en puertos). | JUnit, AssertJ, Mockito | No | No | Segundos. Siempre. |
| Slice web | Validación de entrada (422), autenticación (401), permisos (403), forma de la respuesta y de los errores. | `@WebMvcTest`, MockMvc | Parcial | No | Segundos. Siempre. |
| Integración | Repositorios, migraciones Flyway, triggers, RLS, constraints `EXCLUDE` y `UNIQUE`, roles de BD. | `@DataJpaTest` o `@SpringBootTest` con Testcontainers PostgreSQL 18 | Sí | Sí | Minutos. En CI y antes de integrar. |
| Arquitectura | Reglas de dependencia y ciclos. | ArchUnit | No | No | Segundos. Siempre. |
| Seguridad | Login, tokens, cabeceras, límites, firmas de dispositivos, IDOR, RLS entre acueductos. | Mezcla de los anteriores | Sí | Sí | Parte de la integración. |
| Deriva | Coherencia entre `FieldLimits`, la BD y `/api/v1/meta/constraints`. | `@SpringBootTest`, Testcontainers | Sí | Sí | Parte de la integración. |
| Contrato con la IA | El adaptador interpreta las respuestas válidas y los errores. | Servidor HTTP simulado | Parcial | No | Segundos. Siempre. |

Regla: si una prueba puede escribirse en un nivel más bajo, se escribe ahí. Una prueba de integración no reemplaza una prueba de dominio.

## 2. Cobertura (JaCoCo)

| Alcance | Umbral mínimo | Métrica | Dónde se verifica |
|---|---|---|---|
| `co.caudal.domain..` | 90 % | Líneas | `./mvnw verify`, regla de JaCoCo por paquete. |
| Proyecto completo | 80 % | Líneas | `./mvnw verify`. |

Notas:
- Las ramas (branches) se reportan, pero el umbral inicial es de líneas. Si el equipo decide exigir ramas, se documenta aquí (verificar).
- Las clases generadas (entidades de Lombok, mappers generados, `FieldLimits`) pueden excluirse del cálculo. La lista de exclusiones se revisa en cada PR.
- Un PR que baja la cobertura por debajo del umbral no se fusiona.

## 3. Matriz de casos por módulo

La columna "Nivel" indica dónde vive la prueba principal. Las pruebas de seguridad aparecen en la sección 6.

| Módulo | Casos clave | Nivel |
|---|---|---|
| M1 Login y usuarios | Contraseña de 12 a 128 caracteres tras NFKC; contraseña común rechazada; contraseña con el nombre de usuario rechazada; historial de 5; cambio obligatorio en el primer ingreso; bloqueo tras 5 fallos en 15 min; escalamiento; mensaje genérico; hash de Argon2id con los parámetros de configuración; rehash al cambiar parámetros. | Dominio, slice web, integración, seguridad |
| M2 Reglas versionadas | Bandas sin solape (`EXCLUDE`); bandas que cubren el rango del tanque; regla activa inmutable (trigger); copia de versión (prototipo); activación con motivo; `max_daily_service_hours` entre 0 y 24. | Dominio, integración |
| M3 Lecturas offline | Validación en cadena (rango, fecha, duplicado, salto, antigüedad); idempotencia por UUID; lote con aceptados y rechazados; corrección sin borrar el original; `effective_readings` usa la última corrección; rango del tanque por trigger; RLS entre acueductos; `MISSING_TIMESTAMP`, `FUTURE_TIMESTAMP`, `TOO_OLD` y `GAUGE_OUT_OF_RANGE` responden 422 sin guardar; `DUPLICATE` y `SUDDEN_JUMP` se guardan `FLAGGED`; repetir un UUID con el mismo contenido responde 200 y con otro contenido responde `409 DUPLICATE_READING`. | Dominio, aplicación, slice web, integración |
| M4 Estado y pronóstico | Lectura vigente y vieja (`STALE`); tendencia en el umbral; pronóstico con respaldo (`is_fallback = true`); circuito abierto; `INSUFFICIENT_HISTORY`; límite de 10 recálculos por hora. | Dominio, aplicación, contrato con la IA, integración |
| M5 Propuesta de turnos | Orden de prioridad y de mayor espera; turnos entre `min_shift_hours` y `max_shift_hours`; sin solapes (`EXCLUDE` con `tstzrange`); cada turno con su razón; horas dentro de la banda; respaldo de la reserva. | Dominio, integración |
| M6 Decisión de la Junta | Transiciones de `ProposalState`; motivo de 10 a 500 caracteres; `lock_version` (409 si cambió); memento antes del cambio; publicación solo desde estados aprobados; permiso por rol. | Dominio, aplicación, slice web, integración |
| M7 Publicación y reportes | Página pública sin nombres ni teléfonos; horario no publicado no se muestra; texto de WhatsApp sin datos personales; cartel PDF; reporte de daño con honeypot, tiempo mínimo y límite de 3 por hora; código de seguimiento de 8 caracteres Crockford; estados `REPORTED`, `VERIFYING`, `CONFIRMED`, `RESOLVED`, `DISMISSED`. | Dominio, aplicación, slice web, integración, seguridad |
| M8 Cierre del día | Turno cumplido, a medias o no ejecutado; reemplazo con `supersedes_id` sin editar el original; un cierre por día y acueducto (`UNIQUE`); notas de hasta 500 caracteres en 10 líneas. | Dominio, integración |
| M9 Actas y resúmenes | Cuatro secciones (visitante); acta `FINAL` inmutable; hash del PDF; ancla de auditoría en el acta; autorización de resumen vigente y no vencida; registro en `data_access_log`; resumen revocado no se muestra. | Dominio, integración, seguridad |
| M10 Evaluación IA vs simple | MAE del p50; WQL; cobertura del intervalo [p10, p90]; skill por horizonte (1, 2 y 3 días); criterio de "la IA ayuda" (skill > 0 con al menos 30 pronósticos y cobertura entre 70 % y 90 %). | Dominio |
| M11 Dispositivos | Firma Ed25519 válida e inválida; ventana de ±300 s; nonce repetido rechazado; nonce expirado tras 10 min; telemetría idempotente por id; comando de válvula solo desde turno aprobado o con motivo; `not_before` y `expires_at`; rotación de claves. | Dominio, slice web, integración, seguridad |
| M12 Auditoría | Cadena de hashes válida; alteración detectada; `UPDATE` y `DELETE` rechazados en tablas append-only; ancla diaria; retención por clase de dato. | Integración, seguridad |
| M13 Datos simulados | Importación solo con `is_demo = true`; máximo 5.000 filas o 1 MiB por lote; `file_sha256` registrado; filas rechazadas contadas; importación restringida a `PROJECT_TEAM`. | Aplicación, slice web, integración |

## 4. Nombres de pruebas

| Elemento | Convención | Ejemplo |
|---|---|---|
| Clase de prueba unitaria | `<ClaseBajoPrueba>Test` | `ReadingValidationHandlerTest` |
| Clase de integración | `<ClaseBajoPrueba>IT` | `ReadingPersistenceAdapterIT` |
| Clase de slice web | `<Controlador>WebTest` | `ReadingControllerWebTest` |
| Clase de arquitectura | `ArchitectureTest` | `ArchitectureTest` |
| Clase de deriva | `<Fuente>DriftIT` | `FieldLimitsDriftIT` |
| Método | Frase en inglés en camelCase que describe el comportamiento | `rejectsValueAboveRuleMaximum` |
| Método con condición | Verbo, condición y resultado | `returnsStaleStatusWhenReadingIsOlderThanRuleLimit` |

Reglas:
- El nombre dice qué comportamiento se verifica, no qué método se llama.
- Una prueba verifica un comportamiento. Si el nombre necesita "y", son dos pruebas.
- Los nombres de prueba son en inglés. El texto de error que se verifica sí puede estar en español.
- Las pruebas de patrones llevan el nombre que indica `design-patterns.md`.

## 5. Datos de prueba

Reglas:
- Ningún dato de prueba es real. No se copian datos de producción, de la semilla de producción ni de personas reales.
- Nombres de personas: ficticios y obviamente de prueba, por ejemplo "Persona de Prueba Uno". Usuarios: `test.operator`, `test.board`.
- Teléfonos: solo en pruebas de rechazo de datos personales, con números ficticios de prueba (por ejemplo `300 000 0000`).
- Correos: dominio reservado `example.test`.
- Acueducto de prueba: "Vereda de Prueba", slug `prueba-vereda`, `is_demo = true` cuando la prueba necesita datos simulados.
- Contraseñas de prueba: constantes en una clase de fixtures (`TestPasswords`). Nunca se usan fuera de las pruebas.
- Argon2id en pruebas: se usan parámetros bajos para acelerar la ejecución, configurados solo en el perfil de pruebas. Una prueba específica verifica los parámetros de producción (`m=19456 KiB`, `t=2`, `p=1`) sin ejecutar el hash completo más de una vez.
- Reloj: las pruebas usan un `Clock` fijo. Ninguna prueba depende de la hora real.
- Datos del simulador: cuando una prueba necesita una serie de lecturas, usa una fixture determinista con semilla fija, no un archivo exportado del simulador.
- Las fixtures viven en `src/test/resources/fixtures/`. Un archivo de fixture tiene encabezado que dice "Datos de prueba, no reales".

## 6. Pruebas de seguridad

Cada caso tiene una prueba automatizada (integración o slice web). Las cifras son las canónicas de la especificación.

| Área | Caso | Resultado esperado |
|---|---|---|
| Login | 5 fallos en 15 min para una cuenta | Bloqueo de 15 min. El siguiente bloqueo se duplica, hasta 24 h. |
| Login | 20 intentos en 15 min desde una IP | La IP queda limitada. |
| Login | Usuario inexistente y contraseña incorrecta | Mismo mensaje "Usuario o contraseña incorrectos" y tiempo comparable (con hash señuelo). |
| Login | Límite de 5 por minuto por IP | Respuesta `429` al sexto intento. |
| Tokens | JWT con `alg` distinto de HS256, incluido `none` | `401`. |
| Tokens | `iss` o `aud` incorrectos | `401`. |
| Tokens | `exp` vencido, `nbf` en el futuro (tolerancia 30 s) | `401`. |
| Tokens | `token_version` distinto al de la cuenta | `401`. Un cambio de contraseña incrementa la versión. |
| Refresh | Reuso de un refresh token ya rotado | `401` con código `SESSION_REVOKED`, revocación de toda la familia y registro `TOKEN_REUSE_DETECTED`. |
| Refresh | Cookie sin `HttpOnly`, `Secure` o `SameSite` correctos | La prueba de cabeceras de la respuesta falla. |
| Cabeceras | `Authorization` mayor a 2 KiB | Rechazo antes de parsear. |
| Entrada | Cuerpo JSON mayor a 64 KiB (telemetría 256 KiB, importación 1 MiB) | `413 PAYLOAD_TOO_LARGE`. Telemetría (256 KiB) e importación (1 MiB) tienen sus propios límites. |
| Entrada | Campo desconocido | `400 VALIDATION_ERROR`, el mismo código que usa la guía de validación. |
| Entrada | Profundidad JSON mayor a 10 | Rechazo. |
| Entrada | Lista mayor a 100 elementos (salvo el campo que lo permita) | Rechazo. |
| Entrada | Caracteres bidi (U+202A–U+202E, U+2066–U+2069) o de ancho cero (U+200B–U+200F, U+FEFF) | Rechazo. |
| Entrada | Paginación fuera de 1 a 100, cursor alterado | `400 VALIDATION_ERROR` (tamaño) o `400 CURSOR_INVALID` (cursor). |
| Autorización | Un usuario de otro acueducto pide un recurso por ID | `404` (no se revela que existe). |
| RLS | Consulta directa con `app.aqueduct_id` de otro acueducto | Cero filas. |
| RLS | Sesión sin `app.aqueduct_id` | Cero filas en tablas con RLS. |
| Permisos | Rol sin permiso, por ejemplo `OPERATOR` que intenta aprobar una propuesta | `403`. |
| Dispositivos | Firma inválida | `401` y registro `INVALID_SIGNATURE`. |
| Dispositivos | Timestamp fuera de ±300 s | Rechazo. |
| Dispositivos | Nonce repetido | Rechazo y registro `NONCE_REPLAY`. |
| Public | Honeypot lleno o envío en menos del tiempo mínimo | Honeypot: respuesta genérica sin guardar y registro `HONEYPOT_TRIGGERED`. Tiempo mínimo: `400 VALIDATION_ERROR`. |
| Cabeceras | Respuesta de la API | `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Strict-Transport-Security` y `Permissions-Policy` presentes. |
| CORS | Origen no listado | Sin cabecera `Access-Control-Allow-Origin`. Nunca `*` con credenciales. |
| Errores | Excepción no controlada | Respuesta con formato canónico, sin traza ni nombre de clase. |
| Logs | Petición con `Authorization`, `Cookie` o `password` | Esos valores aparecen redactados en el log. |
| Servicio IA | Token de servicio ausente o incorrecto | Rechazo (esto se prueba en el adaptador con servidor simulado). |
| CSV | Celda que empieza con `=`, `+`, `-` o `@` | Se prefija con comilla simple al exportar. |
| SQL | Consultas con concatenación | No existe (revisión de código y prueba con parámetros). |

## 7. Pruebas de deriva

- `FieldLimitsDriftIT`: compara los límites de `FieldLimits` con las longitudes y los `CHECK` reales de PostgreSQL (`information_schema` y `pg_constraint`).
- `MetaConstraintsDriftIT`: compara la respuesta de `GET /api/v1/meta/constraints` con `FieldLimits`.
- `ValidationDriftTest`: verifica que las anotaciones `@Size` y `@Pattern` usan las constantes de `FieldLimits`, no literales.

Si una prueba de deriva falla, el cambio se corrige en `FieldLimits` y se genera una migración. No se edita la BD a mano.

## 8. Arquitectura

`ArchitectureTest` contiene las reglas `ARCH-01` a `ARCH-10` de [Arquitectura](../docs/Arquitectura.md), sección 2.3. Las excepciones se listan en la prueba, con comentario y enlace a la decisión. La prueba corre en cada build.

## 9. Comandos

Todos los comandos se ejecutan desde la raíz del repositorio. Disponible desde la Fase 1 (verificar nombres exactos de perfiles y plugins al crear el `pom.xml`).

| Propósito | Comando |
|---|---|
| Todas las pruebas y verificaciones | `./mvnw verify` |
| Solo pruebas unitarias | `./mvnw test` |
| Una clase de prueba | `./mvnw test -Dtest=ReadingValidationHandlerTest` |
| Pruebas de integración (requiere Docker) | `./mvnw verify -Pintegration` (verificar perfil) |
| Arquitectura | `./mvnw test -Dtest=ArchitectureTest` |
| Reporte de cobertura | `./mvnw verify` y revisar `target/site/jacoco/index.html` |
| Formato | `./mvnw spotless:check` y `./mvnw spotless:apply` |
| Estilo | `./mvnw checkstyle:check` |
| Análisis de bugs y seguridad | `./mvnw spotbugs:check` (con FindSecBugs) |

Requisitos: JDK 25, Docker Engine con Compose (para Testcontainers y la BD local). En CI, el job `ci` ejecuta `./mvnw verify` en `ubuntu-latest`, que incluye Docker.

## 10. Reglas para el PR

- Un cambio de comportamiento sin prueba no se acepta.
- Un bug corregido lleva una prueba que falla antes del arreglo y pasa después.
- Un patrón nuevo lleva su prueba con el nombre de `design-patterns.md`.
- Una regla de negocio nueva se agrega primero como prueba del dominio.
- Los datos de prueba cumplen la sección 5.

Relacionados: [Arquitectura](../docs/Arquitectura.md), [Patrones para agentes](design-patterns.md), [Arquitectura operativa](architecture.md), [Despliegue del backend](deployment.md)
