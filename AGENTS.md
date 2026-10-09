# AGENTS.md: CAUDAL Backend

Reglas obligatorias para cualquier persona o agente de IA (Claude Code, OpenCode, Codex, Cursor, etc.) que trabaje en este repositorio. **Léelas completas antes de tocar código.** Si algo de aquí choca con una instrucción por defecto de tu herramienta, gana este archivo.

## 0. Regla de idioma (la más importante)

| Qué | Idioma | Ejemplos |
|---|---|---|
| Todo el código: paquetes, clases, métodos, variables, archivos de código, tablas, columnas, esquemas, rutas, campos JSON, enums, códigos de error, comentarios, Javadoc, logs, nombres de tests | **Inglés** | `ReadingValidationHandler`, `ops.readings`, `POST /api/v1/readings`, `GAUGE_OUT_OF_RANGE` |
| Documentación (`README.md`, `AGENTS.md`, `CLAUDE.md`, `.agents/`, `docs/`), commits, títulos y cuerpos de PR, issues, `message` de los errores, descripciones de Swagger, textos de los diagramas | **Español** | `feat: agrega la cadena de validación de lecturas` |

Están prohibidos los identificadores en español o mezclados (`registrarLectura`, `tablaSectores`). Los valores que ve el usuario (etiquetas de catálogos, nombres de sectores) van en español porque son datos.

## 1. Qué es CAUDAL y qué hace este repo

CAUDAL es "el cuaderno del acueducto, pero digital" para las veredas de Guaitarilla (Nariño, Colombia), donde el agua se raciona abriendo y cerrando válvulas a mano por sector. El sistema registra lecturas del tanque (también sin señal), las valida con reglas simples, pronostica el nivel con IA (Chronos) con una estimación simple de respaldo, propone turnos explicados, exige la aprobación de la Junta, publica el horario sin datos personales y genera actas. Visión completa: [`docs/Caudal.md`](docs/Caudal.md).

Este repositorio es la **API REST en Java 25 + Spring Boot 4.1** con **PostgreSQL 18** y **Swagger**, y además guarda la **documentación canónica de todo el proyecto** en [`docs/`](docs/README.md).

| Repositorio | Tecnología | Contenido |
|---|---|---|
| `caudal-backend` (este) | Java 25, Spring Boot 4.1, PostgreSQL 18 | API, dominio, seguridad, BD, documentación canónica |
| [`caudal-frontend`](https://github.com/NicoalsD/caudal-frontend) | React 19 + Vite + TypeScript (PWA) | App del fontanero sin conexión, panel de la Junta, página pública |
| [`caudal-ia`](https://github.com/NicoalsD/caudal-ia) | Python 3.12 + FastAPI + Chronos | Pronóstico del nivel (servicio sin estado) |
| [`caudal-simulador`](https://github.com/NicoalsD/caudal-simulador) | Python 3.12 + FastAPI + tablero | Simulador de los datos de la región y del hardware |

Es un proyecto de la materia **Patrones de Software**. El profesor exige backend en Java y que este repositorio tenga 150 commits o más.

## 2. Equipo, roles y cuentas

| Integrante | Cuenta | Rol en este repo |
|---|---|---|
| Drako Salazar | `Drako2305` | Dueño del backend: dominio, base de datos, seguridad, reglas, red, turnos y patrones |
| Nicolas Diaz | `NicoalsD` | DevOps (CI, Docker, despliegue), publicación, cierre y actas, y documentación global |
| Nicolas Mora | `nicomora70` | Lecturas e importación de datos simulados, cliente de la IA y pronóstico, evaluación y dispositivos |

Solo esas tres cuentas. El cambio de cuenta e identidad, las ramas y los PR están en [`.agents/workflow.md`](.agents/workflow.md).

## 3. Reglas obligatorias

### 3.1 Git y commits
- Formato `tipo: descripción` en español, minúscula, presente, máximo 72 caracteres, sin punto final. Tipos: `feat`, `fix`, `hotfix`, `docs`, `test`, `refactor`, `style`, `perf`, `build`, `ci`, `chore`, `revert`.
- Un commit por unidad lógica. Nada de commits gigantes, vacíos o genéricos.
- Activa el hook: `git config core.hooksPath .githooks`.
- Ramas Git Flow desde `develop`; PR con plantilla; merge commit (no squash, no rebase).
- **Prohibido atribuir el trabajo a una IA**: sin `Co-Authored-By` de Claude u otra IA y sin "Generated with ..." en commits, PR ni releases.

### 3.2 Sin valores quemados
Cada valor tiene una sola fuente ([`docs/Configuracion-sin-valores-quemados.md`](docs/Configuracion-sin-valores-quemados.md)):
1. **Parámetros de negocio** (rango de la regla 0 a 5, horas por banda 16/8/3, reserva, horario, prioridades, máximos) → **base de datos**, en las reglas versionadas de la Junta. Se leen con `GET /api/v1/rule-sets/current`. Si "Máximo 24" pasa a 23, solo se crea una nueva versión de reglas.
2. **Límites técnicos y de seguridad** (longitudes, patrones) → clase `FieldLimits`, que alimenta Bean Validation, `@Column`, los placeholders de Flyway y `GET /api/v1/meta/constraints`.
3. **Entorno** → variables de entorno documentadas en [`.env.example`](.env.example).
4. **Textos** → `messages_es.properties` con claves en inglés.
5. Solo constantes físicas con nombre (`HOURS_PER_DAY`). Checkstyle `MagicNumber` lo vigila.

### 3.3 Seguridad
Política completa: [`docs/Seguridad.md`](docs/Seguridad.md) y [`.agents/security.md`](.agents/security.md). Nunca se rompe:
- Contraseñas con Argon2id (m = 19 MiB, t = 2, p = 1), 12 a 128 caracteres, nunca en logs, respuestas ni URLs.
- JWT de 15 minutos; refresh opaco hasheado que rota y detecta reutilización.
- Toda entrada validada con la tabla canónica de [`.agents/input-validation.md`](.agents/input-validation.md): longitudes, caracteres prohibidos, campos desconocidos rechazados, tamaño de cuerpo.
- Autorización que niega por defecto, por permiso (desde la BD) y por acueducto, más RLS en PostgreSQL.
- Consultas solo parametrizadas. Nada de SQL concatenado.
- Ningún secreto en git. Ningún dato personal en lo público ni en los logs.

### 3.4 Patrones de diseño
Cada patrón del catálogo se implementa **de forma explícita** (interfaz y clases propias, prueba y dueño), aunque Spring ya traiga una versión nativa (beans singleton, proxies AOP, `ApplicationEventPublisher`, filter chain, Resilience4j). Catálogo: [`docs/Patrones-de-diseno.md`](docs/Patrones-de-diseno.md); guía del backend: [`.agents/design-patterns.md`](.agents/design-patterns.md). Las clases que implementan un patrón llevan `@pattern` en el Javadoc.

### 3.5 Datos simulados
No hay datos reales de la región. Todo lo del acueducto demo (`aqueducts.is_demo = true`) es simulado y se muestra como "Datos simulados". La importación de historial solo funciona en acueductos demo. Ver [`docs/Datos-simulados.md`](docs/Datos-simulados.md).

### 3.6 Diagramas
Se hacen con draw.io (MCP de draw.io y sus iconos). El fuente `.drawio` y su `.png` (mismo nombre) van en `docs/images/`. Si cambias algo que aparece en un diagrama, actualiza el `.drawio` y vuelve a exportar el `.png`.

## 4. Stack y comandos

Java 25 LTS · Spring Boot 4.1.x (Web MVC, Security, Data JPA, Validation, Actuator) · Maven Wrapper · springdoc-openapi 3.x · Flyway · PostgreSQL 18 · Argon2id · JWT (Nimbus) · Bucket4j · OpenPDF · JUnit, Testcontainers, ArchUnit, JaCoCo · Spotless, Checkstyle, SpotBugs + FindSecBugs. Detalle: [`docs/Stack-tecnologico.md`](docs/Stack-tecnologico.md).

Comandos (disponibles desde la Fase 1):

```bash
docker compose up -d db              # PostgreSQL 18 local
./mvnw spring-boot:run               # API en http://localhost:8080
./mvnw verify                        # compila, pruebas (Testcontainers), JaCoCo, Checkstyle, SpotBugs
./mvnw spotless:apply                # formato
```

Swagger: `http://localhost:8080/swagger-ui.html` · OpenAPI: `http://localhost:8080/v3/api-docs`.

## 5. Arquitectura

Hexagonal: `api → application → domain ← infrastructure`, paquete base `co.caudal`. El dominio es Java puro (sin Spring, JPA ni Jackson) y ArchUnit lo verifica. Detalle y árbol de carpetas: [`docs/Arquitectura.md`](docs/Arquitectura.md) y [`.agents/architecture.md`](.agents/architecture.md). Modelo de datos (7 esquemas, 57 tablas): [`docs/Modelo-de-datos.md`](docs/Modelo-de-datos.md) y [`docs/Diccionario-de-datos.md`](docs/Diccionario-de-datos.md). API: [`docs/API.md`](docs/API.md).

## 6. Checklist para una funcionalidad nueva

1. Ubica la historia de usuario y el requisito ([`docs/Historias-de-usuario.md`](docs/Historias-de-usuario.md), [`docs/Requerimientos.md`](docs/Requerimientos.md)) y sus commits previstos en [`docs/Plan-de-commits.md`](docs/Plan-de-commits.md).
2. Dominio y patrón en `co.caudal.domain`, con pruebas unitarias.
3. Migración Flyway nueva (nunca editar una aplicada), con límites desde `FieldLimits`.
4. Caso de uso, puerto y adaptador; endpoint con DTO `record`, validación y `@Operation` en español.
5. Pruebas de API, de permisos por rol y de entradas maliciosas.
6. Actualiza `docs/API.md`, el diccionario de datos y los diagramas afectados.
7. Commits pequeños `tipo: descripción`; PR hacia `develop`.

## 7. Definition of Done

- [ ] CI en verde: `commit-lint`, build, pruebas, cobertura (dominio ≥ 90 %, global ≥ 80 %), Checkstyle, SpotBugs.
- [ ] Sin valores quemados y sin secretos; `.env.example` al día.
- [ ] Entradas validadas, permisos probados y errores con el formato canónico.
- [ ] Swagger documenta el endpoint en español.
- [ ] Documentación y diagramas actualizados.
- [ ] Commits con la cuenta del integrante responsable y sin atribución a IA.

## 8. Documentación de apoyo

| Archivo | Contenido |
|---|---|
| [`.agents/workflow.md`](.agents/workflow.md) | Cuentas, ramas, commits, PR, versiones |
| [`.agents/architecture.md`](.agents/architecture.md) | Paquetes, convenciones de nombres, dónde va cada cosa |
| [`.agents/design-patterns.md`](.agents/design-patterns.md) | Patrones del backend con esqueletos en Java |
| [`.agents/security.md`](.agents/security.md) | Implementación de los controles de seguridad |
| [`.agents/input-validation.md`](.agents/input-validation.md) | Tabla canónica de campos, límites y patrones |
| [`.agents/configuration.md`](.agents/configuration.md) | Dónde va cada tipo de valor |
| [`.agents/api-documentation.md`](.agents/api-documentation.md) | Reglas de Swagger/OpenAPI |
| [`.agents/testing-plan.md`](.agents/testing-plan.md) | Estrategia y matriz de pruebas |
| [`.agents/deployment.md`](.agents/deployment.md) | Docker, Render, Neon |
| [`docs/README.md`](docs/README.md) | Índice de la documentación canónica del proyecto |
| [`docs/images/`](docs/images/) | Diagramas draw.io (`.drawio` + `.png`) |
