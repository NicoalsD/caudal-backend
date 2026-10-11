# Despliegue del backend

> Cómo se construye, se configura y se ejecuta `caudal-backend` en local y en la nube. Las cifras y decisiones canónicas vienen de la especificación del proyecto. Lo que no está confirmado se marca "verificar".

## 1. Visión general

| Pieza | Plataforma | Imagen o tipo | Notas |
|---|---|---|---|
| API | Render | Servicio web con Docker, construido desde el `Dockerfile` del repositorio | Health check en `/actuator/health`. |
| Base de datos | Neon (PostgreSQL 18) | Proyecto con ramas `main` (producción) y `dev` (desarrollo) | TLS `verify-full`. PITR de Neon. |
| Migraciones | Flyway desde la API o un job | Conexión directa a Neon, usuario `caudal_migrator` | Ver sección 5. |
| Local | Docker Compose | PostgreSQL 18 local, API con Maven o contenedor | Ver sección 6. |

Restricciones de la especificación:
- La API y la base de datos se despliegan en plataformas gratuitas o de bajo costo. El diseño debe funcionar con los límites de memoria de esos planes.
- Swagger está activo en todos los entornos. Solo documenta; ejecutar requiere JWT. `API_DOCS_ENABLED=false` lo apaga.

## 2. Imagen Docker

La construcción multi-etapa de `Dockerfile` compila con JDK 25 y ejecuta con JRE 25. La imagen final no contiene Maven ni código fuente. `.dockerignore` excluye secretos locales, artefactos y metadatos de Git del contexto de build.

```dockerfile
# Stage 1: build the executable jar (tests run in CI, not here)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# Stage 2: runtime
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 caudal
COPY --from=build /workspace/target/*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Reglas:
- La imagen base y sus etiquetas se verifican al crear el Dockerfile (verificar que `eclipse-temurin:25` exista para la arquitectura de Render).
- El contenedor corre con un usuario sin privilegios (uid 10001).
- No se copian `.env`, claves ni `target/` previo. El `.dockerignore` los excluye.
- Los tests no se ejecutan en la construcción de la imagen. Se ejecutan en CI antes de llegar aquí.

### 2.1 Memoria: objetivo de 512 MB

La especificación fija el objetivo de la JVM en 512 MB (verificar con medición). Los valores de `JAVA_TOOL_OPTIONS` son una propuesta de punto de partida:

| Ajuste | Propósito | Estado |
|---|---|---|
| `-XX:MaxRAMPercentage=60` | Limita el heap a una fracción de la memoria del contenedor. | Propuesto. Medir con la carga de la demo. |
| `-XX:+UseSerialGC` | Recolector de bajo consumo para un solo núcleo. | Propuesto. |
| `-XX:+ExitOnOutOfMemoryError` | Reinicia el proceso ante un `OutOfMemoryError` en vez de quedarse colgado. | Propuesto. |
| `-XX:ReservedCodeCacheSize=64m` | Reduce la memoria reservada para código JIT. | Opcional. Medir antes de usarlo. |

Criterio de aceptación: la API responde a la carga de la demo sin reinicios por memoria y con el RSS del proceso por debajo de 512 MB. Si no se cumple, la decisión se documenta aquí (verificar).

## 3. Render

### 3.1 Servicio web

| Campo | Valor |
|---|---|
| Tipo | Web Service |
| Entorno | Docker |
| Rama de despliegue | `main` para producción. `develop` para un servicio de pruebas, si existe (verificar). |
| Ruta de salud | `/actuator/health` |
| Puerto | Render define `PORT`; Spring usa `server.port=${PORT:8080}`. |
| Región | Ohio, cercana a Neon us-east-2. |
| Plan | `free` para pruebas; suspende al quedar inactivo. Medir memoria antes de producción. |

La configuración implementada está en `render.yaml`: servicio `caudal-api`, rama `develop`, región Ohio, plan gratuito, despliegue manual y health check `/actuator/health`. Las credenciales externas se declaran con `sync: false`; los secretos de firma y cifrado se generan en Render.

Comportamiento de suspensión: un plan que suspende el servicio tras inactividad aumenta la latencia de la primera petición. La PWA debe mostrar un estado de "conectando" en vez de fallar (verificar con el equipo de frontend).

### 3.2 Variables de entorno en Render

Las variables se cargan desde `render.yaml` al crear el Blueprint. Ningún valor real se escribe en el repositorio: Neon y CORS se piden en el panel; los secretos de firma/cifrado se generan en Render. La lista local está en `.env.example`.

Grupos:

| Grupo | Variables | Origen del valor |
|---|---|---|
| Base de datos | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Neon, rama `main`, usuario `caudal_app`. |
| Migraciones | `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` | Neon, conexión directa, usuario `caudal_migrator`. |
| Seguridad | `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`, `IP_HMAC_KEY`, `MFA_ENCRYPTION_KEY`, `MFA_KEY_ID` | Generados con `openssl rand -base64 32`. |
| Servicio IA | `IA_SERVICE_URL`, `IA_SERVICE_TOKEN` | Hugging Face Spaces o Render (IA). El token se genera aparte. |
| Web | `CORS_ALLOWED_ORIGINS`, `COOKIE_SAME_SITE`, `COOKIE_SECURE` | URL de la PWA en Vercel. |
| Operación | `LOG_LEVEL`, `API_DOCS_ENABLED`, `SEED_DEMO_DATA` | Valores por entorno. |

Reglas de secretos:
- Un secreto no aparece en logs, en respuestas ni en la interfaz de Render más allá de su campo oculto.
- Rotación: cada secreto se rota con un procedimiento documentado. El procedimiento está pendiente (verificar). La rotación del `JWT_SECRET` cierra todas las sesiones.
- `IA_SERVICE_TOKEN` se rota aceptando el token actual y el anterior durante el cambio. Así el backend y la IA no quedan desalineados.

### 3.3 Swagger y cabeceras

Swagger UI necesita sus propios scripts y estilos. La API usa `default-src 'none'; frame-ancestors 'none'` en general, y en `/swagger-ui/**` la política de la sección 21 de los hechos. `/v3/api-docs` y `/swagger-ui.html` son públicos (solo documentación).

| Ruta | Política | Decidida |
|---|---|---|
| `/api/v1/**` (JSON) | `default-src 'none'; frame-ancestors 'none'` | Sí. |
| `/swagger-ui.html` y recursos de `/swagger-ui/**` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'` | Sí. |
| `/v3/api-docs` | JSON. Misma política que la API. | Sí. |

La decisión está en los hechos canónicos (sección 21). Ver [Documentación de API](api-documentation.md).

## 4. Neon

### 4.1 Ramas

| Rama | Uso | Quién la usa |
|---|---|---|
| `main` | Producción. La API de Render en producción se conecta aquí. | Render (producción). |
| `dev` | Desarrollo y pruebas manuales. Se crea desde `main` cuando hace falta (copia barata de Neon). | Equipo, en local y en un servicio de pruebas (verificar). |

Reglas:
- Las pruebas automatizadas no usan Neon. Usan Testcontainers con PostgreSQL 18 local.
- Una rama nueva se crea desde `main` y se elimina cuando termina su uso.
- El acceso a Neon es por usuarios con privilegios mínimos (`caudal_app`, `caudal_readonly`). El usuario `caudal_migrator` solo se usa para migraciones.

### 4.2 Conexiones

| Uso | Host | Opciones |
|---|---|---|
| API (`DATABASE_URL`) | Pooler de Neon, si la aplicación lo necesita (verificar compatibilidad con los `SET LOCAL` y `statement_timeout` por rol). | `sslmode=verify-full`. |
| Migraciones (`FLYWAY_URL`) | Conexión directa, sin pooler. | `sslmode=verify-full`. |

Notas:
- Los `SET LOCAL app.aqueduct_id` son por transacción. Funcionan con el pooler en modo de transacción, pero debe confirmarse (verificar).
- Flyway necesita una conexión directa porque usa bloqueos de sesión y comandos que el pooler puede no soportar (verificar).
- `sslmode=verify-full` exige que el cliente valide el certificado del servidor. Se configura el almacén de confianza con el certificado de Neon (verificar cómo lo entrega la imagen de Render).

### 4.3 Roles y tiempos

Los roles y sus tiempos (`statement_timeout` 5 s, `lock_timeout` 3 s, `idle_in_transaction_session_timeout` 10 s) se crean en la migración de base (ver [Modelo de datos](../docs/Modelo-de-datos.md), sección 6). Neon ofrece PITR como respaldo.

## 5. Migraciones

- Flyway aplica las migraciones de `src/main/resources/db/migration/` con el usuario `caudal_migrator`.
- Opción A (propuesta): Flyway corre al arrancar la aplicación, antes de aceptar tráfico. Simple, pero un arranque que falle en la migración deja el servicio caído.
- Opción B: un paso de despliegue separado (job) que ejecuta las migraciones antes de actualizar el servicio. Más seguro, más pasos.

Decisión pendiente (verificar). En ambas opciones, la aplicación arranca con `caudal_app` para el tráfico normal.

Reglas:
- Una migración aplicada nunca se edita. Un error se corrige con una migración nueva.
- No hay "deshacer" (Flyway Community). El plan de recuperación es un PITR de Neon a una rama nueva, verificado, y después la promoción o el reemplazo decidido por el equipo.
- Una migración que falle deja la base sin cambios parciales (cada migración se ejecuta en una transacción cuando PostgreSQL lo permite; las sentencias que no lo permiten se documentan).

## 6. Local con Docker Compose

Servicios previstos (verificar al crear `docker-compose.yml`):

| Servicio | Imagen | Puerto local | Notas |
|---|---|---|---|
| `db` | `postgres:18` | `5432` | Volumen con nombre. Usuario y base `caudal`. Healthcheck con `pg_isready`. |
| `api` (opcional) | Construida desde el `Dockerfile` | `8080` | Para probar la imagen. El desarrollo normal usa Maven en el host. |

Pasos para desarrollar:

1. Copiar `.env.example` a `.env` y reemplazar cada marcador `cambia-esto-...`. El `.env` no se sube al repositorio (está en `.gitignore`).
2. Levantar la base: `docker compose up -d db`.
3. Ejecutar la API: `./mvnw spring-boot:run` (verificar perfil de desarrollo).
4. Revisar salud: `curl http://localhost:8080/actuator/health`.
5. Revisar Swagger: `http://localhost:8080/swagger-ui.html`.

Notas de TLS en local:
- La especificación exige `sslmode=verify-full` en la nube. En local, el contenedor de PostgreSQL puede no tener certificado válido. La opción local es `sslmode=disable` o un certificado autofirmado con `verify-ca` (verificar). Nunca se usa `disable` contra Neon.
- Las cookies `Secure` no se envían por HTTP en algunos navegadores. Localhost suele tratarse como seguro (verificar con los navegadores del equipo).

## 7. Health check y observabilidad

| Punto | Valor |
|---|---|
| `GET /actuator/health` | Público. Respuesta mínima (`status`). Sin detalles de componentes. |
| `management.endpoint.health.show-details` | `never` |
| `management.endpoints.web.exposure.include` | `health` (y `info` si se necesita). |
| Logs | JSON con `request_id`, sin `Authorization`, `Cookie`, `password`, `token` ni `secret`. |
| Nivel de log | `LOG_LEVEL`, por defecto `INFO`. |

## 8. Lista de verificación antes de desplegar

- [ ] Las pruebas pasan en CI (`./mvnw verify`).
- [ ] La migración nueva fue revisada y no edita una aplicada.
- [ ] Las variables nuevas están en `.env.example` y en Render.
- [ ] Ningún secreto está en el repositorio ni en el historial del PR (gitleaks).
- [ ] La ruta de salud responde `200`.
- [ ] Swagger tiene la política de cabeceras decidida (sección 3.3).
- [ ] Hay un plan de reversión: versión anterior de la imagen y PITR de Neon disponible.

## 9. Pendientes por verificar

| Tema | Por qué importa |
|---|---|
| Memoria real de la JVM en 512 MB con la carga de la demo | Define el plan de Render y los ajustes de la JVM. |
| Región de Render y de Neon | Latencia y cumplimiento. |
| Versión de PostgreSQL 18 disponible en Neon | Requisito de la especificación. |
| Compatibilidad del pooler de Neon con `SET LOCAL` | Afecta el aislamiento por acueducto. |
| Forma de entregar el certificado de Neon a la imagen | Requisito de `verify-full`. |
| Migración al arrancar (opción A) o job separado (opción B) | Riesgo de arranque y de reversión. |
| Política de cabeceras para Swagger UI | Si se cambia la política de la sección 3.3, Swagger deja de cargar en el navegador. |
| Comportamiento de suspensión del plan gratuito de Render | Experiencia de la PWA sin conexión. |
| Hosting de la IA: Hugging Face Spaces o Render | Depende de la memoria que necesite Chronos. |
| Procedimiento de rotación de secretos | Requisito de la especificación, sin procedimiento escrito. |

Relacionados: [Arquitectura](../docs/Arquitectura.md), [Modelo de datos](../docs/Modelo-de-datos.md), [Documentación de API](api-documentation.md), [Plan de pruebas](testing-plan.md)
