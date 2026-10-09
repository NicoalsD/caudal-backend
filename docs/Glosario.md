# Glosario

Términos del dominio (acueducto, agua, Junta) y técnicos (software, seguridad, IA) usados en la documentación de CAUDAL, en orden alfabético. La columna **En el código** muestra el identificador en inglés que usan el código, la base de datos o la API. Si no hay identificador, aparece un guion.

Los patrones de diseño (P01 a P23) están en la sección 16 de los hechos canónicos. Aquí se explican los que aparecen en las historias y en los requisitos.

| Término | En el código | Definición |
|---|---|---|
| Abstract Factory (P03) | `DocumentFactory`, `DeviceKitFactory` | Crea familias de objetos relacionados. Ejemplo: documentos PDF y HTML para cartel, acta y resumen. |
| Acceso por objeto | `aqueduct_id`, RLS | Comprobación de que un recurso pertenece al acueducto del usuario antes de responder. Ver [Actores-y-permisos.md](Actores-y-permisos.md), sección 4. |
| Access token | `access token`, claims `aud`, `iss`, `tv` | Credencial de corta duración (15 minutos) que se envía en cada petición. Se guarda solo en la memoria del frontend. |
| Acta | `minutes`, `reporting.minutes` | Documento en PDF que la Junta genera por periodo. Tiene cuatro secciones: observado, estimado, inferido y confirmado. Pasa de `DRAFT` a `FINAL`, y `FINAL` es inmutable. |
| Acueducto | `aqueducts`, `org.aqueducts` | Organización que distribuye agua en una o varias veredas. Cada dato pertenece a un acueducto. |
| Acueducto demo | `aqueducts.is_demo` | Acueducto de prueba, simulado por definición. Es el único donde se permite la importación de historial simulado. Sus datos se marcan como "Datos simulados". |
| Acueducto veredal | - | Acueducto que abastece una vereda rural, normalmente por gravedad y con tanque de almacenamiento. Es el caso de Guaitarilla. |
| Adapter (P06) | `IaForecastClientAdapter`, `DeviceTelemetryAdapter` | Traduce entre dos interfaces distintas, por ejemplo entre el modelo de dominio y la API de la IA. |
| Anomalía | `anomalies`, `kind` | Sospecha del sistema: `POSSIBLE_LEAK`, `SENSOR_FAULT`, `STALE_DATA`, `SUSPECTED_DUPLICATE`, `ABNORMAL_DROP`. Empieza en `OPEN`, y la Junta la confirma o la descarta. Su estatus epistémico es `INFERRED`. |
| Append-only | `append-only` | Tabla en la que solo se inserta y se consulta. Nunca se actualiza ni se borra. Así funcionan las lecturas, las correcciones y el historial de estados. |
| Argon2id | `Argon2PasswordEncoder` | Función de hash de contraseñas diseñada para resistir fuerza bruta. Parámetros en RNF-01. |
| Auditoría | `audit_log`, `audit.audit_log` | Registro de quién hizo qué, cuándo y con qué motivo. Tiene cadena de hashes anclada en `audit.audit_anchors`. |
| Banda de nivel | `rule_level_bands`, `HIGH`, `LOW`, `CRITICAL` | Rango de nivel del tanque con sus horas de servicio por día (`daily_service_hours`) y si solo atiende lo prioritario (`priority_only`). Las bandas de una regla no se solapan y cubren todo su rango. |
| Bridge (P07) | `Notice`, `PublicationChannel` | Separa el aviso del canal por donde sale: página pública, WhatsApp o cartel. |
| Builder (P04) | `ScheduleProposalBuilder`, `MinutesBuilder` | Construye un objeto complejo paso a paso. Ejemplo: una propuesta con sus turnos y explicaciones. |
| Bypass | - | Válvula manual en paralelo a la motorizada. Permite operar a mano aunque el hardware falle. Es parte de la propuesta de hardware. |
| Cadena de validación | `ReadingValidationHandler` | Secuencia de revisiones de una lectura: rango, fecha, duplicado, salto brusco y antigüedad. Ver Chain of Responsibility (P12). |
| Chain of Responsibility (P12) | `ReadingValidationHandler` | Cada revisión decide si la lectura pasa a la siguiente o se queda. La cadena de verificación de firmas de dispositivos sigue el mismo principio (HU-35). |
| Chronos | `chronos-forecasting`, `ChronosForecasterAdapter` | Familia de modelos de series de tiempo, usada para el pronóstico. El modelo se elige por variable de entorno. Por defecto: `autogluon/chronos-2-small` o `amazon/chronos-bolt-small`, en CPU. |
| Cierre del día | `day_closures`, `day_closure` | Registro de qué pasó con los turnos de un día: cumplidos, a medias, no ejecutados y novedades. Solo hay un cierre por día. |
| Circuit breaker | `CircuitBreakerState`: `Cerrado`, `Abierto`, `Semiabierto` | Patrón que deja de llamar a un servicio que falla, para no esperar en vano. Mientras está `Abierto`, el sistema usa el respaldo. Puede apoyarse en Resilience4j (verificar). |
| Cobertura | `coverage` | Porcentaje de valores reales que caen dentro del intervalo [p10, p90]. La meta es cerca del 80 %. Un intervalo demasiado estrecho o demasiado ancho indica mala calibración. |
| Command (P13) | `Command`, `CommandBus`, `ApproveProposalCommand` | Acción encapsulada como objeto. Permite auditarla y reintentarla. |
| Composite (P08) | `NetworkNode`, `SectorNode`, `ValveNode` | Trata sectores y válvulas como un árbol con la misma interfaz. |
| Confirmado | `CONFIRMED` | Estatus epistémico de lo verificado en campo: ejecuciones de turnos e incidentes verificados. |
| Corrección | `reading_corrections`, `correction.reason` | Registro nuevo que apunta a una lectura original y explica por qué cambia. La original no se modifica. |
| CSP (Content-Security-Policy) | `Content-Security-Policy` | Cabecera que limita de dónde puede cargar recursos el navegador. Ver RNF-06. |
| Cuantil | `p10`, `p50`, `p90` | Valor por debajo del cual cae un porcentaje de los casos. `p50` es el valor más probable; `p10` y `p90` forman el rango. Ejemplo: "lo más probable es 2,1, pero podría estar entre 1,8 y 2,4". |
| Cuenta | `users`, `iam.users` | Persona que inicia sesión. No pertenece por sí misma a un acueducto: la pertenencia está en la membresía. |
| Daño (reporte de) | `incidents`, `DAMAGE_CATEGORY` | Reporte de fuga, tubo roto, falta de agua, agua sucia o falla de válvula. Puede venir del público, del fontanero, de una anomalía o de una importación. |
| Dato viejo | `stale`, `STALE` | Lectura cuya antigüedad supera el máximo de la regla. La pantalla lo avisa y no lo presenta como actual. |
| Decorator (P09) | `AuthHttpClient`, `RetryHttpClient`, `LoggingHttpClient`, `AuditingCommandHandler` | Agrega comportamiento a un objeto sin cambiar su clase. Ejemplos: autenticación, reintentos y auditoría. |
| Dispositivo | `devices`, `device_keys` | Sensor, válvula o puerta de enlace con clave Ed25519. En esta versión solo existe en el simulador. |
| Dotación | - | Consumo de agua por persona y por día. Para Guaitarilla se usa la dotación de referencia del RAS 0330 de 2017, para más de 2.000 metros sobre el nivel del mar. Supuesto por verificar. |
| Duplicado | `DUPLICATE` | Lectura con el mismo valor y el mismo tanque dentro de la ventana de duplicados. Queda marcada, no borrada. |
| Ed25519 | `Ed25519` | Algoritmo de firma de clave pública. Cada dispositivo firma sus peticiones con su clave privada, y el servidor verifica con la pública. |
| Estado del tanque | `tanks/{id}/status`, `TankLevelState` | Último nivel, antigüedad, tendencia y banda del tanque. Las bandas son Alto, Bajo y Crítico. |
| Estimación simple | `persistence`, `fallback_reason` | Pronóstico de persistencia: "seguirá igual que hoy", con un rango basado en los cambios diarios históricos. Es el respaldo cuando la IA no responde (`is_fallback = true`) y la línea base para evaluar la IA. Corre en sombra junto a la IA con `is_fallback = false` y `fallback_reason = SHADOW_BASELINE`. |
| Estimado | `ESTIMATED` | Estatus epistémico de lo calculado: pronósticos y horas disponibles. |
| Evaluación | `forecast_evaluations`, `GET /forecast-evaluation` | Comparación de la IA contra la estimación simple con MAE, WQL, cobertura y skill por horizonte. |
| Facade (P10) | `TankStatusFacade`, `CaudalApi` | Una entrada sencilla a varios subsistemas. |
| Factory Method (P02) | `DocumentGeneratorCreator`, `ForecasterCreator`, `DeviceCreator` | Delega en un creador la decisión de qué objeto crear. |
| FieldLimits | `FieldLimits` (clase Java) | Constantes con los límites técnicos y de seguridad de cada campo. Alimentan validaciones, la base de datos y el frontend. Ver RNF-04 y RNF-22. |
| Firma de petición | `X-Signature` | Firma Ed25519 sobre el método, la ruta, el timestamp, el nonce y el hash del cuerpo. Ver RNF-08. |
| Flyway | `db/migration`, `${person_name_max}` | Herramienta que aplica los cambios de esquema en orden y de forma versionada. Sus placeholders toman valores de `FieldLimits`. |
| Fontanero | `OPERATOR` | Persona que cuida el tanque y las válvulas en campo. Registra lecturas y cierra el día. |
| Gemelo digital | `caudal-simulador` | Modelo que imita el comportamiento real del sistema (lluvia, fuente, tanque, consumo, fugas, sensores) para producir datos. Sus valores reales nunca llegan al backend. |
| Hexagonal (arquitectura) | `api`, `application`, `domain`, `infrastructure` | Arquitectura en la que el dominio no depende de frameworks. Las entradas y salidas se conectan por puertos y adaptadores. |
| HMAC | `reporter_ip_hmac`, `last_login_ip_hmac` | Código de autenticación de mensaje con clave secreta. Así se guarda la IP para limitar abusos sin guardarla en claro. |
| Honeypot | - | Campo oculto en un formulario que las personas no llenan. Si un bot lo llena, el envío se descarta. |
| Idempotencia | `Idempotency Key`, UUID del cliente | Propiedad por la que repetir una petición produce el mismo efecto que hacerla una sola vez. Las lecturas usan el UUID generado en el celular. |
| Importación | `imports`, `sim.import_batches` | Carga de historial simulado (lecturas, ejecuciones o incidentes) en el acueducto demo. |
| Inferido | `INFERRED` | Estatus epistémico de lo que se sospecha: anomalías. Sigue siendo inferido hasta que se verifique en campo. |
| Iterator (P14) | `SectorTurnIterator`, `RollingWindowIterator`, `EventScheduleIterator` | Recorre una colección sin exponer su estructura. `SectorTurnIterator` recorre los sectores: primero los prioritarios y luego el de mayor espera. |
| Junta | `BOARD_ADMIN`, `BOARD_MEMBER` | Órgano de decisión del acueducto. El presidente o administrador (`BOARD_ADMIN`) administra usuarios, activa reglas y tiene permisos completos. `BOARD_MEMBER` crea borradores de reglas y decide propuestas. |
| JWT | `JWT`, `HS256`, `Nimbus` | Formato de token firmado que contiene datos (claims) del usuario. CAUDAL lo firma con HS256 y lo valida con Spring Security. |
| Lectura | `readings`, `gauge_value` | Registro de un nivel del tanque tomado por el fontanero (o por un sensor). Guarda la regla pintada, el estado del agua y las notas. |
| Límite de tasa | `rate limit`, `Bucket4j` | Número máximo de peticiones por unidad de tiempo, por IP, usuario, dispositivo o acueducto. Ver RNF-03. |
| MAE | `mae` | Error absoluto medio: promedio de la diferencia entre el valor pronosticado y el real. Se calcula sobre el p50. |
| Mediator (P15) | `ProposalEditorMediator` | Coordina varios componentes de un formulario y valida reglas entre ellos. Ejemplo: el total de horas contra las horas de la banda. |
| Membresía | `memberships`, `iam.memberships` | Vínculo entre un usuario y un acueducto, con un rol y una vigencia (`valid_from`, `valid_to`). Sin membresía vigente no hay permisos. |
| Memento (P16) | `ProposalMemento`, `SimulationSnapshot` | Guarda el estado de un objeto para poder restaurarlo. En CAUDAL guarda la propuesta original antes de los cambios de la Junta. |
| MFA (TOTP) | `mfa_factors`, `mfa_recovery_codes` | Segundo factor de autenticación con códigos de 6 dígitos que cambian cada 30 segundos. Opcional para la Junta administradora y el equipo. |
| Nonce | `X-Nonce`, `device_nonces` | Número usado una sola vez. Impide reenviar una petición firmada. CAUDAL lo exige en 128 bits en hexadecimal y lo recuerda 10 minutos. |
| Observado | `OBSERVED` | Estatus epistémico de lo que se vio directamente: lecturas y reportes de la comunidad. |
| Observer (P17) | `DomainEventPublisher`, `SyncQueueStore`, `ConnectivityMonitor` | Notifica cambios a quien se suscribe. Ejemplos: los eventos `HorarioPublicado` y `ReglasActivadas`. |
| Offline (sin conexión) | `OfflineDatabase`, `SubmitReadingCommand` | Modo en el que la app guarda acciones en el celular y las envía después. Ver RNF-14. |
| Prioritario | `priority_only`, `PRIORITY_SECTOR` | Sector que recibe agua aunque el tanque esté en banda crítica (por ejemplo, la escuela). Su orden se fija en la regla. |
| Pronóstico | `forecast_runs`, `forecast_points`, `GET /tanks/{id}/forecasts/latest` | Estimación del nivel del tanque para 1 a 3 días, con p10, p50 y p90. Puede venir de la IA o de la estimación simple. |
| Propuesta | `schedule_proposals` | Conjunto de turnos calculado por el sistema, pendiente de la decisión de la Junta. Nace en `PENDING_REVIEW` (`DRAFT` es interno). Sus estados son `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `APPROVED_WITH_CHANGES`, `REJECTED`, `PUBLISHED` y `CLOSED`. Los sectores sin turno quedan en `unserved_sectors` con su motivo. |
| Prototype (P05) | `RuleSet.copyForNewVersion()` | Crea un objeto nuevo a partir de otro existente. Se usa para crear un borrador de reglas desde la versión vigente. |
| Proxy (P11) | `PublicScheduleProxy`, `RemoteForecasterProxy` | Controla el acceso a otro objeto. `PublicScheduleProxy` quita datos personales; `RemoteForecasterProxy` decide entre la IA y el respaldo. |
| Publicación | `publications`, `POST /schedule-proposals/{id}/publish` | Acto por el que un horario aprobado pasa a verse en la página pública. Solo se publica lo aprobado. |
| RBAC | `roles`, `permissions`, `role_permissions` | Control de acceso basado en roles. Los permisos se asignan a los roles en la base de datos, y el código verifica códigos de permiso. |
| Refresh token | `refresh_tokens`, `token_hash`, `revoked_reason` | Credencial opaca de larga duración (7 días, o 30 para `OPERATOR`) que sirve para obtener un nuevo access token. Rota en cada uso y se guarda su hash SHA-256. |
| Regla | `rule_sets`, `rule_sector_settings` | Conjunto de parámetros que define la Junta: rango del tanque, bandas, reserva, horario, sectores prioritarios, orden de válvulas y límites de turnos. Tiene versiones; solo una está `ACTIVE`. |
| Regla pintada | `gauge_value`, `gauge_min`, `gauge_max` | Escala de números pintada en el tanque. El fontanero anota el número que ve. En la semilla, la regla va de 0 a 5. También se llama limnimétrica. |
| Reserva | `reserve_level`, `reserve_policy`, `RESERVE_GUARD` | Nivel mínimo que el tanque debe conservar. Si el p10 del pronóstico del día cae por debajo, se aplica `reserve_policy`: `NONE`, `REDUCE_TO_CRITICAL_BAND` (por defecto) o `PRIORITY_ONLY`. |
| Resumen | `summary_shares`, `MONTHLY_SUMMARY` | Vista limitada de la gestión del acueducto para una entidad de apoyo, con vigencia obligatoria. |
| RLS (Row Level Security) | `FORCE ROW LEVEL SECURITY`, `app.aqueduct_id` | Seguridad de PostgreSQL que filtra filas según la sesión. CAUDAL la usa como segunda barrera por acueducto. |
| Sector | `sectors`, `sector.name` | Zona del acueducto que recibe agua por una o varias válvulas. Puede ser prioritario. |
| Semilla | `seed` | Datos iniciales cargados al crear el entorno. Los valores de ejemplo (16 h, 8 h, 3 h) solo existen en la semilla. |
| Simulador | `caudal-simulador` | Repositorio que produce datos verosímiles de la región, del hardware y de la comunidad, con escenarios en YAML. |
| Sincronización | `sync_batches`, `POST /readings/batch` | Envío de las lecturas acumuladas al volver la señal. La respuesta indica el resultado de cada lectura. |
| Skill (puntaje de mejora) | `skill` | Medida de cuánto mejora la IA frente a la estimación simple: `skill = 1 − MAE_IA / MAE_simple`, por horizonte. Un valor mayor que 0 de forma sostenida indica ayuda. |
| Skill (guía del asistente) | `.agents/` | Guía reutilizable que el asistente de desarrollo carga para una tarea. No forma parte del sistema CAUDAL. |
| State (P18) | `ProposalState`, `TankLevelState`, `CircuitBreakerState`, `ConnectionState`, `ValveState` | Cambia el comportamiento de un objeto según su estado y las transiciones permitidas. |
| Strategy (P19) | `TurnAllocationStrategy`, `TrendStrategy`, `Forecaster` | Intercambia algoritmos. Ejemplo: asignación por prioridad y mayor espera, o reparto equitativo (`EQUAL_SPLIT`). |
| Supersedes | `supersedes_id` | Referencia a un registro que este reemplaza. Así se corrigen los turnos sin editarlos. |
| Template Method (P20) | `DocumentGenerator`, `ForecastPipeline`, `Backtest`, `SimulationStep` | Define el esqueleto de un proceso (por ejemplo, encabezado, secciones y firmas) y deja los pasos concretos a las subclases. |
| Token version | `token_version`, claim `tv` | Número que se incrementa al cambiar la contraseña, el rol o al desactivar la cuenta. Invalida los access tokens anteriores. |
| Turno | `schedule_items`, `shift_executions` | Período en que un sector recibe agua. Tiene hora de inicio y fin, y se publica dentro de una propuesta. |
| Turno cumplido, a medias, no ejecutado | `COMPLETED`, `PARTIAL`, `NOT_EXECUTED` | Resultado de un turno registrado en el cierre del día. |
| Válvula | `valves`, `valve.code`, `fail_safe_position` | Llave que abre o cierra el paso de agua a un sector. Tiene una posición segura ante fallas (`KEEP`, `OPEN` o `CLOSED`). |
| Ventana de duplicados | nombre por definir | Tiempo dentro del que un valor igual al anterior se marca como duplicado. Se configura en la regla. |
| Ventana de operación | `rule_operating_windows` | Horario en el que se puede operar el sistema de agua. Los turnos caen dentro de él. |
| Verdad de terreno | `ground truth` | Valores reales que produce el simulador (nivel real, fugas, demanda). Están en archivos aparte y nunca se envían al backend. |
| Visitor (P21) | `MinutesSectionVisitor` | Recorre registros y los clasifica sin cambiar sus clases. Aquí clasifica en observado, estimado, inferido y confirmado. |
| WCAG 2.1 AA | - | Pautas de accesibilidad web del W3C, nivel AA. Ver RNF-13. |
| WhatsApp (mensaje) | `whatsapp-text` | Texto generado con el horario. El botón lo copia; el sistema no lo envía. |
| WQL (Weighted Quantile Loss) | `wql` | Pérdida cuantílica ponderada: mide el error de los tres cuantiles a la vez. Menor es mejor. |

Relacionados: [Caudal.md](Caudal.md) · [Requerimientos.md](Requerimientos.md) · [Actores-y-permisos.md](Actores-y-permisos.md) · [Historias-de-usuario.md](Historias-de-usuario.md)
