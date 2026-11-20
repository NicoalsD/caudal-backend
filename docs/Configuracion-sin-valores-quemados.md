# Configuración sin valores quemados

Un valor de CAUDAL vive en un solo lugar. En el modelo B2B, cada institución tiene su tanque, sus líneas de servicio, sus válvulas y sus parámetros de operación. Casi todos esos valores son datos de la institución en la base de datos: el administrador de la institución los cambia desde la aplicación, quedan auditados y no piden redespliegue. Si cambia un límite técnico, se cambia una constante y todo lo demás se entera por el mismo camino. Este documento explica cómo se aplica esa regla en `caudal-backend`, en el puente USB y en el frontend.

La regla completa está en la sección 12 de los hechos canónicos. La versión operativa para agentes está en [`.agents/configuration.md`](../.agents/configuration.md). Los límites de entrada, con su tabla completa, están en [`.agents/input-validation.md`](../.agents/input-validation.md).

## 1. Las seis capas

| Capa | Qué contiene | Dónde vive | Quién la cambia | Cómo llega al backend | Cómo llega al frontend |
|---|---|---|---|---|---|
| 1. Parámetros de la institución | Tanque (forma, medidas, calibración), líneas, válvulas, parámetros de operación, usuarios, dispositivos | Tablas `org.*` (`org.tanks`, `org.service_lines`, `org.valves`, `org.institution_settings`) | `INSTITUTION_ADMIN` (`INSTITUTION_CONFIGURE`, `USER_MANAGE`, `DEVICE_MANAGE`). Los datos de alta de la institución los pone `PLATFORM_ADMIN` | BD → caso de uso, que lee la fila vigente en cada operación | `GET /api/v1/tanks`, `GET /api/v1/service-lines`, `GET /api/v1/valves`, `GET /api/v1/institution/settings` |
| 2. Límites técnicos y de seguridad | Longitudes, patrones, tamaños de cuerpo, paginación, ventanas de firma | Clase `FieldLimits` (`co.caudal.shared`) | Equipo de backend, con revisión de seguridad | `@Size`, `@Pattern`, `@Column(length)`, placeholders de Flyway | `GET /api/v1/meta/constraints` → esquemas Zod |
| 3. Entorno | URLs, secretos, orígenes CORS, duraciones de tokens, timeouts de la IA, límites de tasa | Variables de entorno (`application.yml` con `${VAR}`) | Quien despliega (Nicolas Diaz, DevOps) | Spring lee las variables al arrancar | Solo `VITE_API_BASE_URL` (no es secreta) |
| 4. Escenarios del simulador y puerto del puente | Clima, fuente de datos y escenarios de las instituciones simuladas; puerto serie y tiempos del puente | YAML en `caudal-simulador` (escenarios) y YAML de `caudal-sim maqueta-usb` (solo puerto y tiempos) | Equipo de simulación; quien opera la maqueta | No aplica al backend. El puente lee de `GET /api/v1/devices/config` lo que es de la institución | No aplica al frontend |
| 5. Textos | Mensajes de la UI y del error para el usuario | `src/i18n/es.ts` y `messages_es.properties` | Quien escribe la UI | Clave de mensaje en `message` del error | Clave en `es.ts` |
| 6. Constantes físicas | Valores ciertos por definición, como el alcance del HC-SR04 (4000 mm) o el rango angular del MG90S (180°) | Constante con nombre en `co.caudal.shared` (nombres y valores por confirmar) | No cambian | Código | Código |

Cada valor pertenece a una sola capa. Si un valor aparece en dos capas, es un error de diseño que se corrige antes de mezclar el PR.

## 2. El ejemplo obligatorio: la institución sube su reserva de 20 % a 25 %

La institución de prueba `maqueta-caudal` decide subir su reserva del pronóstico de 20 % a 25 %. Es un cambio de parámetro de la institución: capa 1.

### 2.1 Dónde vive el valor

- Columna `org.institution_settings.reserve_level_pct` (fila única de la institución). Es un porcentaje entre 0 y 100, validado por la API.
- El valor 20 no está en el código. Solo aparece en la plantilla de alta, como valor de ejemplo.
- La restricción de la base de datos es `reserve_level_pct BETWEEN 0 AND ${percent_max}`. El `100` de esa restricción es una constante física (`PERCENT_MAX`), no el parámetro de la institución.

### 2.2 Cómo se hace el cambio

1. El `INSTITUTION_ADMIN` abre la pantalla de parámetros de la institución (ruta por confirmar, ver sección 10).
2. El frontend envía `PATCH /api/v1/institution/settings` con `{"reserve_level_pct": 25}`. Requiere el permiso `INSTITUTION_CONFIGURE`. La API valida el rango y los tipos.
3. La transacción actualiza la fila y escribe en `audit.audit_log` la acción `INSTITUTION_SETTINGS_CHANGED`, con `before_state` (`reserve_level_pct = 20`) y `after_state` (`reserve_level_pct = 25`), en la misma transacción.
4. Los cálculos siguientes (eventos de nivel bajo, tiempo hasta la reserva, piso de reserva del piloto) leen la fila nueva. Si el backend cachea los parámetros, la caché se invalida al guardar (por definir, ver sección 10).
5. El frontend vuelve a leer `GET /api/v1/institution/settings` y muestra "Reserva: 25 %". No hay activación ni versión nueva: el cambio es inmediato y queda auditado.

### 2.3 Qué NO se toca

| Elemento | ¿Cambia? | Motivo |
|---|---|---|
| Código Java o TypeScript | No | El valor sale de la BD |
| Constante `PERCENT_MAX` (100) | No | Es física |
| `FieldLimits` | No | Los límites de entrada no dependen del parámetro |
| Migraciones de esquema | No | La columna ya existe |
| Variables de entorno | No | No son parámetros de la institución |
| Build del frontend | No | Lee el valor en tiempo de ejecución |
| Valor anterior (20) | No se borra | Queda en `before_state` de la auditoría. Así se sabe qué reserva estaba vigente en cada momento |

Lo que cambia es la fila de `org.institution_settings` y una entrada de auditoría. No hay versiones de parámetros (ver sección 10, pregunta sobre el historial).

## 3. Parámetros y valores de la institución

Los valores de esta sección son de la institución, salvo que se indique otra cosa. Los números de la columna "Ejemplo" son de la maqueta o ejemplos del documento de reglas ([`Reglas-de-negocio.md`](Reglas-de-negocio.md), sección 13). No son fuente de verdad: la fuente es la fila vigente de la BD.

### 3.1 Tanque, institución y sensor

| Parámetro | Columna | Tabla | Quién lo cambia | Ejemplo | Endpoint de lectura |
|---|---|---|---|---|---|
| Forma (`CYLINDER` o `RECTANGULAR`) | `shape` | `org.tanks` | `INSTITUTION_ADMIN` (`PATCH /api/v1/tanks/{id}`) | `CYLINDER` | `GET /api/v1/tanks` |
| Altura | `height_mm` | `org.tanks` | Igual | 300 mm | Igual |
| Diámetro (cilindro) | `diameter_mm` | `org.tanks` | Igual | Por definir con la maqueta real | Igual |
| Largo y ancho (rectangular) | `length_mm`, `width_mm` | `org.tanks` | Igual | No aplica en la maqueta | Igual |
| Distancia sensor-fondo con tanque vacío | `empty_distance_mm` | `org.tanks` | Igual, o con `POST /api/v1/tanks/{id}/calibration` (`{point: EMPTY}`) | 350 mm | Igual |
| Distancia sensor-superficie con tanque lleno | `full_distance_mm` | `org.tanks` | Igual, o con `POST /api/v1/tanks/{id}/calibration` (`{point: FULL}`) | 80 mm | Igual |
| Zona ciega del sensor | `blind_zone_mm` | `org.tanks` | Igual | 20 mm | Igual |
| Nombre de la institución | `name` | `org.institutions` | `PLATFORM_ADMIN` (`PATCH /api/v1/platform/institutions/{id}`) | Por definir | `GET /api/v1/public/institutions` (solo `name` y `slug`) |
| Identificador de login | `slug` | `org.institutions` | `PLATFORM_ADMIN`. Se fija al crear | `maqueta-caudal` | Igual |
| Institución simulada | `is_simulated` | `org.institutions` | `PLATFORM_ADMIN` al crear. Cambiarla después: por definir | `false` para `maqueta-caudal` | `GET /api/v1/platform/institutions` |

El nivel útil no se guarda como parámetro: sale de la calibración (`empty_distance_mm - full_distance_mm`). Ver [`Reglas-de-negocio.md`](Reglas-de-negocio.md), sección 2.

### 3.2 Parámetros de operación: `org.institution_settings`

Una fila por institución, editable por `INSTITUTION_ADMIN` con `INSTITUTION_CONFIGURE` (`GET/PATCH /api/v1/institution/settings`). Cada cambio queda en la auditoría. No tiene versiones.

| Parámetro | Unidad | Qué controla | Ejemplo |
|---|---|---|---|
| `reserve_level_pct` | % | Nivel de reserva del pronóstico | 20 |
| `low_level_pct` | % | Alerta de nivel bajo | 30 |
| `critical_level_pct` | % | Alerta de nivel crítico | 10 |
| `stale_after_seconds` | s | Sin telemetría durante este tiempo, `STALE_DATA` | 30 |
| `persist_interval_seconds` | s | Cada cuánto se guarda la lectura efectiva en `ops.readings` | 60 |
| `max_backdate_hours` | h | Antigüedad máxima de un punto de telemetría; más viejo se rechaza con `TIMESTAMP_TOO_OLD` | 48 |
| `max_level_change_per_minute` | mm/min | Baja más rápida que esta, `ABNORMAL_DROP` | 2 |
| `sensor_stuck_window_seconds` | s | Valor pegado durante esta ventana, con alguna válvula abierta, `SENSOR_FAULT` | 120 |
| `trend_window_minutes` | min | Ventana para la pendiente (tasa actual) | 10 |
| `forecast_step_seconds` | s | Paso del pronóstico Chronos y de la remuestra | 300 |
| `forecast_horizon_steps` | pasos | Horizonte del pronóstico | 12 (1 h con paso de 300 s) |
| `forecast_context_steps` | pasos | Historia que recibe Chronos | 288 (24 h con paso de 300 s) |
| `reserve_floor_pct` | % | Piso de reserva del piloto: no se recomienda `OPEN` por debajo | 15 |
| `operator_absence_minutes` | min | Prioridad humana: tiempo que el piloto respeta un comando manual | 30 |
| `autopilot_mode` | enumeración | `MANUAL`, `ASSISTED` o `AUTOMATIC` | `MANUAL` |

Reglas de la tabla:

- Ninguna fila se duplica en código. Un valor de negocio que aparezca en Java o TypeScript se trata como error de revisión.
- Los valores de la plantilla de alta son de ejemplo. Cada institución los valida con su tanque antes de usar el pronóstico.
- Los límites de cada parámetro (por ejemplo, porcentajes entre 0 y 100, segundos mayores que cero) salen de `FieldLimits` o de constantes físicas, nunca de la BD.

### 3.3 Líneas de servicio y válvulas

| Parámetro | Columna | Tabla | Quién lo cambia | Ejemplo |
|---|---|---|---|---|
| Nombre de la línea (en la UI, "Acueducto N") | `name` | `org.service_lines` | `INSTITUTION_ADMIN` (`PATCH /api/v1/service-lines/{id}`) | "Acueducto 1" |
| Línea activa | `is_active` | `org.service_lines` | Igual. No hay borrado: se desactiva | `true` |
| Válvula motorizada de la línea | `is_motorized` | `org.valves` | `INSTITUTION_ADMIN` (`PATCH /api/v1/valves/{id}`) | `true` |
| Canal del servo | `servo_channel` | `org.valves` | Igual | 1 y 2 en la maqueta |
| Ángulo abierto | `open_angle_deg` | `org.valves` | Igual | 90 (ejemplo) |
| Ángulo cerrado | `closed_angle_deg` | `org.valves` | Igual | 0 (ejemplo) |
| Mantener torque tras moverse | `hold_torque_after_move` | `org.valves` | Igual. Si es `false`, el puente envía `R<n>` tras `DONE` | `true` |
| Posición de seguridad | `fail_safe_position` | `org.valves` | Igual | Por definir |

El ángulo debe estar en el rango del servo (constante física de 0 a 180 grados, por confirmar). El número de líneas y de válvulas lo decide el administrador: no hay valor fijo en el código.

### 3.4 Lo que llega a los dispositivos: `GET /api/v1/devices/config`

El puente y los actuadores no leen valores de su YAML. Leen de `GET /api/v1/devices/config`, que es una respuesta firmada (ver [`Protocolo-de-dispositivos.md`](Protocolo-de-dispositivos.md)). Esa respuesta sale de las tablas de la sección 3.1 y 3.3:

| Destinatario | Campos que entrega | Fuente |
|---|---|---|
| Sensor | `empty_distance_mm`, `full_distance_mm`, `blind_zone_mm`, `sample_interval_seconds` | `org.tanks`, y el intervalo de muestreo de la institución |
| Actuador | `valve_id`, `servo_channel`, `open_angle_deg`, `closed_angle_deg`, `hold_torque_after_move`, `fail_safe_position` | `org.valves` |
| Todos | `config_version` | Se incrementa con cada cambio de calibración, ángulos o canal |

El puente relee la configuración sin reiniciarse. Por eso un cambio del admin llega a la maqueta en el siguiente ciclo, sin tocar el YAML.

### 3.5 Plantilla de alta de instituciones

La plantilla copia el tanque, las líneas, las válvulas y `org.institution_settings` cuando `PLATFORM_ADMIN` crea una institución (patrón Prototype, `InstitutionTemplate`). Sus valores son de ejemplo.

- Dónde vive la plantilla: por definir. Opciones: una institución marcada como plantilla en la BD, o una clase con los valores. Por la regla de la sección 1, un valor de negocio no debería vivir en código, así que la primera opción es la preferida.

### 3.6 Tipos de observación y catálogos

- Los tipos de observación (`WATER_DIRTY`, `WATER_MUDDY`, `VISIBLE_DAMAGE`, `OTHER`) son identificadores de dominio: deben coincidir con el enum de Java y con la restricción de la BD. No se cambian en caliente.
- Sus etiquetas en español están en `org.catalog_items`, si se mantiene el catálogo (por definir). Antes lo editaba el equipo del proyecto; ahora lo edita `PLATFORM_ADMIN` (por definir).

### 3.7 Retención y textos

| Parámetro | Columna | Tabla | Quién lo cambia | Endpoint |
|---|---|---|---|---|
| Días de retención por tipo de dato | `retention_days` | `audit.retention_policies` | `PLATFORM_ADMIN` (por definir) | Sin endpoint de lectura confirmado (por definir) |

Los textos de la interfaz y de los errores siguen la capa 5 (sección 6).

## 4. Variables de entorno del backend

Los nombres son los de `caudal-backend/.env.example`, que es la fuente de las variables. Los ejemplos son valores seguros o marcadores: nunca un secreto real.

| Variable | Para qué | Ejemplo seguro | ¿Secreto? |
|---|---|---|---|
| `DATABASE_URL` | URL JDBC de la aplicación, con `sslmode=verify-full` | `jdbc:postgresql://<host>:5432/caudal?sslmode=verify-full` | No |
| `DATABASE_USERNAME` | Usuario de la aplicación (`caudal_app`) | `caudal_app` | No |
| `DATABASE_PASSWORD` | Contraseña de `caudal_app` | `cambia-esto-...` (marcador) | Sí |
| `FLYWAY_URL` | URL directa de PostgreSQL para migraciones | `jdbc:postgresql://<host-directo>:5432/caudal?sslmode=verify-full` | No |
| `FLYWAY_USER` | Usuario dueño del esquema (`caudal_migrator`) | `caudal_migrator` | No |
| `FLYWAY_PASSWORD` | Contraseña de `caudal_migrator` | `cambia-esto-...` (marcador) | Sí |
| `JWT_SECRET` | Clave HS256 del JWT de acceso, de al menos 256 bits | Generar con `openssl rand -base64 32` | Sí |
| `JWT_ISSUER` | Claim `iss` | `cambia-esto-issuer-caudal-api` (marcador) | No |
| `JWT_AUDIENCE` | Claim `aud` | `cambia-esto-audience-caudal-web` (marcador) | No |
| `JWT_ACCESS_TTL_MINUTES` | Duración del access token, en minutos | `15` | No |
| `PLATFORM_ACT_AS_TTL_MINUTES` | Duración del token temporal `act_as=platform`, en minutos. No se renueva | `15` | No |
| `REFRESH_TTL_DAYS_DEFAULT` | Duración del refresh token (`INSTITUTION_ADMIN`, `VIEWER`, `PLATFORM_ADMIN`) | `7` | No |
| `REFRESH_TTL_DAYS_OPERATOR` | Duración del refresh token de `OPERATOR` | `30` | No |
| `COOKIE_SAME_SITE` | `SameSite` de la cookie `caudal_rt` | `None` (frontend y API en sitios distintos) | No |
| `COOKIE_SECURE` | `Secure` de la cookie `caudal_rt` | `true` | No |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por comas | `https://<frontend>.vercel.app` (marcador) | No |
| `IP_HMAC_KEY` | Clave del HMAC de las IP | Generar con `openssl rand -base64 32` | Sí |
| `MFA_ENCRYPTION_KEY` | Clave AES-256-GCM de los secretos TOTP | Generar 32 bytes en base64 | Sí |
| `MFA_KEY_ID` | Identificador de la clave de MFA, para rotarla | `cambia-esto-id-llave-mfa-v1` (marcador) | No |
| `ARGON2_MEMORY_KIB` | Memoria de Argon2id | `19456` | No |
| `ARGON2_ITERATIONS` | Iteraciones de Argon2id | `2` | No |
| `ARGON2_PARALLELISM` | Paralelismo de Argon2id | `1` | No |
| `PASSWORD_HISTORY_SIZE` | Contraseñas anteriores que no se pueden reutilizar | `5` | No |
| `RESET_GRANT_TTL_MINUTES` | Minutos que sirve un código de activación o de restablecimiento de contraseña | `1440` | No |
| `LOGIN_MAX_FAILURES` | Fallos seguidos antes de bloquear la cuenta | `5` | No |
| `LOGIN_LOCK_MINUTES` | Ventana de fallos y tiempo de bloqueo inicial, en minutos | `15` | No |
| `LOGIN_IP_MAX_ATTEMPTS` | Intentos por par (IP, institución) en la ventana | `20` | No |
| `LOGIN_IP_WINDOW_MINUTES` | Ventana del límite por (IP, institución), en minutos | `15` | No |
| `RATE_LIMIT_LOGIN_PER_MINUTE` | Login por IP por minuto (Bucket4j) | `5` | No |
| `RATE_LIMIT_PUBLIC_DIRECTORY_PER_MINUTE` | Consultas a `GET /public/institutions` por IP por minuto (nombre propuesto) | Por definir | No |
| `RATE_LIMIT_API_PER_MINUTE` | Peticiones a la API por usuario por minuto | `120` | No |
| `RATE_LIMIT_TELEMETRY_PER_MINUTE` | Telemetría por dispositivo por minuto | `60` | No |
| `RATE_LIMIT_FORECAST_PER_HOUR` | Recálculos de pronóstico por institución por hora | `10` | No |
| `IA_SERVICE_URL` | URL base del servicio de pronóstico, sin barra final | `http://localhost:8000` (local) | No |
| `IA_SERVICE_TOKEN` | Token de servicio (`Authorization: Bearer`) | `cambia-esto-...` (marcador) | Sí |
| `IA_SERVICE_TOKEN_PREVIOUS` | Token anterior durante la rotación | Vacío fuera de una rotación | Sí |
| `IA_CONNECT_TIMEOUT_MS` | Tiempo de conexión a la IA, en milisegundos | `2000` | No |
| `IA_READ_TIMEOUT_MS` | Tiempo de lectura de la IA, en milisegundos | `10000` | No |
| `INSIGHTS_READ_TIMEOUT_MS` | Tiempo de lectura del asistente (preguntas y reportes), en milisegundos | `20000` | No |
| `LLM_API_KEY` | Clave del proveedor del modelo de lenguaje del asistente | `cambia-esto-...` (marcador) | Sí |
| `AUTOPILOT_ENABLED` | Enciende el planificador del piloto automático | `true` | No |
| `AUTOPILOT_INTERVAL_SECONDS` | Segundos entre ciclos del piloto | `10` (demostración) o `60` | No |
| `CIRCUIT_BREAKER_FAILURE_THRESHOLD` | Fallos seguidos para abrir el circuito | `5` | No |
| `CIRCUIT_BREAKER_OPEN_SECONDS` | Segundos abierto antes de la prueba | `60` | No |
| `CIRCUIT_BREAKER_HALF_OPEN_CALLS` | Llamadas de prueba en semiabierto | `2` | No |
| `PERMISSION_CACHE_TTL_SECONDS` | Caché de permisos | `60` | No |
| `DEVICE_SIGNATURE_WINDOW_SECONDS` | Ventana de la firma del dispositivo (±) | `300` | No |
| `DEVICE_NONCE_TTL_SECONDS` | Tiempo en que se recuerda un nonce | `600` | No |
| `DEVICE_KEY_ROTATION_DAYS` | Días que la clave anterior sigue válida | `7` | No |
| `MAX_JSON_BODY_BYTES` | Tamaño máximo del cuerpo JSON normal | `65536` | No |
| `API_DOCS_ENABLED` | Activa Swagger y `/v3/api-docs` | `true` (apagar con `false`) | No |
| `API_PUBLIC_URL` | URL pública de la API que muestra Swagger como servidor | `http://localhost:8080` | No |
| `LOG_LEVEL` | Nivel de log | `INFO` | No |
| `PORT` | Puerto HTTP (lo inyecta Render) | `8080` | No |

Variables del frontend: solo `VITE_API_BASE_URL`. Todo lo que empieza por `VITE_` se publica en el bundle del navegador, así que nunca puede ser secreto.

Las variables del servicio de IA, del simulador y del puente USB se documentan en sus propios repositorios (`caudal-ia`, `caudal-simulador`). El servicio de IA debe recibir el mismo `IA_SERVICE_TOKEN` (en su propio entorno, como token aceptado). El puente USB solo necesita el puerto serie, los tiempos y su clave privada local, y su clave privada no va en el YAML. Su documento de configuración: por definir, porque el repositorio aún no tiene ese archivo.

### 4.1 Dónde se lee cada variable

- `application.yml` solo contiene `${VAR}` y valores por defecto no secretos. Ningún secreto tiene valor por defecto en el código.
- Las propiedades de la aplicación se agrupan en records de configuración (`@ConfigurationProperties`), uno por tema (seguridad, IA, rate limits). Ningún bean lee `System.getenv` directamente.
- Si falta una variable obligatoria, la aplicación no arranca. Un fallo de arranque es mejor que un valor por defecto inseguro.

## 5. Cadena de `FieldLimits`

Una sola constante alimenta cinco destinos:

```text
FieldLimits (Java)
   ├── @Size / @Pattern          → validación de la API (Bean Validation)
   ├── @Column(length = ...)     → mapeo JPA
   ├── placeholders ${...}       → migraciones Flyway (CHECK y varchar)
   ├── GET /api/v1/meta/constraints → frontend
   └── Zod (TypeScript)          → formularios y validación en el navegador
```

### 5.1 La constante

`FieldLimits` vive en `co.caudal.shared`. Cada grupo tiene `_MIN`, `_MAX` y `_PATTERN`. Los patrones se escriben en Java, con barras dobles.

```java
package co.caudal.shared;

public final class FieldLimits {

    public static final int PERSON_NAME_MIN = 2;
    public static final int PERSON_NAME_MAX = 80;
    public static final String PERSON_NAME_PATTERN = "^[\\p{L}][\\p{L}\\p{M} .'-]*$";

    public static final int OBSERVATION_TEXT_MIN = 10;
    public static final int OBSERVATION_TEXT_MAX = 500;

    private FieldLimits() {
    }
}
```

### 5.2 Bean Validation y JPA

```java
public record CreateUserRequest(
        @NotBlank
        @Size(min = FieldLimits.USERNAME_MIN, max = FieldLimits.USERNAME_MAX)
        @Pattern(regexp = FieldLimits.USERNAME_PATTERN)
        String username,

        @NotBlank
        @Size(min = FieldLimits.PERSON_NAME_MIN, max = FieldLimits.PERSON_NAME_MAX)
        @Pattern(regexp = FieldLimits.PERSON_NAME_PATTERN)
        String fullName) {
}
```

```java
@Entity
@Table(name = "users", schema = "iam")
class UserEntity {

    @Column(name = "full_name", length = FieldLimits.PERSON_NAME_MAX, nullable = false)
    private String fullName;
}
```

Notas:

- `@Pattern` usa `matches()`: el patrón debe cubrir toda la cadena. La normalización (NFC, recorte, colapso de espacios) ocurre **antes** de la validación, en `SafeText`. Ver [`.agents/input-validation.md`](../.agents/input-validation.md), sección 5.
- `@Size` usa `length()`, que cuenta unidades UTF-16: un emoji cuenta 2. Por eso `@Size` se conserva solo como cota de seguridad, y la longitud de texto de negocio se valida en `SafeTextValidator` con `codePointCount` (ver [`.agents/input-validation.md`](../.agents/input-validation.md), sección 1).

### 5.3 Placeholders de Flyway

El mapa de placeholders se construye desde `FieldLimits`, así que una migración nunca tiene un número escrito a mano:

```java
@Configuration
class FlywayFieldLimitsConfiguration {

    @Bean
    FlywayConfigurationCustomizer fieldLimitsPlaceholders() {
        return configuration -> configuration.placeholders(Map.of(
                "person_name_max", String.valueOf(FieldLimits.PERSON_NAME_MAX),
                "person_name_min", String.valueOf(FieldLimits.PERSON_NAME_MIN),
                "person_name_pattern", FieldLimits.PERSON_NAME_PATTERN,
                "observation_text_max", String.valueOf(FieldLimits.OBSERVATION_TEXT_MAX)));
    }
}
```

El nombre del tipo `FlywayConfigurationCustomizer` y su paquete cambian entre versiones de Spring Boot: verificar el nombre exacto en Spring Boot 4.1 antes de implementar. Cada migración usa el placeholder, no el número:

```sql
ALTER TABLE iam.users
  ADD CONSTRAINT users_full_name_length
  CHECK (char_length(full_name) BETWEEN ${person_name_min} AND ${person_name_max});
```

La lista completa de placeholders está en [`docs/Seguridad-de-la-base-de-datos.md`](Seguridad-de-la-base-de-datos.md), sección 9.2.

### 5.4 Endpoint de constraints

`GET /api/v1/meta/constraints` devuelve los límites y patrones de `FieldLimits`. Requiere autenticación, porque el directorio y el login no necesitan formularios validados en el navegador y el endpoint no debe ampliar la superficie pública (ver [`Actores-y-permisos.md`](Actores-y-permisos.md), sección 3.2). Solo expone límites de formato, nunca datos de negocio ni secretos.

```java
public record FieldConstraint(String field, int min, int max, String pattern) {
}

public record ConstraintsResponse(List<FieldConstraint> fields) {
}
```

Ejemplo de respuesta (recortado):

```json
{
  "fields": [
    { "field": "personName", "min": 2, "max": 80, "pattern": "^[\\p{L}][\\p{L}\\p{M} .'-]*$" }
  ]
}
```

### 5.5 Zod en el frontend

El frontend no tiene límites escritos a mano. Los esquemas se arman al arrancar la app con la respuesta de `/meta/constraints`:

```ts
import { z } from "zod";
import type { ConstraintsResponse } from "@/services/api-types";
// t: función de traducción del proyecto que lee es.ts (módulo exacto por definir)
import { t } from "@/i18n/t";

export function personNameSchema(constraints: ConstraintsResponse) {
  const limit = constraints.fields.find((f) => f.field === "personName");
  if (!limit) {
    throw new Error("Falta el límite personName en /meta/constraints");
  }
  const pattern = new RegExp(limit.pattern, "u");

  return z
    .string()
    .transform((value) => value.normalize("NFC").trim().replace(/ {2,}/g, " "))
    .refine((value) => [...value].length >= limit.min, {
      message: t("validation.minLength", { min: limit.min }),
    })
    .refine((value) => [...value].length <= limit.max, {
      message: t("validation.maxLength", { max: limit.max }),
    })
    .refine((value) => pattern.test(value), {
      message: t("validation.invalidCharacters"),
    });
}
```

Notas:

- `string.length` en JavaScript cuenta unidades UTF-16, no caracteres. Por eso se usa `[...value].length`.
- El flag `u` es obligatorio para `\p{L}`.
- El frontend no normaliza NFKC: solo la contraseña se normaliza así, y el backend es el que decide.

### 5.6 Qué pasa si alguien cambia un límite

1. Se cambia la constante en `FieldLimits`.
2. Se regenera el placeholder de Flyway (automático).
3. La prueba de deriva compara la BD y el endpoint con `FieldLimits`. Si la BD tiene el valor viejo y no hay migración nueva, falla.
4. Si el cambio reduce un máximo y hay datos más largos, la migración falla. Es correcto: obliga a tratar los datos existentes antes.
5. Zod toma el límite nuevo al arrancar: no hace falta redeploy del frontend para ese valor.

## 6. Textos (i18n)

- Frontend: `src/i18n/es.ts`. Las claves están en inglés, los textos en español.
- Backend: `messages_es.properties`, con las mismas claves para los mensajes de error que ve el usuario.
- La interpolación usa nombres: `"Máximo {max} caracteres"`. Spring `MessageSource` usa índices (`{0}`) por defecto. Para mantener los nombres, se usa un pequeño ayudante que convierte `{max}` en `{0}` o se pasa un mapa (propuesta, verificar).
- El asistente se rotula "Asistente (modelo de lenguaje)" y el pronóstico "Pronóstico (modelo Chronos)". Esos rótulos son textos de la capa 5, no valores de negocio.

```ts
// src/i18n/es.ts (fragmento)
export const es = {
  validation: {
    minLength: "Mínimo {min} caracteres",
    maxLength: "Máximo {max} caracteres",
    invalidCharacters: "Usa solo letras, espacios, apóstrofo, guion o punto",
  },
} as const;
```

```properties
# messages_es.properties (fragmento)
validation.max-length=Máximo {max} caracteres
validation.invalid-characters=Usa solo letras, espacios, apóstrofo, guion o punto
```

Reglas:

- Ningún texto visible se escribe en un componente. Va en `es.ts` o en el archivo de mensajes.
- Un mensaje de error para el usuario no revela si un usuario existe en otra institución ni incluye valores secretos.
- Las claves nuevas se prueban: una prueba comprueba que cada clave usada existe y que sus marcadores coinciden.

## 7. Linters de números mágicos

| Herramienta | Dónde | Regla | Excepciones |
|---|---|---|---|
| Checkstyle `MagicNumber` | Backend (Java) | Un número literal en código es un error | `-1`, `0`, `1` y `2` (por defecto). Las declaraciones `static final` de constantes no se marcan |
| ESLint `no-magic-numbers` | Frontend (TypeScript) | Igual | Propuesta: `ignore: [-1, 0, 1]`, `ignoreArrayIndexes: true`, `enforceConst: true` |
| Ruff `PLR2004` | IA y simulador (Python) | Valor mágico en una comparación | Verificar la configuración del repositorio de IA |

Reglas del equipo:

- No se usa `@SuppressWarnings("checkstyle:MagicNumber")` ni `// eslint-disable no-magic-numbers` sin un comentario que explique el motivo y sin revisión de otro integrante.
- Si un número es una constante física (por ejemplo, el alcance del HC-SR04 de 4000 mm o el rango de 180° del servo), se declara con nombre en `co.caudal.shared`. Si es un límite técnico, va en `FieldLimits`. Si es un valor de la institución, va en la BD.
- Un número en una prueba es aceptable si expresa el caso (por ejemplo, `"a".repeat(10_000)` para el caso de la cadena larga).

## 8. Pruebas de deriva

Las pruebas de deriva comparan dos fuentes que deben coincidir. Si divergen, la prueba falla.

| Prueba | Compara | Dónde |
|---|---|---|
| `FieldLimitsEndpointDriftIT` | `FieldLimits` contra `GET /api/v1/meta/constraints` | Backend, Testcontainers |
| `FieldLimitsDatabaseDriftIT` | `FieldLimits` contra `varchar(n)` y los `CHECK` de la BD | Backend, Testcontainers |
| `NoBusinessLiteralsArchTest` | Literales numéricos y de texto de negocio en `co.caudal.domain` y `co.caudal.application`, fuera de constantes | Backend, ArchUnit |
| `EnumDriftIT` | Enums de Java (tipos de observación, roles, estados de comando) contra los `IN (...)` de la BD | Backend |
| `SeedVersusTemplateIT` | La semilla de `maqueta-caudal` pasa las mismas validaciones que una institución creada desde la plantilla | Backend |
| `constraints.parity.test.ts` | Casos válidos e inválidos en Zod contra el snapshot de `/meta/constraints` | Frontend, Vitest |
| `i18n.keys.test.ts` | Cada clave usada en la UI existe en `es.ts` y sus marcadores coinciden | Frontend, Vitest |
| `NoBusinessValuesTest` (frontend) | No hay umbrales de reserva, porcentajes de alerta ni distancias del tanque escritos en `src/` | Frontend, Vitest |

## 9. Lista de verificación para revisar un PR

Marcar cada punto antes de aprobar.

- [ ] Ningún valor de la institución (reserva, alertas, umbrales de sensor, ángulos, canales, medidas del tanque, intervalos del piloto) aparece en código o en `application.yml`.
- [ ] Los límites de entrada usan una constante de `FieldLimits` (no literales en DTO, entidad ni migración).
- [ ] Un campo nuevo tiene su fila en [`.agents/input-validation.md`](../.agents/input-validation.md), su constante y su prueba de deriva.
- [ ] Una migración usa placeholders de Flyway para longitudes y patrones. Los números escritos a mano en migraciones se rechazan.
- [ ] Cada variable de entorno nueva aparece en `.env.example` (con marcador) y en la tabla de la sección 4.
- [ ] Ningún secreto tiene valor por defecto en el código ni en `application.yml`.
- [ ] Ningún valor `VITE_*` es secreto.
- [ ] Los textos visibles están en `es.ts` o en `messages_es.properties`.
- [ ] Los linters pasan sin nuevas supresiones. Cada supresión nueva tiene motivo escrito.
- [ ] Un cambio de parámetro de una institución se hace con `PATCH` auditado en la BD, no con un cambio de código.
- [ ] Las pruebas de deriva de la sección 8 siguen en verde.

## 10. Preguntas abiertas

- Si la caché de los parámetros de la institución existe y cómo se invalida al guardar un cambio.
- Si el historial de `org.institution_settings` basta con el `before_state` de la auditoría, o si hace falta conservar versiones para explicar en las actas qué parámetros aplicaban un día pasado.
- Dónde vive la plantilla de alta: en la BD o en código (sección 3.5).
- Ruta de la pantalla de parámetros de la institución (`/admin/piloto` u otra).
- Valores y nombres de las constantes físicas (`PERCENT_MAX`, rango del servo, alcance del sensor) y de los límites de cada parámetro, en `FieldLimits`.
- Si una institución puede tener más de un tanque. El modelo supone uno.

Relacionados: [`Seguridad.md`](Seguridad.md), [`Seguridad-de-la-base-de-datos.md`](Seguridad-de-la-base-de-datos.md), [`Reglas-de-negocio.md`](Reglas-de-negocio.md), [`Protocolo-de-dispositivos.md`](Protocolo-de-dispositivos.md), [`../.agents/configuration.md`](../.agents/configuration.md), [`../.agents/input-validation.md`](../.agents/input-validation.md)
