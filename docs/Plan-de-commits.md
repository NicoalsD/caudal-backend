# Plan de commits

## Propósito

Lista de unidades de implementación planeadas para las fases 1 a 12 en los cuatro repositorios de CAUDAL. La planeación sigue las historias de usuario, los hechos canónicos y el esquema de datos.

## Cómo usarlo

Marca `Hecho` con `[x]` cuando el commit correspondiente se haya fusionado en `develop`. Conserva el mensaje y la unidad lógica planeada; si el alcance debe cambiar, actualiza el plan antes de dividir o combinar commits.

## Reglas

- Un commit corresponde a una unidad lógica concreta. No se crean commits para alcanzar una cuota.
- El autor indicado publica el trabajo con su propia cuenta de GitHub: `NicoalsD`, `Drako2305` o `nicomora70`.
- Mensajes en español, formato exacto `tipo: descripción`, tipo permitido, presente, minúscula y máximo 72 caracteres, sin punto final.
- No se atribuye trabajo a herramientas o agentes de IA.

## Resumen de implementación

| Repositorio | Fase 1 | Fase 2 | Fase 3 | Fase 4 | Fase 5 | Fase 6 | Fase 7 | Fase 8 | Fase 9 | Fase 10 | Fase 11 | Fase 12 | Total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `caudal-backend` | 17 | 54 | 30 | 12 | 15 | 11 | 13 | 10 | 9 | 3 | 7 | 7 | 188 |
| `caudal-frontend` | 10 | 6 | 9 | 7 | 12 | 6 | 8 | 8 | 5 | 4 | 2 | 3 | 80 |
| `caudal-ia` | 8 | 6 | 3 | 0 | 0 | 16 | 0 | 0 | 0 | 8 | 0 | 4 | 45 |
| `caudal-simulador` | 7 | 14 | 0 | 0 | 8 | 0 | 0 | 0 | 2 | 2 | 24 | 3 | 60 |
| **Total** | **42** | **80** | **42** | **19** | **35** | **33** | **21** | **18** | **16** | **17** | **33** | **17** | **373** |

| Autor | caudal-backend | caudal-frontend | caudal-ia | caudal-simulador | Total |
|---|---:|---:|---:|---:|---:|
| `Drako2305` | 166 | 8 | 0 | 0 | 174 |
| `NicoalsD` | 22 | 72 | 0 | 0 | 94 |
| `nicomora70` | 0 | 0 | 45 | 60 | 105 |
| **Total** | **188** | **80** | **45** | **60** | **373** |

Fase 0: 168 commits de documentación y planeación ya realizados; excluidos de esta lista de implementación.

## `caudal-backend`

### Fase 1: Fundaciones

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B001 | `build: crea el esqueleto Maven de Spring Boot` | `Drako2305` | - | Hexagonal | [ ] |
| B002 | `build: configura Java 25 y el Maven Wrapper` | `Drako2305` | - | - | [ ] |
| B003 | `style: configura Spotless con google-java-format` | `Drako2305` | - | - | [ ] |
| B004 | `build: configura Checkstyle y la regla MagicNumber` | `Drako2305` | - | - | [ ] |
| B005 | `build: configura SpotBugs y FindSecBugs` | `Drako2305` | - | - | [ ] |
| B006 | `ci: valida compilación y commit-lint en GitHub Actions` | `NicoalsD` | - | - | [ ] |
| B007 | `ci: agrega CodeQL y escaneo de secretos` | `NicoalsD` | - | - | [ ] |
| B008 | `build: define dependencias de Spring y OpenAPI` | `Drako2305` | - | - | [ ] |
| B009 | `build: configura PostgreSQL 18 y Flyway` | `Drako2305` | - | Repository | [ ] |
| B010 | `build: levanta PostgreSQL 18 con Docker Compose` | `NicoalsD` | - | - | [ ] |
| B011 | `docs: documenta variables en .env.example` | `NicoalsD` | - | - | [ ] |
| B012 | `feat: configura logs JSON con requestId y redacción` | `Drako2305` | - | - | [ ] |
| B013 | `feat: agrega el formato canónico de errores de API` | `Drako2305` | - | - | [ ] |
| B014 | `feat: expone GET /actuator/health` | `Drako2305` | - | - | [ ] |
| B015 | `feat: publica Swagger y OpenAPI con bearer JWT` | `Drako2305` | - | - | [ ] |
| B016 | `feat: agrega y prueba SystemClock con reloj inyectable` | `Drako2305` | - | P01 | [ ] |
| B017 | `docs: documenta ejecución local y rutas de Swagger` | `NicoalsD` | - | - | [ ] |

### Fase 2: Base de datos

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B018 | `feat: migra iam.users` | `Drako2305` | HU-04 | Repository | [ ] |
| B019 | `feat: migra iam.roles, iam.permissions y iam.role_permissions` | `Drako2305` | HU-04 | - | [ ] |
| B020 | `feat: migra iam.memberships` | `Drako2305` | HU-04 | Repository | [ ] |
| B021 | `feat: migra iam.refresh_tokens` | `Drako2305` | HU-05 | - | [ ] |
| B022 | `feat: migra iam.login_attempts y iam.password_history` | `Drako2305` | HU-02, HU-03 | - | [ ] |
| B023 | `feat: migra iam.password_reset_grants` | `Drako2305` | HU-02, HU-04 | - | [ ] |
| B024 | `feat: migra iam.mfa_factors y iam.mfa_recovery_codes` | `Drako2305` | HU-06 | - | [ ] |
| B025 | `feat: migra iam.api_keys` | `Drako2305` | HU-33 | - | [ ] |
| B026 | `feat: migra iam.privacy_notice_versions y privacy_acceptances` | `Drako2305` | HU-04 | - | [ ] |
| B027 | `feat: migra iam.security_events y iam.rate_limit_buckets` | `Drako2305` | HU-03 | - | [ ] |
| B028 | `feat: migra org.aqueducts` | `Drako2305` | HU-24 | - | [ ] |
| B029 | `feat: migra org.tanks` | `Drako2305` | HU-14 | - | [ ] |
| B030 | `feat: migra org.sectors` | `Drako2305` | HU-18 | P08 | [ ] |
| B031 | `feat: migra org.valves` | `Drako2305` | HU-34 | - | [ ] |
| B032 | `feat: migra org.catalog_items` | `Drako2305` | HU-11, HU-27 | P23 | [ ] |
| B033 | `feat: migra org.rule_sets` | `Drako2305` | HU-07, HU-08 | P05 | [ ] |
| B034 | `feat: migra bandas y ajustes de reglas` (org.rule_level_bands, org.rule_sector_settings, org.rule_valve_orders, org.rule_operating_windows) | `Drako2305` | HU-07 | P05 | [ ] |
| B035 | `feat: migra ops.sync_batches` | `Drako2305` | HU-10 | P13 | [ ] |
| B036 | `feat: migra ops.readings` | `Drako2305` | HU-09, HU-11 | P12 | [ ] |
| B037 | `feat: migra ops.reading_issues y ops.reading_corrections` | `Drako2305` | HU-12, HU-13 | P12 | [ ] |
| B038 | `feat: migra ops.anomalies` | `Drako2305` | HU-17 | P18 | [ ] |
| B039 | `feat: migra pronósticos en ops.forecast_runs y forecast_points` | `Drako2305` | HU-15, HU-16 | P06 | [ ] |
| B040 | `feat: migra ops.forecast_evaluations` | `Drako2305` | HU-33 | P20 | [ ] |
| B041 | `feat: migra ops.schedule_proposals` | `Drako2305` | HU-18 | P18 | [ ] |
| B042 | `feat: migra ops.schedule_items y schedule_item_reasons` | `Drako2305` | HU-18, HU-19 | P04 | [ ] |
| B043 | `feat: migra ops.proposal_snapshots y proposal_decisions` | `Drako2305` | HU-20, HU-21 | P16 | [ ] |
| B044 | `feat: migra ops.publications` | `Drako2305` | HU-23 | P17 | [ ] |
| B045 | `feat: migra ops.shift_executions y ops.day_closures` | `Drako2305` | HU-29 | P13 | [ ] |
| B046 | `feat: migra ops.incidents y incident_status_history` | `Drako2305` | HU-27, HU-28 | P18 | [ ] |
| B047 | `feat: migra reporting.minutes y reporting.summary_shares` | `Drako2305` | HU-30, HU-31 | P04 | [ ] |
| B048 | `feat: migra devices.devices y devices.device_keys` | `Drako2305` | HU-34 | P06 | [ ] |
| B049 | `feat: migra devices.device_nonces y telemetry_points` | `Drako2305` | HU-35 | P12 | [ ] |
| B050 | `feat: migra devices.valve_commands y valve_command_events` | `Drako2305` | HU-36 | P13 | [ ] |
| B051 | `feat: migra audit.audit_log` | `Drako2305` | HU-37 | P09 | [ ] |
| B052 | `feat: migra audit.audit_anchors` | `Drako2305` | HU-37 | - | [ ] |
| B053 | `feat: migra audit.data_access_log y retention_policies` | `Drako2305` | HU-32, HU-37 | P09 | [ ] |
| B054 | `feat: migra sim.import_batches` | `Drako2305` | HU-38 | P12 | [ ] |
| B055 | `feat: agrega triggers append-only y updated_at` | `Drako2305` | HU-37 | P09 | [ ] |
| B056 | `feat: impide cambios en versiones activas de reglas` | `Drako2305` | HU-08 | P05 | [ ] |
| B057 | `feat: valida gauge_value contra el rango del tanque` | `Drako2305` | HU-11 | P12 | [ ] |
| B058 | `feat: encadena hashes de audit.audit_log` | `Drako2305` | HU-37 | P09 | [ ] |
| B059 | `feat: aplica RLS con app.aqueduct_id a tablas protegidas` | `Drako2305` | HU-04, HU-32 | - | [ ] |
| B060 | `feat: restringe password_hash, token_hash y secret_ciphertext` | `Drako2305` | HU-02, HU-05, HU-06 | - | [ ] |
| B061 | `feat: agrega semilla de roles y permisos` | `Drako2305` | HU-04 | - | [ ] |
| B062 | `feat: agrega semilla de catálogos y políticas de retención` | `Drako2305` | HU-27, HU-37 | - | [ ] |
| B063 | `feat: agrega acueducto demo e historial simulado inicial` | `Drako2305` | HU-39 | - | [ ] |
| B064 | `test: verifica restricciones y relaciones de iam` | `Drako2305` | HU-04, HU-05 | Repository | [ ] |
| B065 | `test: verifica RLS y aislamiento entre acueductos` | `Drako2305` | HU-04, HU-32 | - | [ ] |
| B066 | `test: verifica append-only y triggers de ops` | `Drako2305` | HU-11, HU-37 | P09 | [ ] |
| B067 | `test: verifica exclusiones de bandas y turnos` | `Drako2305` | HU-07, HU-19 | - | [ ] |
| B068 | `test: compara límites SQL con FieldLimits` | `Drako2305` | HU-11 | - | [ ] |
| B069 | `test: verifica privilegios de caudal_app y caudal_readonly` | `Drako2305` | HU-32, HU-37 | - | [ ] |
| B070 | `docs: publica el diccionario de las 57 tablas` | `NicoalsD` | - | - | [ ] |
| B071 | `docs: actualiza la ERD de los siete esquemas` | `NicoalsD` | - | - | [ ] |
| B072 | `docs: documenta roles SQL, RLS y retención` | `NicoalsD` | - | - | [ ] |

### Fase 3: Seguridad y login

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B073 | `feat: agrega límites técnicos en FieldLimits` | `Drako2305` | HU-02, HU-11 | - | [ ] |
| B074 | `feat: valida username con FieldLimits` | `Drako2305` | HU-01 | - | [ ] |
| B075 | `feat: normaliza contraseñas con Unicode NFKC` | `Drako2305` | HU-02 | - | [ ] |
| B076 | `feat: codifica contraseñas con Argon2id` | `Drako2305` | HU-01, HU-02 | - | [ ] |
| B077 | `feat: verifica contraseñas comunes y nombre de usuario` | `Drako2305` | HU-02 | - | [ ] |
| B078 | `feat: agrega hash señuelo para usuarios inexistentes` | `Drako2305` | HU-01 | - | [ ] |
| B079 | `feat: emite JWT HS256 con claims de sesión` | `Drako2305` | HU-01 | - | [ ] |
| B080 | `feat: valida issuer, audience y token_version del JWT` | `Drako2305` | HU-01, HU-05 | - | [ ] |
| B081 | `feat: rota refresh tokens opacos almacenados como hash` | `Drako2305` | HU-05 | - | [ ] |
| B082 | `feat: revoca familias al detectar refresh reutilizado` | `Drako2305` | HU-05 | P18 | [ ] |
| B083 | `feat: configura cookie caudal_rt y protección de Origin` | `Drako2305` | HU-01 | - | [ ] |
| B084 | `feat: aplica permisos deny-by-default desde iam` | `Drako2305` | HU-04 | - | [ ] |
| B085 | `feat: filtra membresías por aqueduct_id y user_id` | `Drako2305` | HU-04 | - | [ ] |
| B086 | `feat: agrega GET /api/v1/auth/me` | `Drako2305` | HU-01 | - | [ ] |
| B087 | `feat: implementa POST /api/v1/auth/login` | `Drako2305` | HU-01, HU-03 | - | [ ] |
| B088 | `feat: implementa POST /api/v1/auth/refresh` | `Drako2305` | HU-05 | - | [ ] |
| B089 | `feat: implementa POST /api/v1/auth/logout` | `Drako2305` | HU-05 | P13 | [ ] |
| B090 | `feat: implementa POST /api/v1/auth/logout-all` | `Drako2305` | HU-05 | P13 | [ ] |
| B091 | `feat: implementa POST /api/v1/auth/change-password` | `Drako2305` | HU-02 | P13 | [ ] |
| B092 | `feat: implementa POST /api/v1/auth/reset-password` | `Drako2305` | HU-02, HU-04 | P13 | [ ] |
| B093 | `feat: implementa POST /api/v1/users con alta segura` | `Drako2305` | HU-04 | P13 | [ ] |
| B094 | `feat: implementa PATCH /api/v1/users/{id}` | `Drako2305` | HU-04 | P13 | [ ] |
| B095 | `feat: implementa GET y POST /api/v1/memberships` | `Drako2305` | HU-04 | - | [ ] |
| B096 | `feat: implementa DELETE /api/v1/memberships/{id}` | `Drako2305` | HU-04 | P13 | [ ] |
| B097 | `feat: agrega TOTP cifrado con AES-256-GCM` | `Drako2305` | HU-06 | P06 | [ ] |
| B098 | `feat: implementa enrolamiento y confirmación MFA` | `Drako2305` | HU-06 | - | [ ] |
| B099 | `feat: genera y valida códigos MFA de respaldo` | `Drako2305` | HU-06 | - | [ ] |
| B100 | `feat: implementa estados de bloqueo escalonado de cuenta` | `Drako2305` | HU-03 | P18 | [ ] |
| B101 | `feat: limita login por IP con Bucket4j y PostgreSQL` | `Drako2305` | HU-03 | - | [ ] |
| B102 | `feat: agrega AuditingCommandHandler para eventos de seguridad` | `Drako2305` | HU-03, HU-06 | P09 | [ ] |

### Fase 4: Reglas y red

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B103 | `feat: implementa GET /api/v1/rule-sets/current` | `Drako2305` | HU-08 | - | [ ] |
| B104 | `feat: implementa GET /api/v1/rule-sets` | `Drako2305` | HU-07 | - | [ ] |
| B105 | `feat: implementa GET /api/v1/rule-sets/{id}` | `Drako2305` | HU-07 | - | [ ] |
| B106 | `feat: copia reglas con RuleSet.copyForNewVersion()` | `Drako2305` | HU-07 | P05 | [ ] |
| B107 | `feat: implementa POST /api/v1/rule-sets` | `Drako2305` | HU-07 | P05 | [ ] |
| B108 | `feat: valida cobertura y solape de rule_level_bands` | `Drako2305` | HU-07 | - | [ ] |
| B109 | `feat: implementa PATCH /api/v1/rule-sets/{id}` | `Drako2305` | HU-07 | P05 | [ ] |
| B110 | `feat: activa reglas con ActivateRuleSetCommand` | `Drako2305` | HU-08 | P13 | [ ] |
| B111 | `feat: agrega DomainEventPublisher para RuleSetActivated` | `Drako2305` | HU-08 | P17 | [ ] |
| B112 | `feat: gestiona tanques en GET y POST /api/v1/tanks` | `Drako2305` | HU-14 | - | [ ] |
| B113 | `feat: modela red con NetworkNode` (SectorNode, ValveNode; GET/POST /api/v1/sectors, PATCH /api/v1/sectors/{id}, GET/POST /api/v1/sectors/{id}/valves, PATCH /api/v1/valves/{id}) | `Drako2305` | HU-18 | P08 | [ ] |
| B114 | `feat: publica GET /api/v1/catalogs/{catalog}` | `Drako2305` | HU-11, HU-27 | P23 | [ ] |
| B115 | `feat: expone GET /api/v1/meta/constraints` | `Drako2305` | HU-02, HU-11 | - | [ ] |

### Fase 5: Lecturas e importación

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B116 | `feat: implementa ReadingValidationHandler para rango` | `Drako2305` | HU-11 | P12 | [ ] |
| B117 | `feat: valida fecha y antigüedad de lecturas` | `Drako2305` | HU-11 | P12 | [ ] |
| B118 | `feat: marca duplicados en ReadingValidationHandler` | `Drako2305` | HU-12 | P12 | [ ] |
| B119 | `feat: marca saltos bruscos en ReadingValidationHandler` | `Drako2305` | HU-12 | P12 | [ ] |
| B120 | `feat: agrega idempotencia por UUID en POST /readings` | `Drako2305` | HU-09 | P13 | [ ] |
| B121 | `feat: implementa POST /api/v1/readings` | `Drako2305` | HU-09, HU-11 | P12 | [ ] |
| B122 | `feat: procesa POST /api/v1/readings/batch por elemento` | `Drako2305` | HU-10 | P13 | [ ] |
| B123 | `feat: publica GET /api/v1/readings con cursor firmado` | `Drako2305` | HU-09 | - | [ ] |
| B124 | `feat: publica GET /api/v1/readings/{id}` | `Drako2305` | HU-09 | - | [ ] |
| B125 | `feat: agrega POST /api/v1/readings/{id}/corrections` | `Drako2305` | HU-13 | P13 | [ ] |
| B126 | `feat: calcula effective_readings sin alterar el original` | `Drako2305` | HU-13 | Repository | [ ] |
| B127 | `feat: valida idempotencia y orden de ops.sync_batches` | `Drako2305` | HU-10 | P13 | [ ] |
| B128 | `feat: limita importaciones al acueducto demo` | `Drako2305` | HU-38 | P12 | [ ] |
| B129 | `feat: implementa POST /api/v1/imports/readings` | `Drako2305` | HU-38 | P12 | [ ] |
| B130 | `feat: importa ejecuciones de turnos e incidentes simulados` | `Drako2305` | HU-38 | P12 | [ ] |

### Fase 6: Pronóstico

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B131 | `feat: adapta contrato IA con IaForecastClientAdapter` | `Drako2305` | HU-15 | P06 | [ ] |
| B132 | `feat: configura timeout de conexión y lectura de IA` | `Drako2305` | HU-16 | P11 | [ ] |
| B133 | `feat: implementa CircuitBreakerState para pronósticos` | `Drako2305` | HU-16 | P18 | [ ] |
| B134 | `feat: calcula respaldo de persistencia y rango empírico` | `Drako2305` | HU-16 | P19 | [ ] |
| B135 | `feat: implementa RemoteForecasterProxy con respaldo` | `Drako2305` | HU-16 | P11 | [ ] |
| B136 | `feat: guarda pronóstico IA y baseline en sombra` | `Drako2305` | HU-16 | P09 | [ ] |
| B137 | `feat: agrega TankStatusFacade, TankLevelState y TrendStrategy` | `Drako2305` | HU-14 | P10, P18, P19 | [ ] |
| B138 | `feat: publica GET /api/v1/tanks/{id}/forecasts/latest` | `Drako2305` | HU-15 | P10 | [ ] |
| B139 | `feat: implementa POST /api/v1/tanks/{id}/forecasts` | `Drako2305` | HU-15, HU-16 | P06 | [ ] |
| B140 | `feat: revisa anomalías con PATCH /api/v1/anomalies/{id}` | `Drako2305` | HU-17 | P18 | [ ] |
| B141 | `test: verifica estados, timeout y respaldo del pronóstico` | `Drako2305` | HU-16 | P18 | [ ] |

### Fase 7: Turnos y decisión de la Junta

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B142 | `feat: agrega estados de ProposalState` | `Drako2305` | HU-20, HU-22 | P18 | [ ] |
| B143 | `feat: construye propuestas con ScheduleProposalBuilder` | `Drako2305` | HU-18 | P04 | [ ] |
| B144 | `feat: itera sectores con SectorTurnIterator` | `Drako2305` | HU-18 | P14 | [ ] |
| B145 | `feat: asigna turnos con TurnAllocationStrategy` | `Drako2305` | HU-18, HU-19 | P19 | [ ] |
| B146 | `feat: recorta turnos por reserva y ventana operativa` | `Drako2305` | HU-19 | P19 | [ ] |
| B147 | `feat: explica turnos con schedule_item_reasons` | `Drako2305` | HU-18 | P22 | [ ] |
| B148 | `feat: implementa POST /api/v1/schedule-proposals` | `Drako2305` | HU-18 | P04 | [ ] |
| B149 | `feat: implementa GET /api/v1/schedule-proposals` | `Drako2305` | HU-18 | - | [ ] |
| B150 | `feat: implementa GET /api/v1/schedule-proposals/{id}` | `Drako2305` | HU-18 | - | [ ] |
| B151 | `feat: agrega CommandBus y ApproveProposalCommand` | `Drako2305` | HU-20 | P13 | [ ] |
| B152 | `feat: guarda ProposalMemento antes de modificar` | `Drako2305` | HU-21 | P16 | [ ] |
| B153 | `feat: procesa ModifyProposalCommand y RejectProposalCommand` | `Drako2305` | HU-21, HU-22 | P13 | [ ] |
| B154 | `feat: rechaza solapes con exclusión tstzrange` | `Drako2305` | HU-19 | - | [ ] |

### Fase 8: Publicación y reportes de daño

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B155 | `feat: implementa PublishScheduleCommand` | `NicoalsD` | HU-23 | P13 | [ ] |
| B156 | `feat: publica SchedulePublished al confirmar horario` | `NicoalsD` | HU-23 | P17 | [ ] |
| B157 | `feat: implementa PublicScheduleProxy sin datos personales` | `NicoalsD` | HU-24 | P11 | [ ] |
| B158 | `feat: expone GET /api/v1/public/{aqueductSlug}/schedule` | `NicoalsD` | HU-24 | P11 | [ ] |
| B159 | `feat: genera avisos con Notice y PublicationChannel` | `NicoalsD` | HU-25 | P07 | [ ] |
| B160 | `feat: crea cartel con DocumentGeneratorCreator y DocumentFactory` | `NicoalsD` | HU-26 | P02, P03 | [ ] |
| B161 | `feat: agrega marca demo a página, WhatsApp y cartel` | `NicoalsD` | HU-39 | P22 | [ ] |
| B162 | `feat: recibe POST /api/v1/public/{aqueductSlug}/damage-reports` | `NicoalsD` | HU-27 | P13 | [ ] |
| B163 | `feat: protege formulario público con honeypot y tasa` | `NicoalsD` | HU-27 | - | [ ] |
| B164 | `feat: consulta seguimiento público por trackingCode` | `NicoalsD` | HU-27 | P18 | [ ] |

### Fase 9: Cierre y actas

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B165 | `feat: registra POST /api/v1/schedule-items/{id}/execution` | `Drako2305` | HU-29 | P13 | [ ] |
| B166 | `feat: implementa POST /api/v1/days/{date}/closure` | `Drako2305` | HU-29 | P13 | [ ] |
| B167 | `feat: impide cierres repetidos en ops.day_closures` | `Drako2305` | HU-29 | P18 | [ ] |
| B168 | `feat: construye actas con MinutesBuilder` | `Drako2305` | HU-30 | P04 | [ ] |
| B169 | `feat: clasifica secciones con MinutesSectionVisitor` | `Drako2305` | HU-30 | P21 | [ ] |
| B170 | `feat: genera documentos con DocumentGenerator` | `Drako2305` | HU-30 | P20 | [ ] |
| B171 | `feat: implementa POST /api/v1/minutes` | `Drako2305` | HU-30 | P04 | [ ] |
| B172 | `feat: finaliza acta con POST /api/v1/minutes/{id}/finalize` | `Drako2305` | HU-30 | P13 | [ ] |
| B173 | `feat: crea PDF final y guarda pdf_sha256` | `Drako2305` | HU-30 | P02 | [ ] |

### Fase 10: Evaluación IA

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B174 | `feat: calcula MAE, WQL, cobertura y skill por horizonte` | `Drako2305` | HU-33 | P19 | [ ] |
| B175 | `feat: implementa GET /api/v1/forecast-evaluation` | `Drako2305` | HU-33 | P10 | [ ] |
| B176 | `test: verifica muestra mínima y skill no calculable` | `Drako2305` | HU-33 | P19 | [ ] |

### Fase 11: Simulador en vivo y dispositivos

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B177 | `feat: registra dispositivos en POST /api/v1/devices` | `Drako2305` | HU-34 | P06 | [ ] |
| B178 | `feat: rota claves Ed25519 con periodo de validez` | `Drako2305` | HU-34 | P06 | [ ] |
| B179 | `feat: verifica firma Ed25519 y ventana temporal` | `Drako2305` | HU-35 | P12 | [ ] |
| B180 | `feat: rechaza nonce repetido en POST /api/v1/devices/telemetry` | `Drako2305` | HU-35 | P12 | [ ] |
| B181 | `feat: procesa telemetría con DeviceTelemetryAdapter` | `Drako2305` | HU-35 | P06 | [ ] |
| B182 | `feat: obtiene comandos en GET /api/v1/devices/commands` | `Drako2305` | HU-36 | P13 | [ ] |
| B183 | `feat: confirma comandos en POST /api/v1/devices/commands/{id}/ack` | `Drako2305` | HU-36 | P18 | [ ] |

### Fase 12: Endurecimiento, despliegue y v1.0.0

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| B184 | `ci: agrega pruebas de integración con Testcontainers` | `NicoalsD` | - | - | [ ] |
| B185 | `ci: bloquea dependencias vulnerables en Maven` | `NicoalsD` | - | - | [ ] |
| B186 | `build: configura imagen Docker de producción` | `NicoalsD` | - | - | [ ] |
| B187 | `ci: despliega API en Render desde develop` | `NicoalsD` | - | - | [ ] |
| B188 | `feat: agrega pruebas de autorización y aislamiento RLS` | `Drako2305` | HU-04, HU-35 | - | [ ] |
| B189 | `test: verifica redacción de secretos en logs y errores` | `Drako2305` | HU-01, HU-05 | - | [ ] |
| B190 | `docs: documenta endurecimiento y versión v1.0.0` | `Drako2305` | - | - | [ ] |

## `caudal-frontend`

### Fase 1: Fundaciones

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F001 | `build: crea la aplicación React 19 con Vite y TypeScript` | `NicoalsD` | - | - | [ ] |
| F002 | `build: activa strict y exactOptionalPropertyTypes` | `NicoalsD` | - | - | [ ] |
| F003 | `style: configura ESLint y Prettier` | `NicoalsD` | - | - | [ ] |
| F004 | `ci: valida pnpm, lint y build en GitHub Actions` | `NicoalsD` | - | - | [ ] |
| F005 | `test: configura Vitest y Testing Library` | `NicoalsD` | - | - | [ ] |
| F006 | `feat: configura router y layouts por rol` | `NicoalsD` | - | - | [ ] |
| F007 | `style: agrega tokens CSS y tipografías autoalojadas` | `NicoalsD` | - | - | [ ] |
| F008 | `feat: carga textos desde src/i18n/es.ts` | `NicoalsD` | - | - | [ ] |
| F009 | `build: genera src/services/api/schema.d.ts desde OpenAPI` | `NicoalsD` | - | - | [ ] |
| F010 | `test: configura MSW para simular la API` | `NicoalsD` | - | - | [ ] |

### Fase 2: Base para datos y navegación

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F011 | `feat: agrega HttpClient con manejo de errores de API` | `NicoalsD` | - | P09 | [ ] |
| F012 | `feat: agrega CaudalApi como fachada de servicios` | `NicoalsD` | - | P10 | [ ] |
| F013 | `feat: adapta DTO de API con ApiDtoAdapter` | `NicoalsD` | - | P06 | [ ] |
| F014 | `feat: construye formularios desde FormSchemaBuilder` | `NicoalsD` | - | P04 | [ ] |
| F015 | `feat: obtiene restricciones de GET /meta/constraints` | `NicoalsD` | - | - | [ ] |
| F016 | `test: cubre adaptador DTO y validación de formularios` | `NicoalsD` | - | P06 | [ ] |

### Fase 3: Seguridad y login

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F017 | `feat: implementa AuthHttpClient con token en memoria` | `Drako2305` | HU-01 | P09 | [ ] |
| F018 | `feat: crea formulario de inicio de sesión` | `Drako2305` | HU-01 | - | [ ] |
| F019 | `feat: integra POST /api/v1/auth/login` | `Drako2305` | HU-01 | - | [ ] |
| F020 | `feat: renueva sesión con POST /api/v1/auth/refresh` | `Drako2305` | HU-05 | P11 | [ ] |
| F021 | `feat: muestra cambio obligatorio de contraseña` | `Drako2305` | HU-02 | - | [ ] |
| F022 | `feat: implementa formulario de cambio de contraseña` | `Drako2305` | HU-02 | - | [ ] |
| F023 | `feat: agrega estado de sesión sin datos secretos` | `Drako2305` | HU-01 | P18 | [ ] |
| F024 | `feat: conserva sesiones aisladas por aqueduct_id` | `NicoalsD` | HU-04 | - | [ ] |
| F025 | `test: verifica login, expiración y cambio obligatorio` | `Drako2305` | HU-01, HU-02 | - | [ ] |

### Fase 4: Reglas y red

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F026 | `feat: muestra reglas vigentes desde rule-sets/current` | `NicoalsD` | HU-07, HU-08 | - | [ ] |
| F027 | `feat: crea editor de bandas de nivel` | `NicoalsD` | HU-07 | P04 | [ ] |
| F028 | `feat: edita sectores prioritarios y su orden` | `NicoalsD` | HU-07 | - | [ ] |
| F029 | `feat: edita ventanas de operación por día` | `NicoalsD` | HU-07 | - | [ ] |
| F030 | `feat: exige motivo al activar reglas` | `NicoalsD` | HU-08 | - | [ ] |
| F031 | `feat: muestra red de tanques, sectores y válvulas` | `NicoalsD` | HU-07 | P08 | [ ] |
| F032 | `test: valida borrador, bandas y activación de reglas` | `NicoalsD` | HU-07, HU-08 | - | [ ] |

### Fase 5: Lecturas y cola offline

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F033 | `feat: agrega OfflineDatabase con Dexie` | `NicoalsD` | HU-09 | P01 | [ ] |
| F034 | `feat: guarda pista de sesión sin secretos en IndexedDB` | `NicoalsD` | HU-09 | - | [ ] |
| F035 | `feat: agrega SubmitReadingCommand a la cola offline` | `NicoalsD` | HU-09 | P13 | [ ] |
| F036 | `feat: valida lectura con reglas y catálogo en caché` | `NicoalsD` | HU-09, HU-11 | - | [ ] |
| F037 | `feat: muestra formulario de lectura del tanque` | `NicoalsD` | HU-09 | - | [ ] |
| F038 | `feat: detecta conexión con ConnectivityMonitor` | `NicoalsD` | HU-10 | P17 | [ ] |
| F039 | `feat: agrega SyncQueueStore observable` | `NicoalsD` | HU-10 | P17 | [ ] |
| F040 | `feat: sincroniza cola por POST /readings/batch` | `NicoalsD` | HU-10 | P13 | [ ] |
| F041 | `feat: conserva pendientes al cambiar de usuario` | `NicoalsD` | HU-09, HU-10 | - | [ ] |
| F042 | `feat: presenta errores de validación de lectura` | `NicoalsD` | HU-11, HU-12 | P12 | [ ] |
| F043 | `feat: agrega corrección de lectura con motivo` | `NicoalsD` | HU-13 | P13 | [ ] |
| F044 | `test: cubre persistencia, reintento y lote parcial offline` | `NicoalsD` | HU-09, HU-10 | P17 | [ ] |

### Fase 6: Estado y pronóstico

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F045 | `feat: presenta estado y antigüedad del tanque` | `NicoalsD` | HU-14 | P10 | [ ] |
| F046 | `feat: grafica p10, p50 y p90 con Recharts` | `NicoalsD` | HU-15 | P06 | [ ] |
| F047 | `feat: distingue pronóstico IA y estimación de respaldo` | `NicoalsD` | HU-16 | P18 | [ ] |
| F048 | `feat: muestra alerta de lectura obsoleta` | `NicoalsD` | HU-14 | - | [ ] |
| F049 | `feat: presenta anomalías pendientes de revisión` | `NicoalsD` | HU-17 | - | [ ] |
| F050 | `test: cubre estados vacíos, error y respaldo del tanque` | `NicoalsD` | HU-14, HU-16 | - | [ ] |

### Fase 7: Propuesta y decisión

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F051 | `feat: muestra propuestas pendientes de revisión` | `NicoalsD` | HU-18 | - | [ ] |
| F052 | `feat: explica motivos de cada turno propuesto` | `NicoalsD` | HU-18 | P22 | [ ] |
| F053 | `feat: agrega ProposalEditorMediator` | `NicoalsD` | HU-21 | P15 | [ ] |
| F054 | `feat: valida horas y motivo en editor de propuesta` | `NicoalsD` | HU-19, HU-21 | P15 | [ ] |
| F055 | `feat: integra aprobar, modificar y rechazar propuestas` | `NicoalsD` | HU-20, HU-21, HU-22 | P13 | [ ] |
| F056 | `feat: muestra sectores sin turno y su explicación` | `NicoalsD` | HU-18 | - | [ ] |
| F057 | `test: verifica edición y decisiones de la Junta` | `NicoalsD` | HU-20, HU-21, HU-22 | P15 | [ ] |
| F058 | `test: cubre accesibilidad del editor de propuestas` | `NicoalsD` | HU-21 | - | [ ] |

### Fase 8: Publicación y reportes

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F059 | `feat: implementa página pública por aqueductSlug` | `NicoalsD` | HU-24 | P11 | [ ] |
| F060 | `feat: muestra solo horario publicado sin datos personales` | `NicoalsD` | HU-24 | P11 | [ ] |
| F061 | `feat: agrega copia del horario para WhatsApp` | `NicoalsD` | HU-25 | P07 | [ ] |
| F062 | `feat: agrega enlace de descarga del cartel PDF` | `NicoalsD` | HU-26 | P03 | [ ] |
| F063 | `feat: publica formulario de reporte de daño` | `NicoalsD` | HU-27 | - | [ ] |
| F064 | `feat: consulta estado por código de seguimiento` | `NicoalsD` | HU-27 | P18 | [ ] |
| F065 | `feat: muestra transiciones de estado de incidentes` | `NicoalsD` | HU-28 | P18 | [ ] |
| F066 | `feat: marca como simulados los datos del acueducto demo` | `NicoalsD` | HU-39 | P22 | [ ] |

### Fase 9: Cierre y actas

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F067 | `feat: registra cumplimiento de turnos` | `NicoalsD` | HU-29 | P13 | [ ] |
| F068 | `feat: agrega formulario de cierre diario` | `NicoalsD` | HU-29 | P13 | [ ] |
| F069 | `feat: presenta actas con cuatro secciones epistémicas` | `NicoalsD` | HU-30 | P21 | [ ] |
| F070 | `feat: descarga PDF final de acta` | `NicoalsD` | HU-30 | P20 | [ ] |
| F071 | `test: verifica cierre y vista de actas` | `NicoalsD` | HU-29, HU-30 | - | [ ] |

### Fase 10: Evaluación IA

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F072 | `feat: presenta métricas MAE, WQL y cobertura` | `NicoalsD` | HU-33 | - | [ ] |
| F073 | `feat: muestra skill por horizonte de pronóstico` | `NicoalsD` | HU-33 | P19 | [ ] |
| F074 | `feat: informa muestra insuficiente sin veredicto` | `NicoalsD` | HU-33 | - | [ ] |
| F075 | `test: verifica estados de la evaluación IA` | `NicoalsD` | HU-33 | - | [ ] |

### Fase 11: Dispositivos

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F076 | `feat: agrega vista de dispositivos registrados` | `NicoalsD` | HU-34 | - | [ ] |
| F077 | `test: verifica vista de dispositivos y estado` | `NicoalsD` | HU-34 | - | [ ] |

### Fase 12: Endurecimiento y despliegue

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| F078 | `ci: despliega PWA en Vercel con CSP estricta` | `NicoalsD` | - | - | [ ] |
| F079 | `ci: agrega pnpm audit y pruebas Playwright con axe` | `NicoalsD` | - | - | [ ] |
| F080 | `docs: documenta publicación y verificación de v1.0.0` | `NicoalsD` | - | - | [ ] |

## `caudal-ia`

### Fase 1: Fundaciones y contrato

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| I001 | `build: crea servicio FastAPI con Python 3.12 y uv` | `nicomora70` | - | - | [ ] |
| I002 | `build: configura Pydantic v2 y pydantic-settings` | `nicomora70` | - | - | [ ] |
| I003 | `style: configura Ruff y mypy para el servicio IA` | `nicomora70` | - | - | [ ] |
| I004 | `ci: valida formato, tipos y pruebas en GitHub Actions` | `nicomora70` | - | - | [ ] |
| I005 | `test: configura pytest y cliente de FastAPI` | `nicomora70` | - | - | [ ] |
| I006 | `feat: define contratos Pydantic de POST /v1/forecasts` | `nicomora70` | HU-15 | P06 | [ ] |
| I007 | `feat: agrega GET /v1/health y GET /v1/ready` | `nicomora70` | HU-16 | - | [ ] |
| I008 | `feat: agrega GET /v1/models` | `nicomora70` | HU-15 | - | [ ] |

### Fase 2: Base del servicio

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| I009 | `feat: valida series finitas y marcas de tiempo crecientes` | `nicomora70` | HU-15 | - | [ ] |
| I010 | `feat: valida prediction_length y cuantiles solicitados` | `nicomora70` | HU-15 | - | [ ] |
| I011 | `feat: registra modelos disponibles en ModelRegistry` | `nicomora70` | HU-15 | P01 | [ ] |
| I012 | `feat: configura nombre de modelo por entorno` | `nicomora70` | HU-15 | - | [ ] |
| I013 | `feat: agrega respuesta uniforme de errores de pronóstico` | `nicomora70` | HU-16 | - | [ ] |
| I014 | `test: cubre validación de series y errores HTTP` | `nicomora70` | HU-15, HU-16 | - | [ ] |

### Fase 3: Seguridad del servicio

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| I015 | `feat: protege POST /v1/forecasts con token de servicio` | `nicomora70` | HU-15 | - | [ ] |
| I016 | `feat: compara token actual y anterior en tiempo constante` | `nicomora70` | HU-15 | - | [ ] |
| I017 | `test: verifica autenticación y rotación del token IA` | `nicomora70` | HU-15 | - | [ ] |

### Fase 4: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 5: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 6: Pronóstico Chronos

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| I018 | `feat: adapta Chronos con ChronosForecasterAdapter` | `nicomora70` | HU-15 | P06 | [ ] |
| I019 | `feat: carga Chronos desde ModelRegistry` | `nicomora70` | HU-15 | P01 | [ ] |
| I020 | `feat: agrega ForecasterCreator para modelos Chronos` | `nicomora70` | HU-15 | P02 | [ ] |
| I021 | `feat: prepara series diarias con pandas` | `nicomora70` | HU-15 | - | [ ] |
| I022 | `feat: re-muestrea lecturas con agregación configurable` | `nicomora70` | HU-15 | P19 | [ ] |
| I023 | `feat: maneja huecos de series antes de inferir` | `nicomora70` | HU-15 | - | [ ] |
| I024 | `feat: exige mínimo configurable de puntos históricos` | `nicomora70` | HU-16 | - | [ ] |
| I025 | `feat: genera cuantiles p10, p50 y p90 con Chronos` | `nicomora70` | HU-15 | - | [ ] |
| I026 | `feat: implementa POST /v1/forecasts sin estado` | `nicomora70` | HU-15 | P06 | [ ] |
| I027 | `feat: valida orden y frecuencia diaria de resultados` | `nicomora70` | HU-15 | - | [ ] |
| I028 | `feat: identifica modelo y versión en la respuesta` | `nicomora70` | HU-15 | - | [ ] |
| I029 | `feat: informa INSUFFICIENT_HISTORY con HTTP 422` | `nicomora70` | HU-16 | - | [ ] |
| I030 | `feat: informa INVALID_SERIES con HTTP 422` | `nicomora70` | HU-15 | - | [ ] |
| I031 | `feat: agrega TimingForecaster para medir inferencia` | `nicomora70` | HU-15 | P09 | [ ] |
| I032 | `feat: agrega CachingForecaster para series repetidas` | `nicomora70` | HU-15 | P09 | [ ] |
| I033 | `test: verifica cuantiles, re-muestreo y fallos Chronos` | `nicomora70` | HU-15, HU-16 | P06 | [ ] |

### Fase 7: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 8: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 9: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 10: Backtest y evaluación

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| I034 | `feat: agrega RollingWindowIterator para backtest` | `nicomora70` | HU-33 | P14 | [ ] |
| I035 | `feat: implementa ForecastPipeline para ventanas temporales` | `nicomora70` | HU-33 | P20 | [ ] |
| I036 | `feat: calcula MAE y WQL en ventanas de backtest` | `nicomora70` | HU-33 | P20 | [ ] |
| I037 | `feat: calcula cobertura y ancho de intervalo` | `nicomora70` | HU-33 | P20 | [ ] |
| I038 | `feat: calcula skill frente a persistencia por horizonte` | `nicomora70` | HU-33 | P19 | [ ] |
| I039 | `feat: exporta resultados de backtest en JSON` | `nicomora70` | HU-33 | - | [ ] |
| I040 | `test: verifica métricas y cobertura del backtest` | `nicomora70` | HU-33 | P20 | [ ] |
| I041 | `docs: documenta ejecución reproducible del backtest` | `nicomora70` | HU-33 | - | [ ] |

### Fase 11: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 12: Endurecimiento y despliegue

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| I042 | `build: crea imagen Docker CPU para Hugging Face Spaces` | `nicomora70` | - | - | [ ] |
| I043 | `ci: agrega pip-audit y escaneo de dependencias` | `nicomora70` | - | - | [ ] |
| I044 | `ci: valida consumo de memoria del modelo en CPU` | `nicomora70` | - | - | [ ] |
| I045 | `docs: documenta despliegue y límites del servicio IA` | `nicomora70` | - | - | [ ] |

## `caudal-simulador`

### Fase 1: Fundaciones

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S001 | `build: crea paquete Python 3.12 con uv y Typer` | `nicomora70` | - | - | [ ] |
| S002 | `style: configura Ruff y mypy para el simulador` | `nicomora70` | - | - | [ ] |
| S003 | `ci: valida formato, tipos y pruebas del simulador` | `nicomora70` | - | - | [ ] |
| S004 | `test: configura pytest e Hypothesis` | `nicomora70` | - | - | [ ] |
| S005 | `feat: valida escenarios YAML con Pydantic` | `nicomora70` | - | P04 | [ ] |
| S006 | `feat: agrega SimulationClock con semilla reproducible` | `nicomora70` | - | P01 | [ ] |
| S007 | `build: ignora .env y directorio keys` | `nicomora70` | - | - | [ ] |

### Fase 2: Escenarios y generación inicial

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S008 | `feat: agrega ScenarioBuilder para parámetros YAML` | `nicomora70` | - | P04 | [ ] |
| S009 | `feat: modela clima húmedo y seco por mes` | `nicomora70` | - | P19 | [ ] |
| S010 | `feat: genera lluvia gamma con semilla estable` | `nicomora70` | - | P19 | [ ] |
| S011 | `feat: modela caudal de fuente con retardo de lluvia` | `nicomora70` | - | P19 | [ ] |
| S012 | `feat: simula balance de masa del tanque` | `nicomora70` | - | - | [ ] |
| S013 | `feat: simula demanda horaria por sector` | `nicomora70` | - | - | [ ] |
| S014 | `feat: simula turnos a partir del horario publicado` | `nicomora70` | HU-29 | - | [ ] |
| S015 | `feat: simula fugas con proceso Poisson` | `nicomora70` | HU-27 | - | [ ] |
| S016 | `feat: simula ruido y redondeo de lecturas humanas` | `nicomora70` | HU-11 | P19 | [ ] |
| S017 | `feat: simula duplicados y lecturas sin señal` | `nicomora70` | HU-10, HU-12 | P19 | [ ] |
| S018 | `feat: genera datos de terreno separados del backend` | `nicomora70` | HU-39 | - | [ ] |
| S019 | `feat: exporta datasets CSV y Parquet con manifiesto` | `nicomora70` | - | - | [ ] |
| S020 | `feat: agrega comando CLI generate` | `nicomora70` | - | - | [ ] |
| S021 | `test: verifica reproducibilidad y límites del tanque` | `nicomora70` | - | P01 | [ ] |

### Fase 3: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 4: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 5: Importación backfill

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S022 | `feat: adapta payloads con ApiPayloadAdapter` | `nicomora70` | HU-38 | P06 | [ ] |
| S023 | `feat: agrega cliente HTTP para la API de CAUDAL` | `nicomora70` | HU-38 | P06 | [ ] |
| S024 | `feat: valida destino demo antes de importar historial` | `nicomora70` | HU-38 | - | [ ] |
| S025 | `feat: implementa comando backfill de lecturas` | `nicomora70` | HU-38 | - | [ ] |
| S026 | `feat: implementa backfill de ejecuciones de turnos` | `nicomora70` | HU-38 | - | [ ] |
| S027 | `feat: implementa backfill de incidentes simulados` | `nicomora70` | HU-38 | - | [ ] |
| S028 | `feat: firma manifiesto de importación con Ed25519` | `nicomora70` | HU-38 | - | [ ] |
| S029 | `test: verifica conteos y rechazo de importación no demo` | `nicomora70` | HU-38 | - | [ ] |

### Fase 6: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 7: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 8: Sin trabajo de implementación asignado

Sin commits planeados en esta fase para este repositorio.

### Fase 9: Integración de datos de actas

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S030 | `feat: exporta ejecuciones confirmadas para actas` | `nicomora70` | HU-29, HU-30 | P06 | [ ] |
| S031 | `test: verifica consistencia de periodo en exportaciones` | `nicomora70` | HU-30 | - | [ ] |

### Fase 10: Backtest

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S032 | `feat: exporta ventanas históricas para el backtest IA` | `nicomora70` | HU-33 | P20 | [ ] |
| S033 | `test: verifica separación entre verdad y pronóstico` | `nicomora70` | HU-33 | - | [ ] |

### Fase 11: Simulador en vivo y dispositivos

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S034 | `feat: agrega ValveState para actuadores simulados` | `nicomora70` | HU-36 | P18 | [ ] |
| S035 | `feat: agrega SimulationEventBus` | `nicomora70` | - | P17 | [ ] |
| S036 | `feat: recorre eventos con EventScheduleIterator` | `nicomora70` | - | P14 | [ ] |
| S037 | `feat: crea dispositivos con DeviceCreator` | `nicomora70` | HU-34 | P02 | [ ] |
| S038 | `feat: ensambla sensores y actuadores con DeviceKitFactory` | `nicomora70` | HU-34 | P03 | [ ] |
| S039 | `feat: conecta ValveActuator con ActuatorDriver` | `nicomora70` | HU-36 | P07 | [ ] |
| S040 | `feat: agrega OpenValveCommand y CloseValveCommand` | `nicomora70` | HU-36 | P13 | [ ] |
| S041 | `feat: simula entrega y confirmación de comandos` | `nicomora70` | HU-36 | P18 | [ ] |
| S042 | `feat: implementa SimulationStep para avance temporal` | `nicomora70` | - | P20 | [ ] |
| S043 | `feat: guarda y restaura SimulationSnapshot` | `nicomora70` | - | P16 | [ ] |
| S044 | `feat: agrega NoisySensor para lecturas ruidosas` | `nicomora70` | HU-35 | P09 | [ ] |
| S045 | `feat: agrega StuckSensor para lecturas congeladas` | `nicomora70` | HU-35 | P09 | [ ] |
| S046 | `feat: agrega DriftingSensor para deriva gradual` | `nicomora70` | HU-35 | P09 | [ ] |
| S047 | `feat: agrega OfflineBufferTransport para cortes de red` | `nicomora70` | HU-10 | P11 | [ ] |
| S048 | `feat: publica API local del tablero con FastAPI` | `nicomora70` | - | - | [ ] |
| S049 | `feat: emite eventos del tablero mediante SSE` | `nicomora70` | - | P17 | [ ] |
| S050 | `feat: crea tablero Vite y React para modo live` | `nicomora70` | - | - | [ ] |
| S051 | `feat: agrega controles manuales de escenario en tablero` | `nicomora70` | - | - | [ ] |
| S052 | `feat: agrega modo live con cliente API firmado` | `nicomora70` | HU-35, HU-36 | P06 | [ ] |
| S053 | `feat: agrega modo demo con guion de fallos de campo` | `nicomora70` | HU-11, HU-16, HU-39 | P19 | [ ] |
| S054 | `feat: agrega escenarios seco, lluvioso y fugas frecuentes` | `nicomora70` | - | P04 | [ ] |
| S055 | `test: verifica idempotencia y eventos del modo live` | `nicomora70` | HU-35, HU-36 | P17 | [ ] |
| S056 | `test: verifica el guion demo con Hypothesis` | `nicomora70` | HU-11, HU-16, HU-39 | P19 | [ ] |
| S057 | `docs: documenta operación live y manejo de claves` | `nicomora70` | HU-34, HU-35 | - | [ ] |

### Fase 12: Endurecimiento y despliegue

| # | Mensaje del commit | Autor | HU | Patrón | Hecho |
|---|---|---|---|---|---|
| S058 | `ci: agrega pip-audit y escaneo de secretos` | `nicomora70` | - | - | [ ] |
| S059 | `build: crea imagen Docker para el tablero simulador` | `nicomora70` | - | - | [ ] |
| S060 | `docs: documenta escenarios y despliegue del simulador` | `nicomora70` | - | - | [ ] |

Relacionados: [Roles-y-planeacion.md](Roles-y-planeacion.md), [workflow.md](../.agents/workflow.md), [Historias-de-usuario.md](Historias-de-usuario.md)
