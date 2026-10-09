# Seguridad: guía operativa para agentes de backend

Esta guía es para quien implementa en `caudal-backend`. Dice qué clase hace qué, cómo se configura, qué reglas no se rompen nunca y qué pruebas son obligatorias. La política y las cifras están en [`docs/Seguridad.md`](../docs/Seguridad.md). Las reglas de entrada están en [`.agents/input-validation.md`](input-validation.md).

Antes de tocar seguridad, leer la política. Si esta guía y la política no coinciden, gana la política y se abre un PR para corregir esta guía.

## 1. Paquetes y clases

Todo lo de seguridad vive en `co.caudal.infrastructure.security`. El dominio (`co.caudal.domain`) no importa nada de este paquete: ArchUnit lo verifica.

| Clase | Qué hace | Control | Patrón |
|---|---|---|---|
| `SecurityFilterChain` (bean de configuración) | Define qué rutas son públicas y qué permisos exigen. Sesión `STATELESS`. Resource server con bearer. Cabeceras | Deny-by-default (sección 2.5), cabeceras (sección 2.8) | Filter chain de Spring Security |
| `Argon2PasswordEncoder` (bean) | Hashea y verifica contraseñas con Argon2id | Contraseñas (sección 2.1) | Adapter sobre `PasswordEncoder` |
| `PasswordHashingService` | Política de contraseñas: longitud NFKC, lista de comunes, nombre de usuario, historial, rehash cuando cambian los parámetros | Contraseñas | Ninguno |
| `JwtEncoder` / `JwtDecoder` (beans) | Firma y valida el JWT HS256 (Nimbus) | Tokens (sección 2.3) | Ninguno |
| `JwtClaimsValidator` | Valida `iss`, `aud`, `exp`, `nbf` con tolerancia de 30 s y compara `tv` con `token_version` | Tokens | Ninguno |
| `RefreshTokenService` | Emite, rota, revoca familias y detecta reutilización. Guarda solo SHA-256 | Refresh (sección 2.3) | Ninguno |
| `RefreshCookieFactory` | Arma la cookie `caudal_rt` con sus atributos | Cookie y CSRF (sección 2.3) | Ninguno |
| `LoginThrottlingService` | Cuenta fallos por cuenta y por IP, bloquea con escalamiento, registra en `login_attempts` | Login y bloqueo (sección 2.2) | Ninguno |
| `DecoyPasswordHash` | Hash señuelo para igualar el tiempo cuando el usuario no existe | Tiempo constante (sección 2.2) | Ninguno |
| `RateLimitFilter` | Aplica los límites de tasa con Bucket4j y `ProxyManager` sobre PostgreSQL | Rate limits (sección 2.10) | Ninguno |
| `RequestSizeLimitFilter` | Rechaza cuerpos y cabeceras fuera de límite antes de parsear | Entradas (sección 2.6) | Ninguno |
| `@SafeText` y `SafeTextValidator` | Anotación y validador de texto: normaliza, rechaza controles, bidi y ancho cero, cuenta puntos de código | Entradas (sección 2.6) | Ninguno |
| `DeviceSignatureVerifier` | Verifica cabeceras, ventana de tiempo, nonce y firma Ed25519 | Dispositivos (sección 2.12) | Ninguno |
| `NonceStore` | Guarda nonces ya vistos durante 10 minutos (`devices.device_nonces`) | Anti-repetición | Ninguno |
| `AuditTrail` | Escribe en `audit.audit_log` a través de funciones de la BD. Nunca SQL directo desde aquí | Auditoría ([BD](../docs/Seguridad-de-la-base-de-datos.md), sección 7) | Decorator sobre comandos: `AuditingCommandHandler` (P09) |
| `SecurityEventPublisher` | Escribe `iam.security_events` con detalles redactados | Eventos de seguridad | Observer (`DomainEventPublisher`, P17) |
| `AqueductSessionContext` | Fija `app.aqueduct_id` (y `app.user_id`) con `set_config(..., true)` al inicio de cada transacción | RLS ([BD](../docs/Seguridad-de-la-base-de-datos.md), sección 5) | Nombre propuesto |
| `PermissionCatalog` | Carga roles y permisos desde `iam.*` y los cachea 60 s (`PERMISSION_CACHE_TTL_SECONDS`) | RBAC desde BD | Nombre propuesto |
| `TrackingCodeGenerator` | Genera el código de 8 caracteres Crockford y devuelve el hash para guardar | Seguimiento de daños | Nombre propuesto |
| `ServiceTokenAuthenticator` | Valida el token de servicio de la IA en tiempo constante | Servicio de IA (sección 2.13) | Ninguno |
| `IaForecastClientAdapter` | Cliente de la IA con timeouts, token y circuit breaker | Servicio de IA | Adapter (P06), Circuit Breaker |

## 2. Configuración

### 2.1 Propiedades

Las propiedades se agrupan en un record por tema. Ningún bean lee `System.getenv` directamente.

```java
@ConfigurationProperties(prefix = "caudal.security")
public record SecurityProperties(
        Jwt jwt,
        Refresh refresh,
        Argon2 argon2,
        Login login,
        Cookie cookie,
        Cors cors,
        ApiDocs apiDocs) {

    public record Jwt(String secret, String issuer, String audience, Duration accessTtl) {
    }

    public record Refresh(int ttlDays, int operatorTtlDays) {
    }

    public record Argon2(int memoryKib, int iterations, int parallelism, int passwordHistorySize) {
    }

    public record Login(int accountMaxFailures, Duration accountWindow, int ipMaxAttempts) {
    }

    public record Cookie(String sameSite) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record ApiDocs(boolean enabled) {
    }
}
```

```yaml
caudal:
  security:
    jwt:
      secret: ${JWT_SECRET}
      issuer: ${JWT_ISSUER}
      audience: ${JWT_AUDIENCE}
      access-ttl-minutes: ${JWT_ACCESS_TTL_MINUTES:15}
    refresh:
      ttl-days: ${REFRESH_TTL_DAYS_DEFAULT:7}
      operator-ttl-days: ${REFRESH_TTL_DAYS_OPERATOR:30}
    argon2:
      memory-kib: ${ARGON2_MEMORY_KIB:19456}
      iterations: ${ARGON2_ITERATIONS:2}
      parallelism: ${ARGON2_PARALLELISM:1}
      password-history-size: ${PASSWORD_HISTORY_SIZE:5}
    login:
      max-failures: ${LOGIN_MAX_FAILURES:5}
      lock-minutes: ${LOGIN_LOCK_MINUTES:15}
      ip-max-attempts: ${LOGIN_IP_MAX_ATTEMPTS:20}
      ip-window-minutes: ${LOGIN_IP_WINDOW_MINUTES:15}
    cookie:
      same-site: ${COOKIE_SAME_SITE:None}
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS}
    api-docs:
      enabled: ${API_DOCS_ENABLED:true}
```

Reglas de la configuración:

- Los secretos (`JWT_SECRET`, `DATABASE_PASSWORD`, `IP_HMAC_KEY`, `MFA_ENCRYPTION_KEY`, `IA_SERVICE_TOKEN`) no tienen valor por defecto en el YAML. Si faltan, la aplicación no arranca.
- Los valores por defecto del YAML son los canónicos (`19456`, `2`, `1`, `5`, `15`, `20`). Un valor fuera de esos defaults requiere una decisión documentada.
- La lista de variables completa, con ejemplos y marcas de secreto, está en [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md), sección 4. Los nombres son propuestos, salvo `API_DOCS_ENABLED`, `IA_SERVICE_TOKEN` y `VITE_API_BASE_URL`.

### 2.2 Ejemplos de código

Argon2id con los parámetros canónicos (la firma del constructor se verifica al implementar):

```java
@Bean
PasswordEncoder passwordEncoder(SecurityProperties properties) {
    var argon2 = properties.argon2();
    // saltLength, hashLength, parallelism, memoryKib, iterations
    return new Argon2PasswordEncoder(16, 32, argon2.parallelism(), argon2.memoryKib(), argon2.iterations());
}
```

JWT HS256 con validación explícita de algoritmo:

```java
@Bean
JwtDecoder jwtDecoder(SecurityProperties properties) {
    SecretKey key = new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()),
            new JwtAudienceValidator(properties.jwt().audience())));
    return decoder;
}
```

Verificar en la implementación: la clave debe tener al menos 32 bytes (256 bits). Una clave más corta debe fallar al arrancar. La tolerancia de 30 segundos se configura en el validador de `exp` y `nbf`, no en el reloj del sistema.

Comparación en tiempo constante (token de servicio y firmas de dispositivo):

```java
boolean matches = MessageDigest.isEqual(
        presented.getBytes(StandardCharsets.UTF_8),
        expected.getBytes(StandardCharsets.UTF_8));
```

Nunca `presented.equals(expected)` para secretos.

Fijar el acueducto de la sesión por transacción:

```java
jdbcTemplate.queryForObject(
        "SELECT set_config('app.aqueduct_id', ?, true)", String.class, aqueductId.toString());
```

`true` equivale a `SET LOCAL`. Ver [BD](../docs/Seguridad-de-la-base-de-datos.md), sección 5.1.

## 3. Reglas que nunca se rompen

1. Ninguna consulta se arma con concatenación de texto. Solo JPA, criteria o parámetros nombrados.
2. Ningún secreto, contraseña, token, código ni clave aparece en logs, respuestas, `details`, URLs ni en la base de datos en claro.
3. Las contraseñas se hashean con Argon2id. No se cifran ni se guardan. La recuperación pasa por un código de un solo uso.
4. El access token se valida con algoritmo explícito (`HS256`). Se rechazan `none` y cualquier otro.
5. El refresh token se guarda solo como SHA-256. Cada uso rota el token. Una reutilización revoca la familia completa.
6. Un cambio de contraseña, de rol o una desactivación incrementa `token_version` y revoca los refresh.
7. Un fallo de login responde el mensaje genérico `INVALID_CREDENTIALS`, con tiempo constante.
8. El acueducto nunca se toma de la petición. Sale del JWT y se fija en la transacción con `set_config(..., true)`.
9. Un recurso de otro acueducto responde `404`, igual que uno inexistente.
10. Todo endpoint no público exige autenticación y permiso. Los permisos salen de `iam.role_permissions`.
11. Las acciones sensibles escriben en `audit.audit_log` dentro de la misma transacción de la acción.
12. Las tablas AO no se modifican desde la aplicación. Si se necesita una corrección, se inserta una fila nueva que la reemplaza (`supersedes_id`, `reading_corrections`).
13. El DTO público nunca incluye `full_name`, teléfonos ni identificadores internos de personas. Pasa por `PublicScheduleProxy`.
14. `dangerouslySetInnerHTML` no existe en el frontend. Las celdas de CSV que empiezan con `=`, `+`, `-` o `@` se escapan.
15. Un valor de negocio no se escribe en código (ver [configuración](../docs/Configuracion-sin-valores-quemados.md)).
16. Una comparación de secretos usa `MessageDigest.isEqual` o la biblioteca de firma. Nunca `equals`.
17. Un error no esperado no muestra la excepción. Se registra con `request_id` y se responde `500` genérico.
18. El servicio de IA recibe solo las series numéricas. No recibe nombres, teléfonos ni identificadores de personas.
19. Un comando de válvula solo se crea desde un turno aprobado, o manual con motivo y por `BOARD_ADMIN`. Todo comando tiene `not_before` y `expires_at`.
20. Un secreto nuevo se agrega a `.env.example` con marcador y a la tabla de configuración. Nunca con valor real.

## 4. Flujos clave

### 4.1 Login

1. Se normaliza el usuario (trim, minúsculas ASCII). Si el formato no cumple, se trata como fallo de credenciales, sin revelar nada.
2. `LoginThrottlingService` revisa si la cuenta o la IP están bloqueadas. Si lo están, responde `INVALID_CREDENTIALS` sin verificar la contraseña y registra `LOCKED` o `RATE_LIMITED`.
3. Si el usuario no existe, se verifica contra `DecoyPasswordHash`.
4. Si la contraseña falla, se incrementa el contador de la cuenta y de la IP, y se registra en `login_attempts`.
5. Si la contraseña es correcta y `must_change_password` es verdadero, se emite un token limitado (por definir) que solo permite cambiar la contraseña.
6. Si `MFA` está activo, se pide el código TOTP antes de emitir sesión.
7. Se emite el access token y el refresh (en cookie), se reinicia el contador y se actualiza `last_login_at`.

Diagrama: [`secuencia-login`](../docs/images/secuencia-login.png).

### 4.2 Refresh

1. Siempre se valida el header `X-Requested-With: caudal-web` y el `Origin` contra `CORS_ALLOWED_ORIGINS`. Si falla, `403 FORBIDDEN`.
2. Se busca el SHA-256 del token en `iam.refresh_tokens`.
3. Si no existe o está vencido, `UNAUTHORIZED` (401) sin más detalle.
4. Si está revocado, se revoca la familia (`REUSE_DETECTED`) y se registra `TOKEN_REUSE_DETECTED`.
5. Si es válido, se marca `ROTATED`, se emite el nuevo en la misma familia y se actualiza `replaced_by_id`.

### 4.3 Telemetría de dispositivo

1. `DeviceSignatureVerifier` valida las cuatro cabeceras y que `X-Timestamp` esté dentro de ±300 s.
2. Se revisa `NonceStore`. Si el nonce ya existe, `NONCE_REPLAY`.
3. Se busca la clave pública activa (`revoked_at IS NULL`) y se verifica la firma sobre `método\nruta\ntimestamp\nnonce\nsha256(cuerpo)`.
4. Si la firma falla, `INVALID_SIGNATURE`. No se procesa nada.
5. Se valida el lote (ver [validación](input-validation.md), sección 4.14), se inserta la lectura y después el punto de telemetría (en ese orden, sin `UPDATE`).

Diagrama: [`secuencia-telemetria`](../docs/images/secuencia-telemetria.png).

### 4.4 Servicio de IA

1. `IaForecastClientAdapter` valida la serie (2.000 puntos como máximo, valores finitos, timestamps crecientes) antes de enviar.
2. Envía `Authorization: Bearer <IA_SERVICE_TOKEN>` con timeouts de 2 s de conexión y 10 s de lectura.
3. Valida la respuesta: `p10 <= p50 <= p90`, valores finitos y fechas válidas.
4. Si hay error, timeout o circuito abierto, usa la estimación simple y registra `fallback_reason`.

## 5. Pruebas obligatorias por control

Cada control tiene al menos una prueba que falla si el control se rompe. Los nombres van en inglés. Las pruebas de integración usan Testcontainers con PostgreSQL 18.

| Control | Pruebas mínimas |
|---|---|
| Argon2id y parámetros | `Argon2ParametersTest`: hash en formato PHC con `m=19456,t=2,p=1`. Rehash cuando cambian los parámetros |
| Política de contraseña | `PasswordPolicyTest`: 11 caracteres falla, 12 pasa, 129 falla. NFKC: la forma de ancho completo equivale a la ASCII. Lista de comunes. Contiene el usuario. Historial de 5 |
| Login genérico | `LoginErrorMessageTest`: usuario inexistente y contraseña incorrecta devuelven el mismo cuerpo y código |
| Tiempo constante | `LoginTimingTest`: la diferencia de tiempo entre usuario inexistente y existente está dentro de la tolerancia (prueba de regresión, no de precisión) |
| Bloqueo por cuenta | `AccountLockoutIT`: 5 fallos bloquean 15 minutos; el segundo bloqueo dura 30 minutos; el tope es 24 horas |
| Bloqueo por IP | `IpLockoutIT`: 20 intentos en 15 minutos bloquean la IP |
| JWT | `JwtValidationTest`: `alg: none` rechazado; `alg` distinto de HS256 rechazado; `iss`, `aud`, `exp` y `nbf` validados; tolerancia de 30 s; clave corta falla al arrancar |
| `tv` | `TokenVersionIT`: cambiar contraseña, rol o desactivar invalida el access token existente |
| Refresh | `RefreshRotationIT`: el token nuevo funciona y el anterior no. La base de datos no contiene el token en claro |
| Reutilización | `RefreshReuseDetectionIT`: reutilizar un token revoca la familia y registra `TOKEN_REUSE_DETECTED` |
| Cookie | `RefreshCookieTest`: `HttpOnly`, `Secure`, `Path=/api/v1/auth`, `SameSite` según configuración |
| CSRF del refresh | `RefreshCsrfTest`: sin `X-Requested-With` o con `Origin` ajeno, `403` |
| Cabecera grande | `AuthorizationHeaderLimitTest`: 2 KiB y 3 KiB. El de 3 KiB se rechaza antes del parser |
| RBAC | `PermissionDeniedIT`: cada endpoint devuelve `403` para roles sin el permiso. Un cambio en `iam.role_permissions` surte efecto sin redeploy |
| Aislamiento | `TenantIsolationIT`: un recurso de otro acueducto devuelve `404`. Sin `app.aqueduct_id` no hay filas |
| Eventos de seguridad | `SecurityEventsIT`: cada evento de la tabla de `iam.security_events` se registra con severidad correcta |
| Límites de tasa | `RateLimitIT`: login 5 por minuto; reportes 3 por hora; API 120 por minuto; telemetría 60 por minuto; pronóstico 10 por hora. El exceso devuelve `429 RATE_LIMITED` |
| Honeypot | `PublicReportHoneypotIT`: campo lleno responde éxito, no guarda y registra `HONEYPOT_TRIGGERED` |
| Tamaño de cuerpo | `RequestSizeLimitIT`: 64 KiB pasa, 64 KiB + 1 falla con `413`. Telemetría con su límite propio |
| Texto malicioso | `SafeTextValidatorTest`: bidi, NUL, ancho cero, emoji en nombre, y cadena de 10.000 caracteres (todos los casos de [validación](input-validation.md), sección 6) |
| Dispositivos | `DeviceSignatureVerifierTest`: firma correcta pasa; firma alterada, cuerpo alterado, timestamp fuera de ±300 s y nonce repetido fallan |
| Comandos de válvula | `ValveCommandPolicyTest`: sin turno aprobado y sin motivo de `BOARD_ADMIN` se rechaza; comando vencido no se entrega |
| Servicio de IA | `IaForecastClientIT` (con WireMock): token en cabecera; timeout activa respaldo; respuesta con cuantiles invertidos se rechaza; circuito abierto no llama a la IA |
| Token de servicio | `ServiceTokenAuthenticatorTest`: token correcto, token anterior durante la rotación, y token de 31 bytes rechazado |
| Auditoría | `AuditTrailIT`: cada acción sensible deja una fila en `audit_log` dentro de la transacción; si la acción falla, no hay fila |
| Cadena de hashes | `AuditHashChainIT`: `verify_chain` pasa; alterar una fila lo detecta |
| Redacción | `LogRedactionTest`: `Authorization`, `Cookie`, `password`, `token` y `secret` aparecen como `[REDACTED]` (o no aparecen) |
| Swagger | `ApiDocsToggleIT`: con `API_DOCS_ENABLED=false`, `/swagger-ui.html` y `/v3/api-docs` no responden |
| Errores | `ErrorResponseContractTest`: el formato es `{"error": {...}}`, sin trazas, con `request_id`. Un error no esperado devuelve `500` genérico |
| Cabeceras | `SecurityHeadersIT`: CSP, `nosniff`, `Referrer-Policy` y `frame-ancestors` presentes en la API |
| CORS | `CorsPolicyIT`: origen de la lista pasa; origen ajeno no recibe cabeceras; `*` con credenciales no existe |
| Arquitectura | `SecurityArchitectureTest` (ArchUnit): `domain` no depende de `infrastructure.security`; ningún controlador usa `System.getenv` |

Cuando una prueba de la tabla no existe para un control nuevo, el PR no se aprueba.

## 6. Checklist rápido para el agente

Antes de declarar terminado un cambio de seguridad:

- [ ] La prueba del control de la sección 5 existe y falla sin el cambio.
- [ ] El cambio no añade un endpoint público sin revisión.
- [ ] La entrada nueva está en [`input-validation.md`](input-validation.md) con su constante.
- [ ] Ningún secreto nuevo tiene valor por defecto ni aparece en logs.
- [ ] La acción sensible escribe en auditoría dentro de la transacción.
- [ ] `gitleaks` y `SpotBugs` pasan sin supresiones nuevas.

Relacionados: [`docs/Seguridad.md`](../docs/Seguridad.md), [`docs/Seguridad-de-la-base-de-datos.md`](../docs/Seguridad-de-la-base-de-datos.md), [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md), [`input-validation.md`](input-validation.md), [`configuration.md`](configuration.md)
