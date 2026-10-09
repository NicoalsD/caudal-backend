# Configuración sin valores quemados

Un valor de CAUDAL vive en un solo lugar. Si la Junta cambia una regla, el cambio no pide tocar código, migraciones de esquema ni variables de entorno. Si cambia un límite técnico, se cambia una constante y todo lo demás se entera por el mismo camino. Este documento explica cómo se aplica esa regla en `caudal-backend` y en el frontend.

La regla completa está en la sección 12 de los hechos canónicos. La versión operativa para agentes está en [`.agents/configuration.md`](../.agents/configuration.md). Los límites de entrada, con su tabla completa, están en [`.agents/input-validation.md`](../.agents/input-validation.md).

## 1. Las seis capas

| Capa | Qué contiene | Dónde vive | Quién la cambia | Cómo llega al backend | Cómo llega al frontend |
|---|---|---|---|---|---|
| 1. Parámetros de negocio | Reglas de la Junta, horas, reserva, prioridades, catálogos, tanques | Tablas de `org` (versiones de reglas) y `org.catalog_items` | La Junta (BOARD_ADMIN activa; BOARD_MEMBER crea borradores) | BD → caso de uso (lee la versión vigente) | `GET /api/v1/rule-sets/current`, `GET /api/v1/catalogs/{catalog}` |
| 2. Límites técnicos y de seguridad | Longitudes, patrones, tamaños de cuerpo, paginación, ventanas de firma | Clase `FieldLimits` (`co.caudal.shared`) | Equipo de backend, con revisión de seguridad | `@Size`, `@Pattern`, `@Column(length)`, placeholders de Flyway | `GET /api/v1/meta/constraints` → esquemas Zod |
| 3. Entorno | URLs, secretos, orígenes CORS, duraciones de tokens, timeouts de la IA | Variables de entorno (`application.yml` con `${VAR}`) | Quien despliega (Nicolas Diaz, DevOps) | Spring lee las variables al arrancar | Solo `VITE_API_BASE_URL` (no es secreta) |
| 4. Escenarios del simulador | Parámetros de clima, fuente, turnos | YAML en `caudal-simulador` | Equipo de simulación | No aplica al backend | No aplica al frontend |
| 5. Textos | Mensajes de la UI y del error para el usuario | `src/i18n/es.ts` y `messages_es.properties` | Quien escribe la UI | Clave de mensaje en `message` del error | Clave en `es.ts` |
| 6. Constantes físicas | Valores que son ciertos por definición (`HOURS_PER_DAY = 24`) | Constante con nombre en código | No cambian | Código | Código |

Cada valor pertenece a una sola capa. Si un valor aparece en dos capas, es un error de diseño que se corrige antes de mezclar el PR.

## 2. El ejemplo obligatorio: "Máximo 24" pasa a 23

La Junta decide que el máximo de horas de servicio por día baja de 24 a 23.

### 2.1 Dónde vive el valor

- Columna `org.rule_sets.max_daily_service_hours` (`numeric(4,2)`, `NOT NULL`). Es parámetro de negocio: capa 1.
- El valor 24 no está en el código. Solo aparece en la semilla (`V...__seed_rules.sql`), como valor de ejemplo.
- La restricción de la base de datos es `max_daily_service_hours > 0 AND max_daily_service_hours <= HOURS_PER_DAY`. El `24` de esa restricción es la constante física `HOURS_PER_DAY`, no el parámetro. Nadie puede poner más de 24 horas en un día, así que ese tope no depende de la Junta.

### 2.2 Cómo se hace el cambio

1. `BOARD_ADMIN` o `BOARD_MEMBER` crean un borrador: `POST /api/v1/rule-sets` (con `change_reason` opcional). El borrador se copia de la versión vigente (patrón Prototype, `RuleSet.copyForNewVersion()`).
2. Se edita el campo: `PATCH /api/v1/rule-sets/{id}` con `"max_daily_service_hours": 23`. La validación de la API acepta valores mayores que 0 y menores o iguales que `HOURS_PER_DAY` (24). El 23 pasa.
3. `POST /api/v1/rule-sets/{id}/activate` (solo `BOARD_ADMIN`) con `change_reason` de 10 a 500 caracteres, obligatorio. La versión anterior pasa a `SUPERSEDED` y la nueva a `ACTIVE`, en una sola transacción. Se escribe `RULESET_ACTIVATED` en `audit.audit_log`.
4. La siguiente propuesta de turnos usa la versión activa. Si el backend cachea la versión vigente, la caché se invalida al activar (por definir: caché o lectura directa).
5. El frontend lee `GET /api/v1/rule-sets/current` y muestra "Máximo 23 horas" en la pantalla de reglas y en la propuesta. No hay recompilación ni redespliegue.

### 2.3 Qué NO se toca

| Elemento | ¿Cambia? | Motivo |
|---|---|---|
| Código Java o TypeScript | No | El valor sale de la BD |
| `HOURS_PER_DAY` (24) | No | Es físico |
| `FieldLimits` | No | Los límites de entrada no dependen del parámetro |
| Migraciones de esquema | No | La columna ya existe. Solo se inserta una versión nueva |
| Variables de entorno | No | No son parámetros de negocio |
| Build del frontend | No | Lee el valor en tiempo de ejecución |
| Versión anterior de las reglas (24) | No se borra | Se queda en `SUPERSEDED`, inmutable. Así se sabe qué regla estaba vigente cada día |

Lo único que cambia es una fila nueva en `org.rule_sets` y sus hijas, más un registro de auditoría.

## 3. Parámetros de negocio

Todos viven en `org`. Se leen con `GET /api/v1/rule-sets/current` (versión vigente con sus bandas, sectores, orden de válvulas y horarios) salvo que se indique otra cosa.

| Parámetro (hechos, sección 12.1) | Columna en BD | Tabla | Quién lo cambia | Endpoint de lectura |
|---|---|---|---|---|
| Rango de la regla pintada (0 a 5 en la semilla) | `gauge_min`, `gauge_max`, `gauge_step` | `org.tanks` | Por definir. Propuesta: BOARD_ADMIN al crear el tanque; no se cambia con lecturas | `GET /api/v1/tanks` |
| Bandas de nivel (`HIGH`, `LOW`, `CRITICAL`) | `band`, `min_level`, `max_level` | `org.rule_level_bands` | BOARD_ADMIN (activa); BOARD_MEMBER (borrador) | `GET /api/v1/rule-sets/current` |
| Horas de servicio por banda | `daily_service_hours` | `org.rule_level_bands` | Igual | Igual |
| Solo sectores prioritarios (por banda) | `priority_only` | `org.rule_level_bands` | Igual | Igual |
| Reserva mínima | `reserve_level` | `org.rule_sets` | Igual | Igual |
| Política de reserva (`NONE`, `REDUCE_TO_CRITICAL_BAND`, `PRIORITY_ONLY`) | `reserve_policy` | `org.rule_sets` | Igual | Igual |
| Horario en que se puede operar | `day_of_week`, `start_time`, `end_time` | `org.rule_operating_windows` | Igual | Igual |
| Sectores que reciben turnos | `is_included` | `org.rule_sector_settings` | Igual | Igual |
| Sectores prioritarios y su orden | `is_priority`, `priority_rank` | `org.rule_sector_settings` | Igual | Igual |
| Orden de apertura de válvulas | `sequence_order` | `org.rule_valve_orders` | Igual | Igual |
| Máximo de horas de servicio por día | `max_daily_service_hours` | `org.rule_sets` | Igual | Igual |
| Duración mínima y máxima de un turno | `min_shift_hours`, `max_shift_hours` | `org.rule_sets` | Igual | Igual |
| Ventana para detectar lecturas repetidas | `duplicate_window_minutes` | `org.rule_sets` | Igual | Igual |
| Salto brusco máximo por hora | `max_level_change_per_hour` | `org.rule_sets` | Igual | Igual |
| Antigüedad máxima de un dato antes de "viejo" | `stale_reading_hours` | `org.rule_sets` | Igual | Igual |
| Días máximos hacia atrás para una lectura | `max_backdate_days` | `org.rule_sets` | Igual | Igual |
| Horizonte de pronóstico (1 a 3 días) | `forecast_horizon_days` | `org.rule_sets` | Igual | Igual |
| Días de historia enviados a la IA | `forecast_context_days` | `org.rule_sets` | Igual | Igual |
| Estrategia de asignación | `allocation_strategy` | `org.rule_sets` | Igual | Igual |
| Umbral para decir "viene bajando" | `trend_threshold_per_day` | `org.rule_sets` | Igual | Igual |
| Aspecto del agua y categorías de daño | `code`, `label_es`, `sort_order`, `is_active` | `org.catalog_items` | `PROJECT_TEAM`, con `POST` y `PATCH /api/v1/catalogs/{catalog}/items` | `GET /api/v1/catalogs/{catalog}` |
| Días de retención por tipo de dato | `retention_days` | `audit.retention_policies` | `PROJECT_TEAM`, con `PUT /api/v1/retention-policies/{data_class}` | Sin endpoint de lectura (por definir) |
| Nombre, municipio, zona horaria, página pública | `name`, `municipality`, `timezone`, `public_page_enabled` | `org.aqueducts` | Por definir | Por definir |

Reglas de la tabla:

- Ninguna fila de esta tabla se duplica en código. Un valor de negocio que aparezca en Java o TypeScript se trata como error de revisión.
- Las semillas (`V...__seed_*.sql`) pueden tener valores de ejemplo. Son datos de arranque, no fuente de verdad: la fuente es la fila vigente de la BD.
- Cada versión de reglas es inmutable cuando se activa. Un cambio siempre es una versión nueva.

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
| `REFRESH_TTL_DAYS_DEFAULT` | Duración del refresh token (7 días) | `7` | No |
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
| `LOGIN_MAX_FAILURES` | Fallos seguidos antes de bloquear la cuenta | `5` | No |
| `LOGIN_LOCK_MINUTES` | Ventana de fallos y tiempo de bloqueo inicial, en minutos | `15` | No |
| `LOGIN_IP_MAX_ATTEMPTS` | Intentos por IP en la ventana | `20` | No |
| `LOGIN_IP_WINDOW_MINUTES` | Ventana del límite por IP, en minutos | `15` | No |
| `RATE_LIMIT_LOGIN_PER_MINUTE` | Login por IP por minuto (Bucket4j) | `5` | No |
| `RATE_LIMIT_PUBLIC_REPORTS_PER_HOUR` | Reportes públicos por IP por hora | `3` | No |
| `RATE_LIMIT_API_PER_MINUTE` | Peticiones a la API por usuario por minuto | `120` | No |
| `RATE_LIMIT_TELEMETRY_PER_MINUTE` | Telemetría por dispositivo por minuto | `60` | No |
| `RATE_LIMIT_FORECAST_PER_HOUR` | Recálculos de pronóstico por acueducto por hora | `10` | No |
| `IA_SERVICE_URL` | URL base del servicio de pronóstico, sin barra final | `http://localhost:8000` (local) | No |
| `IA_SERVICE_TOKEN` | Token de servicio (`Authorization: Bearer`) | `cambia-esto-...` (marcador) | Sí |
| `IA_SERVICE_TOKEN_PREVIOUS` | Token anterior durante la rotación | Vacío fuera de una rotación | Sí |
| `IA_CONNECT_TIMEOUT_MS` | Tiempo de conexión a la IA, en milisegundos | `2000` | No |
| `IA_READ_TIMEOUT_MS` | Tiempo de lectura de la IA, en milisegundos | `10000` | No |
| `CIRCUIT_BREAKER_FAILURE_THRESHOLD` | Fallos seguidos para abrir el circuito | `5` | No |
| `CIRCUIT_BREAKER_OPEN_SECONDS` | Segundos abierto antes de la prueba | `60` | No |
| `CIRCUIT_BREAKER_HALF_OPEN_CALLS` | Llamadas de prueba en semiabierto | `2` | No |
| `PUBLIC_FORM_MIN_SECONDS` | Tiempo mínimo de llenado del formulario público | `3` | No |
| `PERMISSION_CACHE_TTL_SECONDS` | Caché de permisos | `60` | No |
| `DEVICE_SIGNATURE_WINDOW_SECONDS` | Ventana de la firma del dispositivo (±) | `300` | No |
| `DEVICE_NONCE_TTL_SECONDS` | Tiempo en que se recuerda un nonce | `600` | No |
| `DEVICE_KEY_ROTATION_DAYS` | Días que la clave anterior sigue válida | `7` | No |
| `MAX_JSON_BODY_BYTES` | Tamaño máximo del cuerpo JSON normal | `65536` | No |
| `API_DOCS_ENABLED` | Activa Swagger y `/v3/api-docs` | `true` (apagar con `false`) | No |
| `LOG_LEVEL` | Nivel de log | `INFO` | No |
| `SEED_DEMO_DATA` | Carga el acueducto demo con datos simulados | `true` | No |
| `PORT` | Puerto HTTP (lo inyecta Render) | `8080` | No |


Variables del frontend: solo `VITE_API_BASE_URL`. Todo lo que empieza por `VITE_` se publica en el bundle del navegador, así que nunca puede ser secreto.

Las variables del servicio de IA y del simulador se documentan en sus propios repositorios (`caudal-ia` y `caudal-simulador`). El servicio de IA debe recibir el mismo `IA_SERVICE_TOKEN` (en su propio entorno, como token aceptado). Su documento de configuración: por definir, porque el repositorio aún no tiene ese archivo.

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

    public static final int NOTE_MAX = 500;
    public static final int NOTE_MAX_LINES = 10;

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
                "note_max", String.valueOf(FieldLimits.NOTE_MAX)));
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

`GET /api/v1/meta/constraints` devuelve los límites y patrones de `FieldLimits`. Es público (sin login), porque el formulario público lo necesita. Solo expone límites de formato, nunca datos de negocio ni secretos.

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
- Un mensaje de error para el usuario no revela si un usuario existe ni incluye valores secretos.
- Las claves nuevas se prueban: una prueba comprueba que cada clave usada existe y que sus marcadores coinciden.

## 7. Linters de números mágicos

| Herramienta | Dónde | Regla | Excepciones |
|---|---|---|---|
| Checkstyle `MagicNumber` | Backend (Java) | Un número literal en código es un error | `-1`, `0`, `1` y `2` (por defecto). Las declaraciones `static final` de constantes no se marcan |
| ESLint `no-magic-numbers` | Frontend (TypeScript) | Igual | Propuesta: `ignore: [-1, 0, 1]`, `ignoreArrayIndexes: true`, `enforceConst: true` |
| Ruff `PLR2004` | IA y simulador (Python) | Valor mágico en una comparación | Verificar la configuración del repositorio de IA |

Reglas del equipo:

- No se usa `@SuppressWarnings("checkstyle:MagicNumber")` ni `// eslint-disable no-magic-numbers` sin un comentario que explique el motivo y sin revisión de otro integrante.
- Si un número es una constante física (por ejemplo, `HOURS_PER_DAY = 24`), se declara con nombre en `co.caudal.shared`. Si es un límite técnico, va en `FieldLimits`. Si es de negocio, va en la BD.
- Un número en una prueba es aceptable si expresa el caso (por ejemplo, `"a".repeat(10_000)` para el caso de la cadena larga).

## 8. Pruebas de deriva

Las pruebas de deriva comparan dos fuentes que deben coincidir. Si divergen, la prueba falla.

| Prueba | Compara | Dónde |
|---|---|---|
| `FieldLimitsEndpointDriftIT` | `FieldLimits` contra `GET /api/v1/meta/constraints` | Backend, Testcontainers |
| `FieldLimitsDatabaseDriftIT` | `FieldLimits` contra `varchar(n)` y los `CHECK` de la BD | Backend, Testcontainers |
| `NoBusinessLiteralsArchTest` | Literales numéricos y de texto de reglas en `co.caudal.domain` y `co.caudal.application`, fuera de constantes | Backend, ArchUnit |
| `EnumDriftIT` | Enums de Java contra los `IN (...)` de la BD | Backend |
| `SeedVersusRulesIT` | La semilla de reglas pasa las mismas validaciones que una versión creada por la API | Backend |
| `constraints.parity.test.ts` | Casos válidos e inválidos en Zod contra el snapshot de `/meta/constraints` | Frontend, Vitest |
| `i18n.keys.test.ts` | Cada clave usada en la UI existe en `es.ts` y sus marcadores coinciden | Frontend, Vitest |
| `RuleSetNoHardcodedValuesTest` (frontend) | No hay horas, máximos o bandas escritos en `src/` | Frontend, Vitest |

## 9. Lista de verificación para revisar un PR

Marcar cada punto antes de aprobar.

- [ ] Ningún valor de negocio (horas, bandas, reserva, prioridades, máximos de turnos) aparece en código o en `application.yml`.
- [ ] Los límites de entrada usan una constante de `FieldLimits` (no literales en DTO, entidad ni migración).
- [ ] Un campo nuevo tiene su fila en [`.agents/input-validation.md`](../.agents/input-validation.md), su constante y su prueba de deriva.
- [ ] Una migración usa placeholders de Flyway para longitudes y patrones. Los números escritos a mano en migraciones se rechazan.
- [ ] Cada variable de entorno nueva aparece en `.env.example` (con marcador) y en la tabla de la sección 4.
- [ ] Ningún secreto tiene valor por defecto en el código ni en `application.yml`.
- [ ] Ningún valor `VITE_*` es secreto.
- [ ] Los textos visibles están en `es.ts` o en `messages_es.properties`.
- [ ] Los linters pasan sin nuevas supresiones. Cada supresión nueva tiene motivo escrito.
- [ ] Un cambio de regla de la Junta se hace con una versión nueva en BD, no con un cambio de código.
- [ ] Las pruebas de deriva de la sección 8 siguen en verde.

## 10. Preguntas abiertas

- Si la caché de la regla vigente existe y cómo se invalida al activar una versión.

Relacionados: [`Seguridad.md`](Seguridad.md), [`Seguridad-de-la-base-de-datos.md`](Seguridad-de-la-base-de-datos.md), [`../.agents/configuration.md`](../.agents/configuration.md), [`../.agents/input-validation.md`](../.agents/input-validation.md)
