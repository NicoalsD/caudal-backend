# Glosario

Términos del dominio (institución, agua, válvulas, pronóstico) y técnicos (software, seguridad, IA) usados en la documentación de CAUDAL, en orden alfabético. La columna **En el código** muestra el identificador en inglés que usan el código, la base de datos o la API. Si todavía no hay identificador, aparece "por definir". Si el término ya no existe, no aparece aquí.

Los patrones de diseño (P01 a P23) están en [Patrones-de-diseno.md](Patrones-de-diseno.md). Aquí se explican los que aparecen en las historias y en los requisitos.

| Término | En el código | Definición |
|---|---|---|
| Abstract Factory (P03) | `DocumentFactory`, `DeviceKitFactory` | Crea familias de objetos relacionados. Ejemplo: documentos PDF y HTML para el acta. |
| Acceso por objeto | `institution_id`, RLS | Comprobación de que un recurso pertenece a la institución del usuario antes de responder. Un recurso de otra institución responde `404`. Ver [Actores-y-permisos.md](Actores-y-permisos.md), sección 4. |
| Access token | `access token`, claims `aud`, `iss`, `tv`, `institution_id` | Credencial de corta duración (15 minutos) que se envía en cada petición. Se guarda solo en la memoria del frontend. |
| Acta | `minutes`, `reporting.minutes` | Documento en PDF que se genera por periodo. Tiene cuatro secciones: observado, estimado, inferido y confirmado. Pasa de `DRAFT` a `FINAL`, y `FINAL` es inmutable. |
| Ack | `ACKED`, `POST /devices/commands/{id}/ack` | Confirmación del dispositivo de que ejecutó un comando de válvula. Sin ack, el comando no se da por cumplido. |
| Adapter (P06) | `IaForecastClientAdapter`, `DeviceTelemetryAdapter` | Traduce entre dos interfaces distintas, por ejemplo entre el modelo de dominio y la API de la IA. |
| Ángulo abierto y cerrado | `open_angle_deg`, `closed_angle_deg` | Posiciones del servo, en grados, que corresponden a la válvula abierta y a la cerrada. Se configuran por válvula. El firmware recibe el ángulo crudo. |
| Anomalía | `anomalies`, `kind` | Sospecha del sistema: `POSSIBLE_LEAK`, `ABNORMAL_DROP`, `SENSOR_FAULT` o `STALE_DATA`. Su estatus epistémico es `INFERRED`. Ver el evento correspondiente. |
| Append-only | `append-only` | Tabla en la que solo se inserta y se consulta. Nunca se actualiza ni se borra. Así funcionan las lecturas, los eventos de auditoría y el historial de comandos. |
| Argon2id | `Argon2PasswordEncoder` | Función de hash de contraseñas diseñada para resistir fuerza bruta. Parámetros en RNF-01. |
| Asistente | `caudal-ia` (`/v1/insights/*`), `source` | LLM compatible con OpenAI que responde preguntas en lenguaje natural y redacta reportes a partir del `outlook`, los eventos y el estado de las válvulas. Se muestra como "Asistente (modelo de lenguaje)". Si falla, responde con reglas y plantillas (`source = RULES`). No decide por sí mismo ni inventa cifras. Ver `Modulo-IA.md`. |
| Auditoría | `audit_log`, `audit.audit_log` | Registro de quién hizo qué, cuándo y con qué motivo. Tiene cadena de hashes anclada en `audit.audit_anchors`. |
| Banda Chronos | `forecast_points`, `p10`, `p50`, `p90`, `is_fallback` | Rango probable del nivel que devuelve el modelo Chronos, en pasos de `forecast_step_seconds` hasta `forecast_horizon_steps`. Es la tercera salida del pronóstico. Si el modelo no responde, se muestra la estimación simple. |
| Bridge (P07) | - | Eliminado con la página pública y las publicaciones. |
| Builder (P04) | `MinutesBuilder` | Construye un objeto complejo paso a paso. Ejemplo: un acta con sus secciones y sus datos. |
| Cadena de validación (P12) | por definir | Revisiones en cadena de una telemetría: distancia válida, eco presente, duplicado y salto brusco. Cada revisión decide si pasa a la siguiente o marca el dato. Ver Chain of Responsibility. |
| Calibración vacío y lleno | `empty_distance_mm`, `full_distance_mm`, `POST /tanks/{id}/calibration` | Dos puntos de referencia del sensor. `EMPTY` es la distancia del sensor al fondo; `FULL` es la distancia con el tanque lleno. El admin los fija con la lectura actual ("usar lectura actual como vacío/lleno"). |
| Canal (servo) | `servo_channel` | Número del canal del servo en la placa, que el puente usa en el comando `S<n> <deg>`. En la maqueta, la línea 1 usa el canal 1 y la línea 2 el canal 2. |
| Chain of Responsibility (P12) | por definir | Patrón de la validación de telemetría. La cadena de verificación de firmas de dispositivos sigue el mismo principio. |
| Chronos | `chronos-forecasting`, `ChronosForecasterAdapter` | Familia de modelos de series de tiempo que produce la banda del pronóstico. El modelo se elige por variable de entorno. Por defecto local: `autogluon/chronos-2-small`; el despliegue en Render usa `amazon/chronos-bolt-tiny`. Todos en CPU. |
| Cierre del día | `day_closures`, `day_closure` | Resumen del día que genera el sistema (aperturas y cierres por válvula con su origen, nivel mínimo y máximo, litros entregados estimados, eventos y observaciones), completado con las novedades del operador. Solo hay un cierre por día. |
| Circuit breaker | `CircuitBreakerState`: `Cerrado`, `Abierto`, `Semiabierto` | Patrón que deja de llamar a un servicio que falla, para no esperar en vano. Mientras está `Abierto`, el sistema usa el respaldo. |
| Cobertura | `coverage` | Porcentaje de valores reales que caen dentro del intervalo [p10, p90]. La meta es cerca del 80 %. |
| Command (P13) | `Command`, `CommandBus` | Acción encapsulada como objeto. Permite auditarla y reintentarla. |
| Comando de válvula | `devices.valve_commands`, `status`, `manual_reason`, `autopilot_decision_id` | Orden de abrir o cerrar una válvula. Su estado va de `PENDING` a `DELIVERED`, `ACKED`, `FAILED`, `EXPIRED` o `CANCELLED` (con motivo `SUPERSEDED` si un comando nuevo lo reemplaza). Su origen es manual (con motivo opcional) o del piloto (decisión registrada). Exactamente uno de los dos orígenes está presente. |
| Composite (P08) | por reubicar | Trataba sectores y válvulas como un árbol. Sin árbol de regiones, el patrón se reubica en la estructura de tanque, líneas y válvulas (por definir). |
| Confirmado | `CONFIRMED` | Estatus epistémico de lo verificado en campo, por ejemplo una orden de válvula con ack. |
| Cuantil | `p10`, `p50`, `p90` | Valor por debajo del cual cae un porcentaje de los casos. `p50` es el valor más probable; `p10` y `p90` forman el rango. Ejemplo: "lo más probable es 2,1 m, pero podría estar entre 1,8 y 2,4". |
| Cuenta | `users`, `iam.users` | Persona que inicia sesión. Pertenece a una sola institución y su nombre de usuario es único dentro de ella, `(institution_id, username)`. Si una persona necesita acceso a otra institución, se le crea otra cuenta. |
| Decorator (P09) | `AuthHttpClient`, `RetryHttpClient`, `LoggingHttpClient`, `AuditingCommandHandler` | Agrega comportamiento a un objeto sin cambiar su clase. Ejemplos: autenticación, reintentos y auditoría. |
| Dato viejo | `STALE`, `sensor_status = STALE` | Telemetría que no llegó dentro de `stale_after_seconds`. La pantalla lo avisa y no lo presenta como actual. |
| Dispositivo | `devices`, `device_keys` | Sensor, actuador o puente con clave Ed25519 propia. En la maqueta hay un `LEVEL_SENSOR` y dos `VALVE_ACTUATOR`. |
| Distancia | `distance_mm`, métrica `DISTANCE` | Distancia medida por el sensor, en milímetros crudos, desde el sensor hasta el agua. El backend la convierte en nivel. Sin eco (`-1`) se guarda como punto crudo, pero no produce dato de nivel. |
| Ed25519 | `Ed25519` | Algoritmo de firma de clave pública. Cada dispositivo firma sus peticiones con su clave privada, y el servidor verifica con la pública. |
| Escena 3D | por definir | Vista de la institución en three.js: tanque, líneas, flujo de agua y válvulas. La escala se toma de las medidas reales del tanque. |
| Estado del sensor | `sensor_status`: `OK`, `NO_ECHO`, `STALE` | Estado que devuelve `GET /tanks/{id}/live`. `NO_ECHO` es sin eco, `STALE` es dato viejo. |
| Estimación simple | `persistence`, `fallback_reason` | Pronóstico de persistencia: "seguirá igual que hoy", con un rango basado en los cambios históricos. Es el respaldo cuando la IA no responde (`is_fallback = true`) y la línea base para evaluar la IA. |
| Estimado | `ESTIMATED` | Estatus epistémico de lo calculado: tiempos hasta la reserva, litros entregados y bandas del pronóstico. |
| Evaluación | `forecast_evaluations`, `GET /forecast-evaluation` | Comparación de la IA contra la estimación simple con MAE, WQL, cobertura y skill por horizonte. |
| Evento | `GET /events`, `kind` | Hecho que el sistema registra y muestra aparte del pronóstico: anomalías, alertas de nivel bajo o crítico (código por definir), comandos fallidos y observaciones manuales. |
| Facade (P10) | `TankStatusFacade`, `CaudalApi` | Una entrada sencilla a varios subsistemas. |
| Factory Method (P02) | `DocumentGeneratorCreator`, `ForecasterCreator`, `DeviceCreator` | Delega en un creador la decisión de qué objeto crear. |
| Firma de petición | `X-Signature` | Firma Ed25519 sobre el método, la ruta, el timestamp, el nonce y el hash del cuerpo. Ver RNF-08. |
| FieldLimits | `FieldLimits` (clase Java) | Constantes con los límites técnicos y de seguridad de cada campo. Alimentan validaciones, la base de datos y el frontend. Ver RNF-04 y RNF-22. |
| Flyway | `db/migration`, `${person_name_max}` | Herramienta que aplica los cambios de esquema en orden y de forma versionada. Sus placeholders toman valores de `FieldLimits`. |
| Gemelo digital | `caudal-simulador` | Modelo que imita el comportamiento del tanque, el sensor y las válvulas para producir datos verosímiles. Sus valores reales nunca llegan al backend. |
| Hexagonal (arquitectura) | `api`, `application`, `domain`, `infrastructure` | Arquitectura en la que el dominio no depende de frameworks. Las entradas y salidas se conectan por puertos y adaptadores. |
| HMAC | `reporter_ip_hmac`, `last_login_ip_hmac` | Código de autenticación de mensaje con clave secreta. Así se guarda la IP para limitar abusos sin guardarla en claro. |
| Idempotencia | `Idempotency Key`, UUID del cliente | Propiedad por la que repetir una petición produce el mismo efecto que hacerla una sola vez. |
| Importación | `imports`, `sim.import_batches` | Carga de historial simulado (lecturas o eventos). Solo se permite en instituciones simuladas y la usa el superadmin (`IMPORT_SIMULATED`). |
| Inferido | `INFERRED` | Estatus epistémico de lo que se sospecha: las anomalías, hasta que se verifiquen en campo. |
| Institución | `institutions`, `org.institutions`, `institution_id` | Cliente de CAUDAL. Tiene un tanque, un sensor, líneas de servicio y válvulas. Sus datos están aislados de los de otras instituciones. Antes se llamaba acueducto. |
| Institución de prueba | `maqueta-caudal` | Institución no simulada, con datos de la maqueta real. Tiene dos líneas, "Acueducto 1" y "Acueducto 2". |
| Institución simulada | `org.institutions.is_simulated` | Institución cuyos datos son simulados por definición. Sus datos se marcan "Datos simulados" y es la única donde se permite la importación de historial. |
| Iterator (P14) | `RollingWindowIterator` | Recorre una colección sin exponer su estructura. Su uso tras el alcance B2B está por definir. |
| JWT | `JWT`, `HS256`, `Nimbus` | Formato de token firmado que contiene datos (claims) del usuario. CAUDAL lo firma con HS256 y lo valida con Spring Security. |
| Lectura efectiva | `ops.readings`, `origin = SENSOR` | Nivel que se guarda en la base de datos a partir de la telemetría, cada `persist_interval_seconds` o cuando el nivel cambia más de un umbral. Los puntos crudos van a `devices.telemetry_points`. |
| Límite de tasa | `rate limit`, `Bucket4j` | Número máximo de peticiones por unidad de tiempo. En dispositivos, 60 peticiones firmadas por minuto. En el directorio público y en el login, por IP (y por institución en el login). Ver RNF-03. |
| Línea de servicio | `org.service_lines`, `service_line` | Tramo que sale de una válvula y alimenta una zona de la institución. En la UI se llama "Acueducto N" y su nombre es editable. Cada línea tiene una válvula. Antes se llamaba sector. |
| LLM | `caudal-ia`, `nvidia/nemotron-3.5-super-vl:free` | Modelo de lenguaje que responde al asistente. Por defecto, Nemotron de NVIDIA, gratis en OpenRouter. Ver Asistente. |
| MAE | `mae` | Error absoluto medio: promedio de la diferencia entre el valor pronosticado y el real. Se calcula sobre el p50. |
| Maqueta | `caudal-maqueta` | Modelo físico a escala: un tanque, un HC-SR04 que mide la distancia, dos servos MG90S que pisan dos mangueras de suero y un Arduino UNO conectado por USB. Cada manguera simula una línea de servicio. |
| Maqueta virtual | `caudal-simulador` (dispositivo simulado) | Maqueta en software con el mismo protocolo firmado: envía telemetría, consulta `GET /devices/commands` y responde con el ack. Permite probar el lazo cerrado sin hardware. |
| Memento (P16) | `SimulationSnapshot` | Guarda el estado de una simulación para poder restaurarlo. |
| Mediator (P15) | por reubicar | Coordinaba un formulario de propuesta de turnos, que se elimina. Se reubica si un formulario de configuración lo necesita. |
| MFA (TOTP) | `mfa_factors`, `mfa_recovery_codes` | Segundo factor de autenticación con códigos de 6 dígitos que cambian cada 30 segundos. Opcional para el admin y la plataforma. |
| Modo del piloto | `autopilot_mode` (`org.institution_settings`), `MANUAL`, `ASSISTED`, `AUTOMATIC` | Nivel de automatización de una institución. `MANUAL` solo muestra recomendaciones; `ASSISTED` exige un clic del operador; `AUTOMATIC` ejecuta la recomendación si el operador no actúa en `operator_absence_minutes`. Lo configura el admin con `AUTOPILOT_CONFIGURE`. |
| Nivel útil | `empty_distance_mm - full_distance_mm` | Distancia entre el vacío y el lleno calibrados. Es el rango en el que el nivel varía de 0 % a 100 %. |
| Nonce | `X-Nonce`, `device_nonces` | Número usado una sola vez. Impide reenviar una petición firmada. CAUDAL lo exige en 128 bits en hexadecimal y lo recuerda 10 minutos. |
| Observación manual | `ops.observations`, `WATER_DIRTY`, `WATER_MUDDY`, `VISIBLE_DAMAGE`, `OTHER` | Nota que el operador registra a mano sobre el agua o el sistema: agua sucia, agua turbia, daño visible u otro. Se muestra en los eventos y alimenta el cierre del día. |
| Observado | `OBSERVED` | Estatus epistémico de lo que se midió o se vio directamente: lecturas del sensor, observaciones manuales y litros que quedan. |
| Observer (P17) | `DomainEventPublisher` | Notifica cambios a quien se suscribe. Ejemplo: el evento de un comando de válvula confirmado. |
| Outlook | `GET /tanks/{id}/outlook` | Las tres salidas separadas del pronóstico: agua que queda, tiempo hasta la reserva o vaciado, y banda Chronos. Cada una lleva su origen. |
| Piloto automático | `application/autopilot/`, `AUTOPILOT` | Componente del backend que recomienda y, en `AUTOMATIC`, ejecuta la acción de las válvulas. Tiene dos guardas: el piso de reserva y la prioridad humana. Su origen en los comandos es `AUTOPILOT`. |
| Piso de reserva | `reserve_floor_pct` | Nivel mínimo que el piloto nunca deja bajar con una maniobra automática. Es la guarda más fuerte del piloto. |
| Pronóstico | `forecast_runs`, `forecast_points`, `GET /tanks/{id}/outlook` | Estimación del nivel en tres salidas separadas. Ver Outlook. |
| Prototype (P05) | `InstitutionTemplate` | Crea una institución nueva a partir de una plantilla, con su tanque, sus líneas y su configuración. Se usa en el alta B2B. |
| Proxy (P11) | `PublicInstitutionDirectoryProxy`, `RemoteForecasterProxy` | Controla el acceso a otro objeto. `PublicInstitutionDirectoryProxy` entrega solo `{slug, name}`; `RemoteForecasterProxy` decide entre la IA y el respaldo. |
| Puente USB | `caudal-sim maqueta-usb` (en `caudal-simulador`) | Proceso local que habla con la placa Arduino UNO por serie (115200 baudios, con pyserial) y con el backend con firmas Ed25519. Lee `DISTANCE` del sensor, recoge comandos y reporta su ack. |
| RBAC | `roles`, `permissions`, `role_permissions` | Control de acceso basado en roles. Los permisos se asignan a los roles en la base de datos, y el código verifica códigos de permiso. |
| Recomendación | `recommendations`, `action`, `confidence` | Propuesta del piloto o del asistente para las válvulas: `action` (`OPEN`, `CLOSE`, `WAIT` o `INSPECT`), `valve_id` cuando aplica, `reason` y `confidence`. |
| Refresh token | `refresh_tokens`, `token_hash`, `revoked_reason` | Credencial opaca de larga duración que sirve para obtener un nuevo access token. Rota en cada uso y se guarda su hash SHA-256. |
| Reserva | `reserve_level_pct` | Nivel mínimo que la institución quiere conservar en el tanque. Es la referencia del tiempo hasta la reserva. Se configura en `org.institution_settings`. |
| RLS (Row Level Security) | `FORCE ROW LEVEL SECURITY`, `app.institution_id` | Seguridad de PostgreSQL que filtra filas según la sesión. CAUDAL la usa como segunda barrera por institución. |
| Semilla | `seed` | Datos iniciales cargados al crear el entorno. Incluye la institución de prueba `maqueta-caudal`. |
| Sensor ultrasónico | `HC-SR04`, `LEVEL_SENSOR` | Sensor que mide la distancia al agua por eco. En la maqueta mide desde arriba del tanque. |
| Servo | `VALVE_ACTUATOR`, `servo_channel` | Motor que gira la manguera (en la maqueta, un MG90S) para abrirla o cerrarla. Cada válvula motorizada tiene un servo. |
| Simulador | `caudal-simulador` | Repositorio que produce datos verosímiles de las instituciones simuladas, la maqueta virtual y el puente USB, con escenarios en YAML. |
| Skill (puntaje de mejora) | `skill` | Medida de cuánto mejora la IA frente a la estimación simple: `skill = 1 − MAE_IA / MAE_simple`, por horizonte. Un valor mayor que 0 de forma sostenida indica ayuda. |
| Skill (guía del asistente de desarrollo) | `.agents/` | Guía reutilizable que el asistente de desarrollo carga para una tarea. No forma parte del sistema CAUDAL. |
| State (P18) | `ValveCommandState`, `TankLevelState`, `CircuitBreakerState` | Cambia el comportamiento de un objeto según su estado y las transiciones permitidas. El ciclo del comando de válvula es el caso principal. |
| Strategy (P19) | `TankGeometry`, `CylinderGeometry`, `RectangularGeometry`, `TrendStrategy`, `Forecaster` | Intercambia algoritmos. `TankGeometry` convierte la distancia en nivel y volumen según la forma del tanque. |
| Template Method (P20) | `DocumentGenerator`, `ForecastPipeline`, `Backtest`, `SimulationStep` | Define el esqueleto de un proceso (por ejemplo, encabezado, secciones y firmas de un acta) y deja los pasos concretos a las subclases. |
| Telemetría | `DISTANCE`, `devices.telemetry_points` | Envío firmado de la medida del sensor, cada ~2 s. Es la fuente del nivel en vivo. |
| Tanque | `org.tanks`, `shape` | Depósito de agua de la institución. Su forma (`CYLINDER` o `RECTANGULAR`), sus medidas y su calibración viven en la tabla. |
| Tiempo hasta la reserva | por definir (campo del outlook) | Tiempo que falta para llegar a `reserve_level_pct` con la tasa de cambio actual (pendiente sobre `trend_window_minutes`). Es determinista, no es IA. Si el nivel sube o está estable, muestra "no se vacía al ritmo actual". |
| Tiempo hasta vaciarse | por definir | Tiempo que falta para que el tanque quede vacío con la tasa de cambio actual. Es determinista. |
| Token version | `token_version`, claim `tv` | Número que se incrementa al cambiar la contraseña, el rol o al desactivar la cuenta. Invalida los access tokens anteriores. |
| Válvula | `org.valves`, `is_motorized`, `fail_safe_position` | Llave que abre o cierra el paso de agua a una línea de servicio. Cada línea tiene una. Su posición segura ante fallas es `KEEP`, `OPEN` o `CLOSED`. |
| Válvula motorizada | `org.valves.is_motorized = true`, `servo_channel` | Válvula que el sistema opera con un servo. Las válvulas sin motor no reciben comandos. |
| Verdad de terreno | `ground truth` | Valores reales que produce el simulador (nivel real, fugas, consumo). Están en archivos aparte y nunca se envían al backend. |
| Visitor (P21) | `MinutesSectionVisitor` | Recorre registros y los clasifica sin cambiar sus clases. Aquí clasifica en observado, estimado, inferido y confirmado. |
| WQL (Weighted Quantile Loss) | `wql` | Pérdida cuantílica ponderada: mide el error de los tres cuantiles a la vez. Menor es mejor. |
| Zona ciega | `blind_zone_mm` | Distancia mínima que el sensor mide con fiabilidad. Una distancia menor no produce dato y cuenta para `SENSOR_FAULT`. |

Relacionados: [Caudal.md](Caudal.md) · [Requerimientos.md](Requerimientos.md) · [Actores-y-permisos.md](Actores-y-permisos.md) · [Historias-de-usuario.md](Historias-de-usuario.md) · [Reglas-de-negocio.md](Reglas-de-negocio.md)
