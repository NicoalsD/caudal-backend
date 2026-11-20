# Reglas de negocio

Este documento describe las reglas de negocio de CAUDAL en el modelo B2B. Cada institución tiene su tanque, su sensor ultrasónico, sus líneas de servicio y sus válvulas. Cada cifra indica el parámetro que la gobierna (sección 13).

Convenciones:

- **ejemplo**: valor para ilustrar el cálculo. No es configuración real.
- **por definir**: punto sin decisión. No se implementa sin confirmarlo.
- Los números en texto usan coma decimal (`2,1`). En JSON y en la base de datos el decimal es punto (`2.1`).
- Las horas se guardan en UTC (`timestamptz`) y se muestran en `America/Bogota`. Los ejemplos usan hora local.
- Los identificadores (tablas, columnas, enums, endpoints, permisos) van en inglés y en `código`.

## 1. Instituciones y aislamiento

Una **institución** es cliente de CAUDAL. Tiene un tanque, un sensor, líneas de servicio y válvulas. CAUDAL da de alta la institución y el administrador de la institución configura el resto.

### 1.1 Alta desde plantilla

- `PLATFORM_ADMIN` crea la institución con `platform.create_institution(...)`, una función `SECURITY DEFINER`.
- La creación copia una plantilla (patrón Prototype): tanque, líneas, válvulas y los parámetros de `org.institution_settings`.
- En la misma operación se crea el primer `INSTITUTION_ADMIN` y se dan de alta sus dispositivos.

### 1.2 Institución simulada

- Si `is_simulated = true`, todos sus datos son simulados. La UI lo marca "Datos simulados".
- Solo una institución simulada admite importar historial simulado (permiso `IMPORT_SIMULATED`, que usa el superadmin).
- Una institución no simulada no admite importación de historial.

### 1.3 Institución de prueba `maqueta-caudal`

- Es una institución no simulada. Sus datos vienen de la maqueta real.
- Hardware: un Arduino UNO por USB, un HC-SR04 (sensor de nivel) y dos servos MG90S que pisan dos mangueras de suero.
- Líneas: "Acueducto 1" (canal de servo 1) y "Acueducto 2" (canal de servo 2). Cada manguera simula un acueducto. Cada línea tiene una válvula motorizada.
- El tanque tiene las medidas de la maqueta (secciones 2 y 3).
- Usuarios locales: administrador, operador y lector. La contraseña la define el usuario y no se documenta aquí.

### 1.4 Login y aislamiento

- El usuario elige primero la institución: la lista pública `GET /api/v1/public/institutions` devuelve solo `{slug, name}`, o bien la ruta `/i/{slug}/login`.
- `POST /auth/login` recibe `{institution_slug, username, password, mfa_code?}`.
- El usuario es único por institución `(institution_id, username)`. Dos instituciones pueden tener un usuario `admin`.
- Cualquier fallo de login responde `INVALID_CREDENTIALS`. El error nunca dice si el usuario existe en otra institución.
- El superadmin entra con la opción "CAUDAL (plataforma)", con `institution_slug` nulo.
- Cada tabla de datos tiene RLS por `institution_id` (`audit.current_institution_id()`, con el claim `institution_id` del JWT). Un usuario que pide un recurso de otra institución recibe `404`.
- El JWT del superadmin lleva `scope=platform` y no lleva `institution_id`. Para configurar una institución recibe un token de 15 minutos con `act_as=platform`, y ese acceso queda auditado.

## 2. Geometría del tanque: distancia, nivel y volumen

El sensor mide una **distancia** en mm: desde el sensor hasta la superficie del agua. El backend la convierte en nivel y en volumen con la geometría de la institución. Cada forma tiene su clase (patrón Strategy: `CylinderGeometry` y `RectangularGeometry`).

### 2.1 Campos de `org.tanks`

| Campo | Significado |
|---|---|
| `shape` | `CYLINDER` o `RECTANGULAR` |
| `height_mm` | Altura interior del tanque |
| `diameter_mm` | Diámetro interior (solo cilindro) |
| `length_mm`, `width_mm` | Medidas de la base (solo rectangular) |
| `empty_distance_mm` | Distancia del sensor al fondo, con el tanque vacío |
| `full_distance_mm` | Distancia del sensor a la superficie, con el tanque lleno |
| `blind_zone_mm` | Zona ciega del sensor (sección 4) |

### 2.2 Fórmulas

```
área_mm2    = π · (diameter_mm / 2)²           (cilindro)
            = length_mm · width_mm             (rectangular)

útil_mm     = empty_distance_mm - full_distance_mm
level_mm    = clamp(empty_distance_mm - distance_mm, 0, útil_mm)
level_pct   = level_mm / útil_mm × 100
volume_l    = área_mm2 × level_mm / 10^6
```

Un litro son 10^6 mm³. Por eso se divide entre 10^6.

### 2.3 Ejemplo numérico: maqueta

Datos:

- `empty_distance_mm = 350` y `full_distance_mm = 80` (medidas de la maqueta).
- `height_mm = 300` (ejemplo).
- `diameter_mm = 200` (ejemplo). El diámetro real de la maqueta está **por definir**.

Cálculo con la lectura `distance_mm = 215`:

1. Rango útil: `350 - 80 = 270 mm`.
2. Nivel sin límites: `350 - 215 = 135 mm`. Está entre 0 y 270, así que `level_mm = 135 mm`.
3. `level_pct = 135 / 270 × 100 = 50,0 %`.
4. Área de la base: `π · (200 / 2)² = π · 10000 ≈ 31416 mm²`.
5. `volume_l = 31416 × 135 / 10^6 = 4,24 L`.

Otros casos con la misma maqueta:

| `distance_mm` | `level_mm` | `level_pct` | `volume_l` | Nota |
|---|---|---|---|---|
| 80 | 270 | 100,0 % | 8,48 | Tanque lleno |
| 215 | 135 | 50,0 % | 4,24 | Caso del ejemplo |
| 350 | 0 | 0,0 % | 0,00 | Tanque vacío |
| 400 | 0 | 0,0 % | 0,00 | Por debajo del fondo: `350 - 400 = -50`, que se limita a 0 |

Sensibilidad: con esta área, cada 10 mm de nivel equivalen a `31416 × 10 / 10^6 = 0,31 L`.

## 3. Calibración

### 3.1 Cómo se calibra

- En la vista del tanque, el administrador tiene dos botones: "usar lectura actual como vacío" y "usar lectura actual como lleno".
- Cada botón toma `distance_mm` de la lectura en vivo y lo guarda en `empty_distance_mm` o en `full_distance_mm` (`PATCH /api/v1/tanks/{id}`).
- Solo se calibra con una lectura cuyo `sensor_status` sea `OK` (sección 5). Con `NO_ECHO` o `STALE` el botón queda desactivado.
- Un cambio de calibración se aplica sin reiniciar nada. El puente recibe la calibración en `GET /api/v1/devices/config`.

### 3.2 Validaciones

| Regla | Ejemplo que pasa | Ejemplo que falla |
|---|---|---|
| `full_distance_mm < empty_distance_mm` | `80 < 350` | `full = 400` con `empty = 350` (400 no es menor que 350) |
| `empty_distance_mm <= height_mm + margen_de_montaje` | `350 <= 300 + 50` (margen de ejemplo 50 mm) | `empty = 360` (360 > 350) |

El margen de montaje (lo que sobresale el sensor sobre el borde del tanque) está **por definir**. Su valor de ejemplo es 50 mm.

Si una validación falla, la API responde `422` y no cambia nada.

### 3.3 Zona ciega

- `blind_zone_mm` es la distancia mínima que el sensor mide de forma confiable. Para la maqueta se usa 20 mm como ejemplo, a confirmar con la hoja de datos del HC-SR04.
- Una distancia menor que `blind_zone_mm` no produce nivel (sección 4).

## 4. Telemetría y lectura efectiva

### 4.1 Del sensor a la base

- El puente `maqueta-usb` envía la métrica `DISTANCE` en mm crudos cada ~2 s. Cada envío va firmado con Ed25519.
- El límite es de 60 peticiones firmadas por minuto por dispositivo (`RATE_LIMIT_TELEMETRY_PER_MINUTE`). A 2 s hay 30 por minuto, así que el límite se cumple.
- Cada punto crudo se guarda en `devices.telemetry_points`.
- La cadena de validación (sección 4.2) decide si el punto produce una lectura efectiva.
- La lectura efectiva se guarda en `ops.readings` cada `persist_interval_seconds`. También se guarda antes si el nivel cambia más de un umbral, que está **por definir** porque aún no existe en `institution_settings`.

Cálculo de volumen de filas: a 1 s habría 86400 puntos crudos al día; a 2 s, 43200. Con `persist_interval_seconds = 60` (ejemplo), la regla por tiempo guarda 1440 lecturas al día (`86400 / 60`). Los cambios por umbral suman filas adicionales.

### 4.2 Cadena de validación

Cada punto pasa por los validadores en este orden (patrón Chain of Responsibility).

| Orden | Validador | Condición | Efecto |
|---|---|---|---|
| 1 | Sin eco | `distance_mm = -1` | No crea lectura en `ops.readings` ni produce nivel. Cuenta para `SENSOR_FAULT`. |
| 2 | Zona ciega | `distance_mm < blind_zone_mm` | No produce nivel. Cuenta para `SENSOR_FAULT`. |
| 3 | Fuera de rango físico | `distance_mm` menor que 0 (distinto de -1) o mayor que el alcance del sensor (4000 mm en el HC-SR04) | Rechazo. No se guarda. |
| 4 | Futuro | `observed_at > ahora + 5 min` (constante de `FieldLimits`) | Rechazo. No se guarda. |
| 5 | Antigüedad | `observed_at < ahora - max_backdate_hours` | Rechazo (`TIMESTAMP_TOO_OLD`). No se guarda. Sirve para los lotes que el puente guardó sin conexión. |
| 6 | Duplicado por nonce | El `nonce` de la firma ya se usó dentro de `DEVICE_NONCE_TTL_SECONDS` (600 s) | Rechazo. Es una repetición del mismo envío. |

Ejemplos con `blind_zone_mm = 20`:

- `distance_mm = -1`: sin nivel, cuenta para `SENSOR_FAULT`.
- `distance_mm = 12`: sin nivel, cuenta para `SENSOR_FAULT`.
- `distance_mm = 215`: pasa todos los validadores y produce `level_mm = 135`.
- `distance_mm = 4200`: rechazo por fuera de rango.
- `observed_at` 10 minutos en el futuro: rechazo.
- El mismo `nonce` enviado dos veces: la segunda vez se rechaza.

## 5. Estado del tanque en vivo

`GET /api/v1/tanks/{id}/live` devuelve el estado a partir de la última lectura cruda válida.

| Campo | Significado |
|---|---|
| `distance_mm` | Distancia cruda del sensor |
| `level_mm`, `level_pct` | Nivel (sección 2) |
| `volume_l` | Litros (sección 2) |
| `observed_at` | Momento de la medida (UTC) |
| `age_seconds` | Segundos desde `observed_at` |
| `sensor_status` | `OK`, `NO_ECHO` o `STALE` |

Reglas de `sensor_status`, en este orden:

1. `STALE` si no llegó ningún punto en `stale_after_seconds` (ejemplo: 30 s).
2. `NO_ECHO` si el último punto fue `-1`. Una distancia en zona ciega tampoco produce nivel y se reporta como `NO_ECHO`.
3. `OK` en los demás casos.

Ejemplo de respuesta, con la lectura del ejemplo de la sección 2 (15:30 en Bogotá son las 20:30 UTC):

```json
{
  "distance_mm": 215,
  "level_mm": 135.0,
  "level_pct": 50.0,
  "volume_l": 4.24,
  "observed_at": "2026-11-20T20:30:00Z",
  "age_seconds": 2,
  "sensor_status": "OK"
}
```

La UI consulta este endpoint cada 2 s.

### 5.1 Tendencia

La tendencia es la pendiente, por mínimos cuadrados, de las lecturas efectivas de los últimos `trend_window_minutes` (ejemplo: 10 min). El eje X son minutos, con cero en la última lectura. La pendiente sale en mm/min.

Ejemplo, con tres lecturas efectivas:

| Minuto `x` | Nivel `y` (mm) |
|---|---|
| -4 | 143 |
| -2 | 139 |
| 0 | 135 |

```
x̄ = (-4 - 2 + 0) / 3 = -2
ȳ = (143 + 139 + 135) / 3 = 139

Σ (x - x̄)(y - ȳ) = (-2)(4) + (0)(0) + (2)(-4) = -16
Σ (x - x̄)²       = 4 + 0 + 4 = 8

pendiente = -16 / 8 = -2 mm/min
```

Lectura: el nivel baja 2 mm/min, es decir, unos 0,06 L/min (`2 × 0,0314`). Si la pendiente es negativa, el tanque baja. Si es positiva, sube. Si está cerca de cero, está estable. El umbral de "estable" está **por definir**.

## 6. Pronóstico: tres salidas separadas

`GET /api/v1/tanks/{id}/outlook` devuelve tres salidas separadas. Cada una tiene su origen (`OBSERVED` o `ESTIMATED`). Si `sensor_status` no es `OK`, las salidas se calculan con el último dato válido y muestran su `age_seconds`.

### 6.1 Agua que queda (`OBSERVED`)

Litros y porcentaje, calculados con la geometría (sección 2) a partir de la última lectura válida. No hay predicción.

Ejemplo: `4,24 L` y `50,0 %`.

### 6.2 Tiempo hasta la reserva y hasta vaciarse (`ESTIMATED`, determinista)

Usa la pendiente de la sección 5.1 y se calcula sin modelo. Parámetros:

- `reserve_level_pct` (ejemplo: 20 %). Se convierte en mm: `reserve_mm = reserve_level_pct / 100 × útil_mm`.
- Si la pendiente es cero o positiva, la salida dice "no se vacía al ritmo actual".
- Si `level_mm <= reserve_mm`, la salida dice "ya está en reserva".
- En otro caso:

```
tiempo_a_reserva = (level_mm - reserve_mm) / |pendiente|
tiempo_a_vaciarse = level_mm / |pendiente|
```

Ejemplo con `level_mm = 135`, `útil_mm = 270`, `reserve_level_pct = 20` (ejemplo) y pendiente `-2 mm/min`:

- `reserve_mm = 20 / 100 × 270 = 54 mm` (`54 × 0,0314 = 1,70 L`).
- `tiempo_a_reserva = (135 - 54) / 2 = 40,5 min`.
- `tiempo_a_vaciarse = 135 / 2 = 67,5 min`.

La reserva del pronóstico (`reserve_level_pct`) no es el piso del piloto (`reserve_floor_pct`, sección 10). Son parámetros distintos.

Limitación: la salida supone que el ritmo de bajada se mantiene. No considera válvulas que se abrirán o cerrarán después.

### 6.3 Banda del modelo Chronos (`ESTIMATED`)

- El servicio de pronóstico (`caudal-ia`) recibe la serie remuestreada cada `forecast_step_seconds` (ejemplo: 300 s) y devuelve la banda p10, p50 y p90 para cada paso.
- La historia enviada son `forecast_context_steps` pasos (ejemplo: 288, que son 24 h).
- El horizonte son `forecast_horizon_steps` pasos (ejemplo: 12, que es 1 h).
- La banda se expresa en `level_mm` y en `level_pct`, los dos en la respuesta del outlook.
- Si `caudal-ia` no responde dentro de `IA_READ_TIMEOUT_MS` (10 s), la salida usa una estimación simple (la misma pendiente de la sección 5.1). Se marca con `is_fallback = true` y se indica el motivo.

Los eventos (sección 7) y las recomendaciones del piloto (sección 10) se muestran aparte de estas tres salidas.

## 7. Anomalías y eventos

Todos los tiempos de esta sección están en segundos, porque el sensor envía cada ~2 s. Las anomalías son sospechas inferidas por reglas. Empiezan en `OPEN`.

| `kind` | Condición | Parámetro |
|---|---|---|
| `SENSOR_FAULT` | Sin eco o valor pegado durante la ventana, con alguna válvula abierta. Con todas las válvulas cerradas, un valor estable es normal. | `sensor_stuck_window_seconds` (ejemplo: 120 s, 60 lecturas a 2 s) |
| `STALE_DATA` | Sin telemetría durante más de `stale_after_seconds` | `stale_after_seconds` (ejemplo: 30 s) |
| `ABNORMAL_DROP` | El nivel baja más rápido que `max_level_change_per_minute`, en mm/min | `max_level_change_per_minute` (ejemplo: 2 mm/min) |
| `POSSIBLE_LEAK` | El nivel baja con todas las válvulas cerradas, más que la tolerancia | Tolerancia de caída, **por definir** (ejemplo: 3 mm) |

Ejemplos:

- `ABNORMAL_DROP`: con tendencia de `-3 mm/min` y límite de 2 mm/min, `3 > 2`, así que se crea la anomalía. Con `-2 mm/min` no se crea, porque no supera el límite.
- `POSSIBLE_LEAK`: todas las válvulas están cerradas y el nivel baja 5 mm entre dos lecturas. Como 5 mm supera la tolerancia de ejemplo (3 mm), se crea la anomalía.

Ciclo de vida: `OPEN` pasa a `CONFIRMED` (un `OPERATOR` o un `INSTITUTION_ADMIN` la confirma en campo, con su nombre y una nota, con el permiso `ANOMALY_REVIEW`) o a `DISMISSED`. La nota es opcional y su regla está **por definir**.

Eventos (no son anomalías):

| Evento | Condición | Parámetro |
|---|---|---|
| Alerta de nivel bajo | `level_pct < low_level_pct` | `low_level_pct` (ejemplo: 30 %) |
| Alerta de nivel crítico | `level_pct < critical_level_pct` | `critical_level_pct` (ejemplo: 10 %) |
| Comando fallido | Un comando de válvula termina en `FAILED` o `EXPIRED` (sección 9) | Sin parámetro |
| Observación manual | Un operador registra una observación (sección 8) | Sin parámetro |

## 8. Observaciones manuales

El operador anota lo que ve en campo. Una observación no es una medida del sensor.

- Tipos: `WATER_DIRTY` (agua sucia), `WATER_MUDDY` (agua turbia), `VISIBLE_DAMAGE` (daño visible) y `OTHER` (otro).
- Quién: quien tenga el permiso `OBSERVATION_CREATE` (por defecto, `OPERATOR`). El `VIEWER` solo lee.
- Texto: de 10 a 500 caracteres (`FieldLimits`).
- Se crea con `POST /api/v1/observations`. La fecha la asigna el servidor al recibirla. Si se acepta la hora del cliente, está **por definir**.
- Queda como evento `OBSERVED`. Aparece en la línea de tiempo del tanque, en el cierre del día (sección 11) y en las actas como dato observado (sección 12).

## 9. Válvulas y comandos

### 9.1 Origen

Cada comando tiene un solo origen:

- **Manual**: `POST /api/v1/valve-commands/manual`, con el permiso `VALVE_COMMAND_MANUAL` (`OPERATOR` e `INSTITUTION_ADMIN`). El motivo (`manual_reason`) es opcional. Si falta, se guarda el texto por defecto "Operación manual desde la app", así que todo comando manual tiene motivo.
- **Piloto**: el comando lleva `autopilot_decision_id` (sección 10).

La restricción de la base de datos es `manual_reason XOR autopilot_decision_id`: exactamente uno de los dos no es nulo.

### 9.2 Vigencia

- `not_before`: el comando no se entrega antes de este momento.
- `expires_at`: si llega este momento sin entregarse, el comando pasa a `EXPIRED`. Por defecto es el momento de la creación más 2 minutos.

### 9.3 Un solo comando activo por válvula

Un comando está activo si está en `PENDING` o `DELIVERED`. Si llega un comando nuevo para la misma válvula mientras hay uno activo, el nuevo reemplaza al anterior: el anterior pasa a `CANCELLED` con motivo `SUPERSEDED`.

### 9.4 Máquina de estados

Estados: `PENDING`, `DELIVERED`, `ACKED`, `FAILED`, `EXPIRED` y `CANCELLED`. `ACKED`, `FAILED`, `EXPIRED` y `CANCELLED` son finales.

| Desde | Hacia | Qué lo provoca |
|---|---|---|
| `PENDING` | `DELIVERED` | El puente lo recoge con `GET /api/v1/devices/commands` (sondeo cada 2 s) |
| `PENDING` | `EXPIRED` | Pasa `expires_at` sin entrega |
| `PENDING` | `CANCELLED` | Llega un comando nuevo para la misma válvula (motivo `SUPERSEDED`) |
| `DELIVERED` | `CANCELLED` | Llega un comando nuevo para la misma válvula (motivo `SUPERSEDED`) |
| `DELIVERED` | `ACKED` | El Arduino responde `DONE` y el puente confirma con `POST /api/v1/devices/commands/{id}/ack` |
| `DELIVERED` | `FAILED` | El puente o el Arduino informan un error. La forma de reportarlo está **por definir** |
| `DELIVERED` | `EXPIRED` | No llega el ack antes de `expires_at`. Por confirmar |

Ejemplo: abrir "Acueducto 1" (canal 1, ángulo abierto de ejemplo 90°).

| Hora | Paso | Estado | Dato |
|---|---|---|---|
| 10:00:00 | El operador pulsa Abrir | `PENDING` | `origin = manual`, `expires_at` a las 10:02:00 |
| 10:00:02 | El puente recoge el comando y envía `S1 90` por serie | `DELIVERED` | |
| 10:00:03 | El Arduino responde `DONE` y el puente hace el ack | `ACKED` | `GET /api/v1/valves/live` muestra `OPEN` |

El ack llega en 3 s, dentro del objetivo de unos 5 s.

### 9.5 Posición de seguridad y ángulos

Cada válvula está en `org.valves` con:

- `is_motorized` y `servo_channel` (canal del servo en el Arduino; en la maqueta, 1 y 2).
- `open_angle_deg` y `closed_angle_deg`. En el ejemplo, 90° y 0°. Los ángulos reales de la maqueta están **por definir** con la prueba de banco.
- `hold_torque_after_move` (booleano, por defecto `true`): si es `false`, el puente envía `R<n>` al recibir `DONE` y el servo queda suelto. Motivo: en el banco, un servo sostenido a 0° contra la manguera siguió esforzándose.
- `fail_safe_position`: la posición a la que se lleva el actuador si el sistema pierde el control. Su valor (`OPEN` o `CLOSED`) y su disparador están **por definir**.

El puente recibe canal, ángulos y `hold_torque_after_move` en `GET /api/v1/devices/config`. El firmware recibe ángulos crudos y no conoce las palabras "abierto" o "cerrado".

### 9.6 Estado en vivo

`GET /api/v1/valves/live` devuelve `OPEN`, `CLOSED` o `UNKNOWN` para cada válvula. El estado sale del último comando `ACKED` de esa válvula:

- `OPEN` si fue un comando de apertura.
- `CLOSED` si fue un comando de cierre.
- `UNKNOWN` si la válvula no tiene ningún ack.

Un comando `FAILED` o `EXPIRED` no cambia el estado.

`ACKED` confirma que el actuador respondió. No confirma que el agua haya fluido.

## 10. Piloto automático y asistente

### 10.1 Modos

El modo está en `institution_settings.autopilot_mode`:

- `MANUAL`: el piloto no envía comandos. Solo opera el personal.
- `ASSISTED`: el piloto propone una recomendación y un operador confirma el comando.
- `AUTOMATIC`: el piloto envía los comandos que decide, respetando las guardas de la sección 10.2.

Las recomendaciones son `OPEN`, `CLOSE`, `WAIT` e `INSPECT`. Las arman reglas deterministas sobre el outlook, los eventos y el estado de las válvulas; el LLM no genera recomendaciones. Cada decisión se guarda en `ops.autopilot_decisions` con sus entradas (nivel, tendencia, anomalías abiertas, modo) y el comando que generó, si lo hubo.

### 10.2 Guardas

1. **Piso de reserva** (`reserve_floor_pct`, ejemplo 15 %). Si `level_pct` está por debajo del piso, el piloto no recomienda ni envía `OPEN` en ninguna línea. Ejemplo: nivel de 12 %, por debajo de 15 %, no permite `OPEN`. Si una válvula ya está abierta, la recomendación es `CLOSE`.
2. **Prioridad humana** (`operator_absence_minutes`, ejemplo 30 min). Mientras una persona haya enviado un comando a una válvula hace menos de este tiempo, el piloto no cambia esa válvula y recomienda `WAIT`. Ejemplo: el operador abre "Acueducto 1" a las 10:00. A las 10:20 el piloto recomienda `WAIT` (20 min < 30 min). A partir de las 10:30 puede actuar (30 min cumplidos).

Las demás reglas que eligen entre `OPEN`, `WAIT` e `INSPECT` están **por definir** en el backend.

### 10.3 Asistente (modelo de lenguaje)

- Responde preguntas en lenguaje natural y redacta reportes. Permiso: `ASSISTANT_USE`.
- No decide, no envía comandos y no inventa cifras. Recibe `outlook`, eventos y estado de las válvulas, y sus respuestas usan esos datos.
- Preguntas sugeridas: "¿cuánta agua queda?", "¿cuándo llega a la reserva?" y "¿qué pasó hoy?".
- Ejemplo: "¿cuánta agua queda?" responde "Quedan 4,24 L (50,0 %)", que son los datos de la salida 6.1.
- Si el modelo de lenguaje falla o supera `INSIGHTS_READ_TIMEOUT_MS` (20 s), el asistente responde con un texto de respaldo por reglas, con los mismos datos.

## 11. Cierre del día

- Un cierre por día y por tanque. Una vez cerrado, no se vuelve a cerrar.
- El sistema genera un resumen automático del día con:
  - aperturas y cierres por válvula, con su origen (manual o piloto);
  - nivel mínimo y máximo, en mm y en %;
  - litros entregados estimados (el método de cálculo está **por definir**);
  - eventos (sección 7) y observaciones (sección 8).
- El operador agrega sus novedades y cierra el día.
- El cierre alimenta las actas (sección 12).

Ejemplo: "Acueducto 1" se abrió a las 10:00 con comando manual y se cerró a las 10:40. El resumen dice "abierta 40 min, origen manual".

## 12. Actas

### 12.1 Estatus epistémico

Cada registro cae en una sola sección del acta.

| Sección | Qué datos van | Por qué |
|---|---|---|
| **Observado** | Lecturas efectivas, agua que queda (salida 6.1), observaciones manuales, novedades del operador | Lo que alguien midió o vio |
| **Estimado** | Banda Chronos (salida 6.3), tiempos hasta la reserva y hasta vaciarse (salida 6.2), litros entregados estimados del cierre | Lo que se calculó |
| **Inferido** | Anomalías en `OPEN` o `DISMISSED`, recomendaciones del piloto | Lo que se sospecha |
| **Confirmado** | Comandos `ACKED`, anomalías `CONFIRMED` por el operador en campo | Lo que se verificó |

Reglas:

- Un registro con dos interpretaciones va a la sección más conservadora.
- `ACKED` confirma que el actuador respondió. El acta lo describe así, sin afirmar que el agua fluyó.

### 12.2 Estados del acta

- `DRAFT`: se puede regenerar. Se actualizan `generated_by` y `generated_at`.
- `FINAL`: inmutable. Al cerrarla se guardan `finalized_by`, `finalized_at`, `pdf_sha256` y `audit_head_hash` (ancla de la cadena de auditoría).
- La generación requiere el permiso `MINUTES_GENERATE`. Los roles que lo tienen están **por definir**.
- El periodo y el alcance de cada acta (por institución, por día o por semana) están **por definir**.

## 13. Parámetros que gobiernan cada número

| Número del documento | Parámetro | Dónde vive | Valor de la maqueta o de ejemplo |
|---|---|---|---|
| Distancias del sensor | `empty_distance_mm`, `full_distance_mm` | `org.tanks` | 350 y 80 (maqueta) |
| Zona ciega | `blind_zone_mm` | `org.tanks` | 20 mm (ejemplo) |
| Altura y diámetro | `height_mm`, `diameter_mm`, `length_mm`, `width_mm`, `shape` | `org.tanks` | 300 mm y 200 mm (ejemplo); diámetro real por definir |
| Margen de montaje | Margen de la validación de calibración | Por definir | 50 mm (ejemplo) |
| Canal, ángulos y posición de seguridad | `servo_channel`, `open_angle_deg`, `closed_angle_deg`, `fail_safe_position` | `org.valves` | Canales 1 y 2; ángulos 90° y 0° (ejemplo) |
| Líneas de servicio | `name` y su válvula | `org.service_lines` | "Acueducto 1" y "Acueducto 2" |
| Reserva del pronóstico | `reserve_level_pct` | `org.institution_settings` | 20 % (ejemplo) |
| Alertas de nivel | `low_level_pct`, `critical_level_pct` | `org.institution_settings` | 30 % y 10 % (ejemplo) |
| Dato viejo | `stale_after_seconds` | `org.institution_settings` | 30 s (ejemplo) |
| Persistencia de lecturas | `persist_interval_seconds` | `org.institution_settings` | 60 s (ejemplo) |
| Antigüedad máxima de una medida importada | `max_backdate_hours` | `org.institution_settings` | 48 h (ejemplo) |
| Umbral de cambio para persistir | Por definir | Por definir | Por definir |
| Caída anormal | `max_level_change_per_minute` | `org.institution_settings` | 2 mm/min (ejemplo) |
| Sensor pegado | `sensor_stuck_window_seconds` | `org.institution_settings` | 120 s (ejemplo) |
| Tendencia | `trend_window_minutes` | `org.institution_settings` | 10 min (ejemplo) |
| Umbral de "estable" | Por definir | Por definir | Por definir |
| Paso del pronóstico | `forecast_step_seconds` | `org.institution_settings` | 300 s (ejemplo) |
| Horizonte del pronóstico | `forecast_horizon_steps` | `org.institution_settings` | 12 pasos, es decir 1 h (ejemplo) |
| Historia enviada al pronóstico | `forecast_context_steps` | `org.institution_settings` | 288 pasos, es decir 24 h (ejemplo) |
| Tolerancia de fuga | Por definir | Por definir | 3 mm (ejemplo) |
| Piso de reserva del piloto | `reserve_floor_pct` | `org.institution_settings` | 15 % (ejemplo) |
| Prioridad humana | `operator_absence_minutes` | `org.institution_settings` | 30 min (ejemplo) |
| Modo del piloto | `autopilot_mode` | `org.institution_settings` | `MANUAL`, `ASSISTED` o `AUTOMATIC` |
| Vigencia de comandos | `expires_at` | `devices.valve_commands` | Creación más 2 min por defecto |
| Servo sostenido o suelto | `hold_torque_after_move` | `org.valves` | `true` (sostenido) |
| Tolerancia de 5 minutos de futuro | Constante de la regla de telemetría | `FieldLimits` (constante) | 5 min |
| Alcance del sensor | Constante física del HC-SR04 | Constante con nombre en `co.caudal.shared` (por definir) | 4000 mm |
| Textos de observaciones (10 a 500) | Límites de texto | `FieldLimits` | 10 a 500 caracteres |
| Límite de telemetría | `RATE_LIMIT_TELEMETRY_PER_MINUTE` | Variable de entorno | 60 por minuto por dispositivo |
| Ventana de nonces | `DEVICE_NONCE_TTL_SECONDS` | Variable de entorno | 600 s |
| Tiempo de espera de la IA | `IA_READ_TIMEOUT_MS`, `INSIGHTS_READ_TIMEOUT_MS` | Variables de entorno | 10000 ms y 20000 ms |

Lo que no está en esta tabla se define antes de implementarlo: el margen de montaje, el umbral de persistencia, el umbral de estable, la tolerancia de fuga, la regla de cada recomendación del piloto y el método de litros entregados.

Relacionados: [API.md](API.md), [Configuracion-sin-valores-quemados.md](Configuracion-sin-valores-quemados.md), [Contrato-IA.md](Contrato-IA.md), [Protocolo-de-dispositivos.md](Protocolo-de-dispositivos.md), [Modelo-de-datos.md](Modelo-de-datos.md), [Diccionario-de-datos.md](Diccionario-de-datos.md)
