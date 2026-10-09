# Protocolo de dispositivos

Este documento define cómo se comunican los dispositivos de campo (sensores de nivel, actuadores de válvula y gateways) con `caudal-backend`. Cubre el registro y las claves, la firma de cada petición, la validación en el servidor, la telemetría, los comandos de válvula y el comportamiento ante fallas.

Los dispositivos son simulados por ahora (`caudal-simulador`). El hardware real está documentado como propuesta en los hechos (sección 6) y no se construye. Todo lo que aquí se describe debe funcionar igual con el simulador y con hardware real.

Fuente de las reglas: hechos canónicos, secciones 11 (dispositivos), 13 (endpoints) y 17 (estatus y reglas). Lo que no está en esas secciones se marca "propuesta" o "por definir".

## 1. Registro de dispositivos y claves

### 1.1 Alta de un dispositivo

1. Un usuario con permiso `DEVICE_REGISTER` (`BOARD_ADMIN` o `PROJECT_TEAM`) llama a `POST /api/v1/devices` con `kind`, `name` y el tanque o la válvula a la que pertenece (`devices.devices`).
2. El dispositivo nace en estado `ACTIVE`, pero sin clave. Sin clave no puede firmar nada, así que no acepta peticiones.
3. El dispositivo genera su par de claves Ed25519 y su clave privada nunca sale de él.
4. El usuario registra la clave pública con `POST /api/v1/devices/{id}/keys`.

### 1.2 Formato de la clave pública

| Atributo | Valor |
|---|---|
| Algoritmo | Ed25519 |
| Tamaño de la clave pública | 32 bytes |
| Codificación en la API y en la BD | base64url sin relleno (`=`): 43 caracteres |
| Columna | `devices.device_keys.public_key` (`varchar(64)`, `UNIQUE`) |
| Huella | SHA-256 de los 32 bytes crudos de la clave, en hexadecimal minúsculas (64 caracteres) |
| Columna de la huella | `devices.device_keys.fingerprint` (`char(64)`, `UNIQUE`) |

Validación al registrar: la cadena decodifica a exactamente 32 bytes. Si no, `422 INVALID_KEY`. La huella se calcula en el servidor, no la envía el dispositivo.

Clave privada: nunca llega al servidor, ni se guarda en git ni en logs. En el simulador vive en `.env` o en un directorio de claves que `.gitignore` excluye. En hardware real, en memoria protegida del microcontrolador (por definir según el ESP32-S3).

### 1.3 Rotación

Regla de la BD: un dispositivo tiene una sola clave vigente, la de `revoked_at IS NULL` (índice único parcial, propuesta).

Procedimiento:

| Paso | Quién | Acción |
|---|---|---|
| 1 | Dispositivo | Genera un par nuevo y lo tiene listo, sin usarlo |
| 2 | Usuario con `DEVICE_REGISTER` | `POST /devices/{id}/keys` con la clave nueva |
| 3 | Servidor | En una transacción: registra la clave nueva y deja la anterior válida 7 días (`DEVICE_KEY_ROTATION_DAYS`); al vencer, se revoca (`revoked_at`). Inserta `DEVICE_KEY_ROTATED` en la auditoría (propuesta) |
| 4 | Dispositivo | Empieza a firmar con la clave nueva |

Entre los pasos 3 y 4 el dispositivo puede seguir firmando con la clave anterior, que sigue válida 7 días. No hay corte de servicio.

Revocación por compromiso: `DELETE /api/v1/devices/{id}/keys/{keyId}` revoca una clave sin reemplazo (hace `UPDATE` de `revoked_at`). Para suspender un dispositivo, `PATCH /api/v1/devices/{id}` con `status = SUSPENDED`.

## 2. Firma de cada petición

Solo las rutas de dispositivos (`/api/v1/devices/*`) usan firma. Las demás rutas usan JWT.

### 2.1 Cabeceras

| Cabecera | Formato | Ejemplo |
|---|---|---|
| `X-Device-Id` | UUID en texto | `0190f3a2-9999-7000-8000-000000000001` |
| `X-Timestamp` | Entero: segundos desde 1970-01-01T00:00:00Z, sin ceros a la izquierda | `1791538200` |
| `X-Nonce` | 32 caracteres hexadecimales minúsculos (128 bits, aleatorios) | `3f9a1c4e7b2d408f9e6a5c1b2d3e4f50` |
| `X-Signature` | Firma Ed25519 de 64 bytes, en base64url sin relleno: 86 caracteres | `<86 caracteres>` |

Cada cabecera debe aparecer una sola vez. Si aparece repetida, `401 INVALID_SIGNATURE`. Si su formato no es válido (no es entero, no es hex de 32, no decodifica), `400 VALIDATION_ERROR`.

### 2.2 Cadena canónica

La cadena canónica es la entrada exacta de la firma. Tiene cinco líneas, separadas por el carácter LF (`0x0A`), sin LF final:

```
METODO
RUTA
TIMESTAMP
NONCE
SHA256_CUERPO
```

| Línea | Regla | Ejemplo |
|---|---|---|
| `METODO` | Método HTTP en mayúsculas, tal como se envía | `POST` |
| `RUTA` | Ruta con prefijo `/api/v1`, sin esquema ni host. Incluye la query string exacta si la hay (las rutas de dispositivos no usan query). | `/api/v1/devices/telemetry` |
| `TIMESTAMP` | Igual al valor de `X-Timestamp`, sin cambios | `1791538200` |
| `NONCE` | Igual al valor de `X-Nonce`, en minúsculas | `3f9a1c4e7b2d408f9e6a5c1b2d3e4f50` |
| `SHA256_CUERPO` | SHA-256 de los bytes crudos del cuerpo, en hexadecimal minúsculas (64 caracteres). Si el cuerpo está vacío, es el hash de la cadena vacía. | `<64 caracteres>` |

Hash de cuerpo vacío (para `GET /devices/commands`):

```
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

Reglas de codificación:

- La cadena se codifica en UTF-8. Todas las líneas excepto el cuerpo son ASCII.
- Los bytes del cuerpo se hashean tal como viajan por la red, sin reformatear el JSON. El dispositivo firma el JSON que envía, byte a byte.
- Se firma la cadena completa, sin hash previo (Ed25519 firma el mensaje completo).

Ejemplo de cadena canónica para `POST /devices/telemetry` (los valores son ficticios):

```
POST
/api/v1/devices/telemetry
1791538200
3f9a1c4e7b2d408f9e6a5c1b2d3e4f50
<sha256 en hexadecimal del cuerpo, 64 caracteres>
```

La firma es `Ed25519.sign(clave_privada, bytes_UTF8_de_la_cadena)` codificada en base64url sin relleno.

### 2.3 Vectores de prueba

Los vectores (cadena, clave pública, firma esperada y resultado) se generan en la Fase 11, cuando el simulador tenga las claves de prueba. Se guardan en el repositorio de pruebas del backend y en el simulador, para que ambos lados verifiquen la misma firma.

| ID | Descripción | Cadena canónica | Clave pública | Firma esperada | Resultado esperado |
|---|---|---|---|---|---|
| SV-01 | Telemetría válida | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 200 |
| SV-02 | Cuerpo alterado un byte | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 401 `INVALID_SIGNATURE` |
| SV-03 | Nonce repetido | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 401 `NONCE_REPLAY` |
| SV-04 | Timestamp a 301 s | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 401 `TIMESTAMP_OUT_OF_WINDOW` |
| SV-05 | Clave revocada | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 401 `INVALID_SIGNATURE` |
| SV-06 | Dispositivo suspendido | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 403 `DEVICE_INACTIVE` |
| SV-07 | Cuerpo de 256 KiB más 1 byte | Se genera en la Fase 11 | Se genera en la Fase 11 | Se genera en la Fase 11 | 413 `PAYLOAD_TOO_LARGE` |

## 3. Validación en el servidor

### 3.1 Orden

Los pasos se ejecutan en este orden. Al primer fallo se responde y se detiene el proceso.

| Paso | Validación | Si falla | Notas |
|---|---|---|---|
| 0 | Tamaño del cuerpo: no más de 256 KiB en telemetría ni 64 KiB en el resto | `413 PAYLOAD_TOO_LARGE` | Se aplica en el filtro de transporte antes de leer el cuerpo (`Content-Length` y límite de flujo). El paso 6 la repite en la capa de aplicación. |
| 1 | Dispositivo activo: existe `X-Device-Id` y su estado es `ACTIVE` | No existe: `401 INVALID_SIGNATURE`. `SUSPENDED` o `RETIRED`: `403 DEVICE_INACTIVE` | Un dispositivo inexistente responde igual que una firma incorrecta, para no revelar qué identificadores existen. |
| 2 | Clave vigente: existe una clave con `revoked_at IS NULL` | `401 INVALID_SIGNATURE` | La firma se verifica solo con esta clave. |
| 3 | Ventana de tiempo: `abs(ahora - X-Timestamp) <= 300` segundos | `401 TIMESTAMP_OUT_OF_WINDOW` | Tolerancia de ±300 segundos (hechos, sección 11). |
| 4 | Nonce no visto: no existe `device_nonces` con ese dispositivo y nonce con `seen_at` dentro de 10 minutos | `401 NONCE_REPLAY` | Solo consulta. No inserta todavía. |
| 5 | Firma: `Ed25519.verify(clave_vigente, cadena_canónica, firma)` | `401 INVALID_SIGNATURE` | Solo después de verificar la firma se registra el nonce. |
| 5b | Registro del nonce: `INSERT` en `device_nonces`. Si ya existe (carrera entre dos peticiones iguales), `401 NONCE_REPLAY`. | `401 NONCE_REPLAY` | Se hace en la misma transacción que el procesamiento. |
| 6 | Tamaño del cuerpo, verificación en la aplicación | `413 PAYLOAD_TOO_LARGE` | Repetición del paso 0 para peticiones sin `Content-Length`. |
| 7 | Límite de tasa: 60 peticiones por minuto por dispositivo | `429 RATE_LIMITED` | Va después de la firma para que un atacante sin clave no consuma el cupo de un dispositivo real. |
| 8 | Validación del cuerpo (esquema, límites, puntos) | `400 VALIDATION_ERROR` | Ver `API.md`, sección 16. |

Desvíos respecto al orden pedido, y sus motivos:

- Nonce: el paso 4 solo consulta y el paso 5b lo inserta después de verificar la firma. Si se insertara antes, un atacante sin clave podría llenar la tabla con nonces falsos.
- Tamaño: se aplica antes de leer el cuerpo (paso 0). Así no se hashea un cuerpo gigante para luego rechazarlo.

### 3.2 Eventos de seguridad

Cada fallo de validación de dispositivos se registra en `iam.security_events` con `device_id` (si se conoce), `ip_hmac`, `request_id` y `details` (sin firma ni nonce completos).

| Situación | `type` | `severity` | `details.reason` |
|---|---|---|---|
| Firma inválida o clave ausente | `INVALID_SIGNATURE` | `MEDIUM` | `BAD_SIGNATURE` o `NO_ACTIVE_KEY` |
| Ventana de tiempo fuera de rango | `INVALID_SIGNATURE` | `LOW` | `TIMESTAMP_OUT_OF_WINDOW` |
| Nonce repetido | `NONCE_REPLAY` | `HIGH` | Sin detalle adicional |
| Dispositivo suspendido o retirado | `PERMISSION_DENIED` | `MEDIUM` | `DEVICE_INACTIVE` (propuesta: el catálogo de tipos no tiene uno propio) |
| Límite de tasa | `RATE_LIMITED` | `LOW` | `device` |
| Diez o más firmas inválidas del mismo dispositivo en 10 minutos | `INVALID_SIGNATURE` | `HIGH` | `REPEATED` (propuesta) |

## 4. Telemetría

### 4.1 `POST /api/v1/devices/telemetry`

Cuerpo: `points`, lista de 1 a 100 puntos. Cada punto:

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `id` | UUID | Sí | Lo genera el dispositivo. Es la clave de idempotencia y el `id` de `devices.telemetry_points`. |
| `metric` | texto | Sí | `LEVEL`, `BATTERY_V`, `RSSI` o `VALVE_POSITION` |
| `value` | número | Sí | Finito. `numeric(10,3)`: hasta 3 decimales. Para `LEVEL`, hasta 2 decimales (ver 4.3). |
| `observed_at` | fecha y hora | Sí | ISO-8601 con zona. Momento de la medición en el dispositivo. |

Ejemplo (valores ficticios):

```json
{
  "points": [
    { "id": "0190f3a2-6666-7000-8000-000000000001", "metric": "LEVEL", "value": 2.10, "observed_at": "2026-10-08T18:00:00-05:00" },
    { "id": "0190f3a2-6666-7000-8000-000000000002", "metric": "BATTERY_V", "value": 12.4, "observed_at": "2026-10-08T18:00:00-05:00" }
  ]
}
```

Respuesta 200:

```json
{
  "accepted": 1,
  "duplicates": 0,
  "rejected": 1,
  "results": [
    { "id": "0190f3a2-6666-7000-8000-000000000001", "status": "ACCEPTED", "reading_id": "0190f3a2-6666-7000-8000-000000000001", "validation_status": "ACCEPTED" },
    { "id": "0190f3a2-6666-7000-8000-000000000002", "status": "ACCEPTED", "reading_id": null, "validation_status": null }
  ]
}
```

Estados por punto:

| `status` | Significado |
|---|---|
| `ACCEPTED` | Guardado. Si es `LEVEL`, generó una lectura (`reading_id`). |
| `DUPLICATE` | Ya existía un punto con ese `id`. Mismo contenido: no se guarda otra vez. |
| Error por punto (`error`) | No se guarda. Trae `code` (por ejemplo `GAUGE_OUT_OF_RANGE`, `FUTURE_TIMESTAMP`, `TOO_OLD`, `VALIDATION_ERROR`). El resto del lote sigue. |

Un `id` repetido con contenido distinto se rechaza con `code = CONFLICT`. La idempotencia se cumple solo para contenido igual.

### 4.2 Almacenamiento

- Todos los puntos se guardan en `devices.telemetry_points` (append-only), también los de métricas que no son nivel.
- `telemetry_points.reading_id` apunta a la lectura creada, si la hubo.
- La retención de estos datos sigue `audit.retention_policies` con la clase `TELEMETRY_POINTS`.

### 4.3 Conversión de telemetría de nivel a lectura

Solo los puntos `metric = LEVEL` generan lecturas. Pasos:

1. Si `value` tiene más de 2 decimales o no está en el rango del tanque del dispositivo (`tanks.gauge_min` a `gauge_max`): el punto no se guarda, su elemento devuelve `error` con `code = GAUGE_OUT_OF_RANGE` o `VALIDATION_ERROR`, y no genera lectura.
2. Si pasa: se crea una fila en `ops.readings` con los siguientes valores:

| Campo de `readings` | Valor |
|---|---|
| `id` | El mismo `id` del punto (propuesta: así la conversión es idempotente) |
| `tank_id` | `devices.devices.tank_id` del sensor |
| `gauge_value` | `value` |
| `water_appearance_code` | `NOT_OBSERVED` (Sin observar). Un sensor no ve el aspecto del agua. Ver nota. |
| `damage_noticed` | `false` |
| `note` | `NULL` |
| `observed_at` | `observed_at` del punto |
| `received_at` | Hora de recepción en el servidor |
| `source` | `SENSOR` |
| `recorded_by` | `NULL` (no hay persona) |
| `device_id` | El sensor |
| `sync_batch_id` | `NULL` |
| `rule_set_id` | Versión vigente al procesar |
| `validation_status` | Resultado de la cadena de validación |

3. Se aplica la misma cadena de validación que a una lectura manual (`Reglas-de-negocio.md`, sección 8). Los puntos de un sensor con datos atrasados pasan por `TOO_OLD` si superan `max_backdate_days`, igual que cualquier lectura.
4. Se actualiza `telemetry_points.reading_id`.

Nota: `readings.water_appearance_code` es obligatorio. El catálogo `WATER_APPEARANCE` de la semilla incluye `NOT_OBSERVED` (Sin observar), que es el valor de las lecturas de sensor.

## 5. Comandos de válvula

### 5.1 Generación

Los comandos solo se generan de dos maneras:

| Origen | Condición | `schedule_item_id` | `manual_reason` | `created_by` |
|---|---|---|---|---|
| Turno | La propuesta está en `PUBLISHED` (aprobada y publicada) | Obligatorio | Nulo | Nulo (lo generó el sistema) |
| Manual | `POST /valve-commands/manual` por `BOARD_ADMIN` | Nulo | Obligatorio, 10 a 500 caracteres | El usuario |

Para cada turno de una propuesta publicada, y para cada válvula motorizada del sector (`is_motorized = true`, ordenadas por `rule_valve_orders.sequence_order`), se crean dos comandos:

| Comando | `not_before` | `expires_at` (propuesta, verificar en campo) |
|---|---|---|
| `OPEN` | `start_at - 2 minutos` | `start_at + 10 minutos` |
| `CLOSE` | `end_at` | `end_at + 10 minutos` |

Un sector sin válvulas motorizadas no genera comandos. Se opera a mano con la válvula de bypass (ver sección 6).

Si una propuesta publicada cambia (no se modifica: se genera una nueva, ver `Reglas-de-negocio.md`, sección 13), los comandos `PENDING` de la propuesta anterior pasan a `CANCELLED` con un evento en `devices.valve_command_events`.

### 5.2 Estados del comando

Estados (`devices.valve_commands.status`): `PENDING`, `DELIVERED`, `ACKED`, `FAILED`, `EXPIRED`, `CANCELLED`.

| Desde | Hacia | Disparador |
|---|---|---|
| (nada) | `PENDING` | Creación |
| `PENDING` | `DELIVERED` | El dispositivo lo recibe en `GET /devices/commands` |
| `DELIVERED` | `ACKED` | `POST /devices/commands/{id}/ack` con `ACKED` |
| `DELIVERED` | `FAILED` | `POST /devices/commands/{id}/ack` con `FAILED` |
| `PENDING` o `DELIVERED` | `EXPIRED` | Pasa `expires_at` sin confirmación (job de expiración, propuesta cada minuto) |
| `PENDING` | `CANCELLED` | Cambio o cancelación del turno |

Cada transición escribe una fila en `devices.valve_command_events` (`event`, `detail`, `occurred_at`).

### 5.3 Entrega y confirmación

- `GET /devices/commands` devuelve hasta 100 comandos del dispositivo con `status` `PENDING` o `DELIVERED` (sin confirmar), cuyo `not_before` ya llegó y cuyo `expires_at` no ha pasado. Al primer envío pasan a `DELIVERED`.
- La entrega es al menos una vez: un comando `DELIVERED` sin confirmar se vuelve a enviar mientras siga vigente. El dispositivo debe tratarlo como idempotente. Abrir una válvula que ya está abierta no tiene efecto.
- `POST /devices/commands/{id}/ack` con `result` `ACKED` o `FAILED` y `detail` (hasta 200 caracteres). Confirmar dos veces lo mismo responde igual (idempotente).
- Un `FAILED` avisa a la Junta. La forma (incidente automático, notificación) está por definir.

### 5.4 Expiración

Un comando solo se ejecuta dentro de su ventana `[not_before, expires_at)`. El dispositivo lo verifica con su reloj. Si el reloj del dispositivo está desfasado más de 300 segundos, sus firmas ya fallan (sección 3), así que no ejecuta comandos hasta sincronizar.

Un comando vencido nunca se ejecuta, aunque llegue tarde. El servidor lo marca `EXPIRED` y no lo vuelve a entregar.

### 5.5 Reglas de seguridad de las válvulas

- Un solo comando activo por válvula y ventana: no se encolan dos `OPEN` o `CLOSE` con la misma `not_before` para la misma válvula (propuesta: índice único parcial).
- Si llegan comandos contradictorios para la misma válvula, el dispositivo ejecuta el de `not_before` más reciente y descarta el anterior.
- El comando manual siempre requiere motivo y queda en la auditoría con el usuario que lo creó.

## 6. Comportamiento ante fallas

| Falla | Qué pasa | Por qué es seguro |
|---|---|---|
| Se corta internet en campo | El dispositivo guarda sus lecturas en un buffer local y las reenvía con los mismos `id` cuando vuelve la señal (patrón P11, `OfflineBufferTransport` en el simulador) | La idempotencia por `id` evita duplicados |
| El servidor no responde | El dispositivo reintenta con espera creciente. No abre ni cierra válvulas por su cuenta. | La válvula queda en su última posición |
| El dispositivo no recibe comandos | Mantiene la posición actual. Si la válvula tiene `fail_safe_position` distinto de `KEEP`, aplica esa posición al perder la comunicación durante un turno (por definir la lógica exacta en el firmware). | Cada válvula define su posición segura en `org.valves.fail_safe_position` (`KEEP`, `OPEN` o `CLOSED`) |
| Un comando llega tarde | Se descarta si pasó `expires_at` | Evita abrir agua fuera del turno |
| Falla la firma o la clave | El dispositivo no envía ni ejecuta. Se avisa por la página de estado (por definir). | Un dispositivo sin autenticación no puede mover válvulas |
| Falla la válvula (no responde al comando) | Responde `ACK` con `FAILED`. La Junta puede usar el bypass manual. | La válvula manual en paralelo siempre permite operar |
| Se rota la clave | Ver sección 1.3 | Ventana corta y coordinada |
| Un sensor manda valores pegados o fuera de rango | El punto se guarda. La lectura se rechaza o se marca. El estado del tanque muestra la anomalía `SENSOR_FAULT` (`Reglas-de-negocio.md`, sección 11). | El sistema no actúa sobre un dato sospechoso |

Bypass manual: toda válvula con actuador tiene una válvula manual en paralelo (hechos, sección 6). Su operación es posible sin el sistema, así que el sistema nunca es el único camino para abrir agua.

## 7. Conversión y eventos internos

Eventos de dominio que emite el módulo de dispositivos (patrón P17, `DomainEventPublisher`):

| Evento (clase, en inglés) | Cuándo | Quién escucha |
|---|---|---|
| `SensorReadingAccepted` | Una lectura de sensor queda guardada | Estado del tanque (recalcula tendencia) |
| `ValveCommandDelivered` | Un comando pasa a `DELIVERED` | Auditoría |
| `ValveCommandConfirmed` | Un comando pasa a `ACKED` o `FAILED` | Auditoría y avisos a la Junta |
| `DeviceKeyRevoked` | Una clave se revoca | Auditoría |

## 8. Errores del módulo de dispositivos

| Código | HTTP | Mensaje | Cuándo |
|---|---|---|---|
| `INVALID_SIGNATURE` | 401 | La firma del dispositivo no es válida. | Firma, clave o dispositivo desconocido |
| `NONCE_REPLAY` | 401 | Petición repetida. | Nonce ya visto |
| `TIMESTAMP_OUT_OF_WINDOW` | 401 | La hora de la petición está fuera de la ventana permitida. | Fuera de ±300 s |
| `DEVICE_INACTIVE` | 403 | El dispositivo no está activo. | Suspendido o retirado |
| `INVALID_KEY` | 422 | La clave pública no es válida. | No decodifica a 32 bytes |
| `PAYLOAD_TOO_LARGE` | 413 | El cuerpo de la petición es demasiado grande. | Más de 256 KiB o 64 KiB según la ruta |
| `RATE_LIMITED` | 429 | Hiciste demasiadas solicitudes. Espera un momento. | Más de 60 por minuto |
| `INVALID_STATE_TRANSITION` | 409 | Esta acción no es válida en el estado actual. | `ack` sobre comando `EXPIRED` o `CANCELLED` |
| `REASON_REQUIRED` | 422 | Escribe el motivo (entre 10 y 500 caracteres). | Comando manual sin motivo |

Un error de firma nunca revela qué parte falló (clave, nonce o firma), salvo en el registro de seguridad.

## 9. Pruebas

- Pruebas de firma: los vectores de la sección 2.3 se ejecutan en el backend (con la librería de Ed25519 de Java, propuesta: BouncyCastle, que ya usa el proyecto para Argon2id) y en el simulador (`cryptography`). Ambos deben obtener el mismo resultado.
- Pruebas de idempotencia: el mismo lote de telemetría dos veces no duplica lecturas.
- Pruebas de expiración: un comando después de `expires_at` nunca pasa a `DELIVERED`.
- Pruebas de orden: la validación responde en el orden de la sección 3.1. Cada paso tiene una prueba que demuestra que el paso siguiente no se ejecuta.
- Prueba de ausencia de nonce: un nonce rechazado por firma inválida no queda registrado.

Relacionados: [API.md](API.md), [Reglas-de-negocio.md](Reglas-de-negocio.md), [Contrato-IA.md](Contrato-IA.md), [Diccionario-de-datos.md](Diccionario-de-datos.md)
