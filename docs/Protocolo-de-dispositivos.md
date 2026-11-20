# Protocolo de dispositivos

Este documento define cómo se comunican los dispositivos de campo con `caudal-backend`: el sensor de nivel, los actuadores de válvula y el puente USB que conecta la placa con el backend. Cubre el registro y las claves, la firma de cada petición, la validación en el servidor, la telemetría `DISTANCE`, la configuración firmada, los comandos de válvula, el protocolo serie del Arduino UNO y el comportamiento ante fallas.

El dispositivo de la institución de prueba `maqueta-caudal` es la maqueta física: un Arduino UNO conectado por USB, un HC-SR04 y dos servos MG90S (ver [Simulador-y-hardware.md](Simulador-y-hardware.md)). El puente `caudal-sim maqueta-usb` (en `caudal-simulador`) habla con la placa por serie y con el backend con firmas Ed25519. El ESP32 con Wi-Fi queda como alternativa futura. Todo lo que aquí se describe debe funcionar igual con la maqueta y con hardware de campo, y el simulador virtual lo reproduce sin cambios.

Fuente de las reglas: [Reglas-de-negocio.md](Reglas-de-negocio.md), secciones 4, 7 y 9; [API.md](API.md), sección 16. Lo que no está en esas fuentes se marca "propuesta" o "por definir".

## 1. Registro de dispositivos y claves

### 1.1 Alta de un dispositivo

1. Un usuario con permiso `DEVICE_MANAGE` (`INSTITUTION_ADMIN`, o `PLATFORM_ADMIN` con `act_as`) llama a `POST /api/v1/devices` con `kind`, `name` y el tanque o la válvula a la que pertenece (`devices.devices`). Los `kind` son `LEVEL_SENSOR`, `VALVE_ACTUATOR` y `GATEWAY`.
2. El dispositivo nace en estado `ACTIVE`, pero sin clave. Sin clave no puede firmar nada, así que no acepta peticiones.
3. El dispositivo genera su par de claves Ed25519 y su clave privada nunca sale de él.
4. El usuario registra la clave pública con `POST /api/v1/devices/{id}/keys`.

En la maqueta hay tres dispositivos: un `LEVEL_SENSOR` (el HC-SR04) y dos `VALVE_ACTUATOR` (los servos, uno por línea de servicio). Quién firma por cada uno es una decisión abierta (ver 1.2).

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

Clave privada: nunca llega al servidor, ni se guarda en git ni en logs.

- **Maqueta.** El Arduino UNO no firma nada: no tiene red ni criptografía en el firmware. La firma la hace el puente en el equipo local, y las tres claves privadas (una por dispositivo: un `LEVEL_SENSOR` y dos `VALVE_ACTUATOR`) viven en disco local con permisos `600`. No hay una clave del puente: cada dispositivo firma con la suya.
- **Simulador.** La clave vive en `.env` o en un directorio de claves que `.gitignore` excluye.
- **Hardware de campo.** En memoria protegida del microcontrolador (por definir según la placa elegida).

### 1.3 Rotación

Regla de la BD: un dispositivo tiene una sola clave vigente, la de `revoked_at IS NULL` (índice único parcial, propuesta).

Procedimiento:

| Paso | Quién | Acción |
|---|---|---|
| 1 | Dispositivo | Genera un par nuevo y lo tiene listo, sin usarlo |
| 2 | Usuario con `DEVICE_MANAGE` | `POST /devices/{id}/keys` con la clave nueva |
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

Hash de cuerpo vacío (para `GET /devices/config` y `GET /devices/commands`):

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

Los vectores (cadena, clave pública, firma esperada y resultado) se generan al implementar las pruebas de firma, cuando el simulador tenga las claves de prueba. Se guardan en el repositorio de pruebas del backend y en el simulador, para que ambos lados verifiquen la misma firma.

| ID | Descripción | Resultado esperado |
|---|---|---|
| SV-01 | Telemetría válida | 200 |
| SV-02 | Cuerpo alterado un byte | 401 `INVALID_SIGNATURE` |
| SV-03 | Nonce repetido | 401 `NONCE_REPLAY` |
| SV-04 | Timestamp a 301 s | 401 `TIMESTAMP_OUT_OF_WINDOW` |
| SV-05 | Clave revocada | 401 `INVALID_SIGNATURE` |
| SV-06 | Dispositivo suspendido | 403 `DEVICE_INACTIVE` |
| SV-07 | Cuerpo de 256 KiB más 1 byte | 413 `PAYLOAD_TOO_LARGE` |
| SV-08 | Configuración firmada (`GET /devices/config`) con cuerpo vacío | 200 con la configuración del dispositivo |

## 3. Validación en el servidor

### 3.1 Orden

Los pasos se ejecutan en este orden. Al primer fallo se responde y se detiene el proceso.

| Paso | Validación | Si falla | Notas |
|---|---|---|---|
| 0 | Tamaño del cuerpo: no más de 256 KiB en telemetría ni 64 KiB en el resto | `413 PAYLOAD_TOO_LARGE` | Se aplica en el filtro de transporte antes de leer el cuerpo (`Content-Length` y límite de flujo). El paso 6 la repite en la capa de aplicación. |
| 1 | Dispositivo activo: existe `X-Device-Id` y su estado es `ACTIVE` | No existe: `401 INVALID_SIGNATURE`. `SUSPENDED` o `RETIRED`: `403 DEVICE_INACTIVE` | Un dispositivo inexistente responde igual que una firma incorrecta, para no revelar qué identificadores existen. |
| 2 | Clave vigente: existe una clave con `revoked_at IS NULL` | `401 INVALID_SIGNATURE` | La firma se verifica solo con esta clave. |
| 3 | Ventana de tiempo: `abs(ahora - X-Timestamp) <= 300` segundos | `401 TIMESTAMP_OUT_OF_WINDOW` | Tolerancia de ±300 segundos. |
| 4 | Nonce no visto: no existe `device_nonces` con ese dispositivo y nonce con `seen_at` dentro de 10 minutos | `401 NONCE_REPLAY` | Solo consulta. No inserta todavía. |
| 5 | Firma: `Ed25519.verify(clave_vigente, cadena_canónica, firma)` | `401 INVALID_SIGNATURE` | Solo después de verificar la firma se registra el nonce. |
| 5b | Registro del nonce: `INSERT` en `device_nonces`. Si ya existe (carrera entre dos peticiones iguales), `401 NONCE_REPLAY`. | `401 NONCE_REPLAY` | Se hace en la misma transacción que el procesamiento. |
| 6 | Tamaño del cuerpo, verificación en la aplicación | `413 PAYLOAD_TOO_LARGE` | Repetición del paso 0 para peticiones sin `Content-Length`. |
| 7 | Límite de tasa: 60 peticiones firmadas por minuto por dispositivo | `429 RATE_LIMITED` | Va después de la firma para que un atacante sin clave no consuma el cupo de un dispositivo real. |
| 8 | Validación del cuerpo (esquema, límites, puntos) | `400 VALIDATION_ERROR` | Ver [API.md](API.md), sección 16. |

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
| Dispositivo suspendido o retirado | `FORBIDDEN` | `MEDIUM` | `DEVICE_INACTIVE` (propuesta: el catálogo de tipos no tiene uno propio) |
| Límite de tasa | `RATE_LIMITED` | `LOW` | `device` |
| Diez o más firmas inválidas del mismo dispositivo en 10 minutos | `INVALID_SIGNATURE` | `HIGH` | `REPEATED` (propuesta) |

## 4. Telemetría y configuración

### 4.1 `POST /api/v1/devices/telemetry`

El sensor envía la distancia cruda que mide el HC-SR04, en mm. El backend la convierte en nivel y en volumen con la geometría del tanque. La conversión está en [Reglas-de-negocio.md](Reglas-de-negocio.md), sección 2.

Cuerpo: `points`, lista de 1 a 100 puntos. Cada punto:

| Campo | Tipo | Oblig. | Límites |
|---|---|---|---|
| `id` | UUID | Sí | Lo genera el dispositivo. Es la clave de idempotencia y el `id` de `devices.telemetry_points`. |
| `metric` | texto | Sí | `DISTANCE` (sensor, mm crudos). `VALVE_POSITION`, `BATTERY_V` y `RSSI` están por definir: la maqueta por USB no los mide. |
| `value` | entero | Sí | Para `DISTANCE`: `-1` (sin eco) o de 0 a 4000 mm (alcance del HC-SR04). |
| `observed_at` | fecha y hora | Sí | ISO-8601 con zona. Momento de la medición en el dispositivo. |

Frecuencia: el puente envía un punto cada 2 segundos, es decir, 30 peticiones por minuto. El límite es de 60 por minuto por dispositivo, así que hay margen. No se envían lotes de 20 segundos.

Ejemplo (valores ficticios):

```json
{
  "points": [
    { "id": "0190f3a2-6666-7000-8000-000000000001", "metric": "DISTANCE", "value": 215, "observed_at": "2026-11-20T15:30:00-05:00" }
  ]
}
```

Respuesta 200:

```json
{
  "accepted": 1,
  "duplicates": 0,
  "rejected": 0,
  "results": [
    { "id": "0190f3a2-6666-7000-8000-000000000001", "status": "ACCEPTED", "reading_id": "0190f3a2-7777-7000-8000-000000000001" }
  ]
}
```

Estados por punto:

| `status` | Significado |
|---|---|
| `ACCEPTED` | Guardado. Si el punto produjo una lectura efectiva en ese momento, trae `reading_id`. |
| `DUPLICATE` | Ya existía un punto con ese `id`. Mismo contenido: no se guarda otra vez. |
| Error por punto (`error`) | No se guarda. Trae `code`: `DISTANCE_OUT_OF_RANGE`, `FUTURE_TIMESTAMP` o `VALIDATION_ERROR`. El resto del lote sigue. |

Un `id` repetido con contenido distinto se rechaza con `code = CONFLICT`. La idempotencia se cumple solo para contenido igual.

### 4.2 Almacenamiento

- Todos los puntos crudos se guardan en `devices.telemetry_points` (append-only), también los `-1`.
- `telemetry_points.reading_id` apunta a la lectura efectiva creada, si la hubo.
- La retención de estos datos sigue `audit.retention_policies` con la clase `TELEMETRY_POINTS`.

### 4.3 Del sensor a la lectura efectiva

Cada punto `DISTANCE` pasa por la cadena de validación (patrón Chain of Responsibility), en este orden:

| Orden | Validador | Efecto |
|---|---|---|
| 1 | Sin eco (`value = -1`) | Se guarda el punto. No crea lectura en `ops.readings` ni produce nivel. Cuenta para `SENSOR_FAULT`. |
| 2 | Zona ciega (`value < blind_zone_mm`) | Se guarda el punto. No produce nivel. Cuenta para `SENSOR_FAULT`. |
| 3 | Fuera de rango físico (menor que 0 distinto de `-1`, o mayor que 4000) | `DISTANCE_OUT_OF_RANGE`. No se guarda. |
| 4 | Futuro (`observed_at > ahora + 5 min`) | `FUTURE_TIMESTAMP`. No se guarda. |
| 5 | Antigüedad (`observed_at < ahora - max_backdate_hours`) | `TIMESTAMP_TOO_OLD`. No se guarda. |

Si el punto pasa todos los validadores, el backend calcula `level_mm`, `level_pct` y `volume_l` con la geometría de la institución (patrón Strategy: `CylinderGeometry` o `RectangularGeometry`). Luego decide si guarda una lectura efectiva en `ops.readings`:

- cada `persist_interval_seconds`, o
- antes, si el nivel cambia más de un umbral (valor por definir).

Si se guardara una lectura efectiva por cada punto, el sensor a 2 segundos sumaría unas 43.000 filas al día. Los campos de la lectura efectiva son `tank_id`, `device_id`, `distance_mm`, `level_mm`, `level_pct`, `volume_l`, `observed_at`, `received_at` y `source = SENSOR`. Los nombres exactos de columna se confirman en [Modelo-de-datos.md](Modelo-de-datos.md).

El `-1` se guarda como punto crudo, pero no como nivel. Si el último punto es `-1`, el estado en vivo es `NO_ECHO`. Así el backend distingue un sensor sin eco de un sensor que dejó de enviar (`STALE`).

### 4.4 `GET /api/v1/devices/config`

El backend entrega a cada dispositivo su configuración firmada. El puente la relee sin reiniciarse, así que un cambio del administrador (ángulo, altura, calibración, canal) llega sin cortar el servicio. El firmware del Arduino no cambia: recibe ángulos crudos y no conoce las palabras "abierto" o "cerrado".

Cuerpo vacío. Firma normal (sección 2). Respuesta para el sensor:

```json
{
  "config_version": 7,
  "device_id": "0190f3a2-9999-7000-8000-000000000001",
  "kind": "LEVEL_SENSOR",
  "sensor": { "empty_distance_mm": 350, "full_distance_mm": 80, "blind_zone_mm": 20, "sample_interval_seconds": 2 }
}
```

Respuesta para un actuador:

```json
{
  "config_version": 7,
  "device_id": "0190f3a2-9999-7000-8000-000000000002",
  "kind": "VALVE_ACTUATOR",
  "actuator": { "valve_id": "0190f3a2-e002-7000-8000-000000000001", "servo_channel": 1, "open_angle_deg": 90, "closed_angle_deg": 0, "hold_torque_after_move": true, "fail_safe_position": "KEEP" }
}
```

- `config_version` sube con cada cambio de configuración. El puente aplica solo versiones nuevas.
- Cadencia de consulta: por definir. Propuesta: cada 30 segundos, o al cambiar `config_version`. Con esa cadencia, la consulta gasta 2 peticiones por minuto del cupo de 60.
- `sample_interval_seconds` y el tiempo máximo de recorrido de un servo (`max_travel_seconds`) son valores por definir. El segundo vive en el YAML del puente, junto con el puerto serie.
- Errores: las mismas firmas de la sección 3, y `403 DEVICE_INACTIVE`.

## 5. Comandos de válvula

### 5.1 Origen

Cada comando tiene exactamente un origen. La restricción de la base de datos es `manual_reason XOR autopilot_decision_id`.

| Origen | Quién lo crea | `manual_reason` | `autopilot_decision_id` | `created_by` |
|---|---|---|---|---|
| Manual | Un `OPERATOR` o un `INSTITUTION_ADMIN` con `POST /valve-commands/manual` (permiso `VALVE_COMMAND_MANUAL`) | Texto del operador, o el texto por defecto si no escribió nada | Nulo | El usuario |
| Piloto automático | El piloto, desde una decisión de `ops.autopilot_decisions`, en modo `ASSISTED` (aceptada por una persona) o `AUTOMATIC` | Nulo | La decisión que lo originó | Nulo, o la persona que aceptó |

El dispositivo no distingue el origen: verifica la firma y ejecuta igual. Ver [API.md](API.md), sección 10.

Cada comando lleva una acción (`OPEN` o `CLOSE`) para una válvula. Su ventana es `[not_before, expires_at)`. Los valores por defecto están en [API.md](API.md), sección 10.1.

### 5.2 Estados del comando

Estados (`devices.valve_commands.status`): `PENDING`, `DELIVERED`, `ACKED`, `FAILED`, `EXPIRED`, `CANCELLED`. `ACKED`, `FAILED`, `EXPIRED` y `CANCELLED` son finales.

El ciclo se implementa con el patrón State (`ValveCommandState`).

| Desde | Hacia | Disparador |
|---|---|---|
| (nada) | `PENDING` | Creación |
| `PENDING` | `DELIVERED` | El puente lo recoge con `GET /devices/commands` (sondeo cada 2 s) |
| `DELIVERED` | `ACKED` | El Arduino responde `DONE` y el puente confirma con `POST /devices/commands/{id}/ack` con `ACKED` |
| `DELIVERED` | `FAILED` | El puente no recibe `DONE` dentro de `max_travel_seconds`, o el Arduino responde `ERR`. Se confirma con `POST .../ack` con `FAILED`. |
| `PENDING` | `EXPIRED` | Pasa `expires_at` sin entrega |
| `DELIVERED` | `EXPIRED` | No llega el ack antes de `expires_at`. Por confirmar. |
| `PENDING` | `CANCELLED` | Llega un comando nuevo para la misma válvula. El anterior queda con motivo `SUPERSEDED`. |
| `DELIVERED` | `CANCELLED` | Llega un comando nuevo para la misma válvula. El anterior queda con motivo `SUPERSEDED`. |

Cada transición escribe una fila en `devices.valve_command_events` (`event`, `detail`, `occurred_at`).

### 5.3 Entrega y confirmación

- `GET /devices/commands` devuelve hasta 100 comandos del dispositivo con `status` `PENDING` o `DELIVERED` (sin confirmar), cuyo `not_before` ya llegó y cuyo `expires_at` no ha pasado. Al primer envío pasan a `DELIVERED`.
- La entrega es al menos una vez: un comando `DELIVERED` sin confirmar se vuelve a enviar mientras siga vigente. El dispositivo debe tratarlo como idempotente. Abrir una válvula que ya está abierta no tiene efecto.
- `POST /devices/commands/{id}/ack` con `result` `ACKED` o `FAILED` y `detail` (hasta 200 caracteres). Confirmar dos veces lo mismo responde igual (idempotente).
- `ACKED` confirma que el actuador respondió, no que el agua haya fluido.
- Un `FAILED` aparece en la interfaz del operador y del administrador como evento `COMMAND_FAILED` ([API.md](API.md), sección 9.3). No hay aviso externo.

### 5.4 Expiración

Un comando solo se ejecuta dentro de su ventana `[not_before, expires_at)`. El dispositivo lo verifica con su reloj. Si el reloj del dispositivo está desfasado más de 300 segundos, sus firmas ya fallan (sección 3), así que no ejecuta comandos hasta sincronizar.

Un comando vencido nunca se ejecuta, aunque llegue tarde. El servidor lo marca `EXPIRED` y no lo vuelve a entregar.

### 5.5 Reglas de seguridad de las válvulas

- Un solo comando activo (`PENDING` o `DELIVERED`) por válvula. Si llega otro, reemplaza al anterior, que pasa a `CANCELLED` con motivo `SUPERSEDED` ([API.md](API.md), sección 10.1).
- Si llegan comandos contradictorios para la misma válvula, el puente ejecuta el de `not_before` más reciente y descarta el anterior.
- El comando manual queda en la auditoría con el usuario que lo creó.
- Un comando de piloto nunca abre una válvula con el nivel en o por debajo del piso de reserva (ver [API.md](API.md), sección 11).

## 6. Puente USB y el Arduino UNO

Esta sección describe la maqueta. Es el único protocolo serie del proyecto.

### 6.1 Cableado y pines

| Componente | Conexión | Notas |
|---|---|---|
| HC-SR04 | VCC a riel de 5 V, GND a riel de GND, TRIG a D9, ECHO a D8 | Alcance de 4000 mm según la hoja de datos |
| Servo 1 (MG90S) | Señal a D10, rojo a riel de 5 V, marrón a riel de GND | Canal 1. Pisa la manguera de la línea "Acueducto 1". |
| Servo 2 (MG90S) | Señal a D11, rojo a riel de 5 V, marrón a riel de GND | Canal 2. Pisa la manguera de la línea "Acueducto 2". |

Todo se alimenta por el USB del UNO, a través de los rieles del protoboard. No hay fuente externa en el montaje actual. Esto es un riesgo: dos servos pueden hacer caer el voltaje y reiniciar la placa en medio de una maniobra. Ver [Riesgos.md](Riesgos.md), RG-01.

### 6.2 Protocolo serie

Velocidad de 115200 baudios. Cada comando es una línea terminada en `\n`. Cada respuesta es una línea. Si la línea es muy larga, responde `ERR linea demasiado larga`.

| Comando (puente → placa) | Respuesta (placa → puente) | Significado |
|---|---|---|
| (al arrancar) | `READY caudal-uno <versión>` | Firmware listo. La versión actual es `1.0.0`. |
| `PING` | `PONG` | Prueba de vida |
| `INFO` | `INFO caudal-uno <versión> s1=<grados\|-1> s2=<grados\|-1>` | Versión y última posición de cada servo (`-1` si nunca se movió) |
| `D` | `D <mm>` | Distancia en mm, mediana de los últimos pings válidos. `D -1` si ningún ping volvió. |
| `S<n> <grados>` | `OK S<n> <grados>` y después `DONE S<n> <grados>` | Inicia un movimiento suave del servo `n` (1 o 2) hasta `grados` (0 a 180). `DONE` llega cuando el servo alcanza el ángulo. |
| `R<n>` | `OK R<n>` | Libera el servo `n` (sin par de sostén). El puente lo envía tras `DONE` cuando `hold_torque_after_move` es `false`. |
| Cualquier otra línea | `ERR <motivo>` | Por ejemplo: `ERR servo desconocido, usa S1 <grados> o S2 <grados>`, `ERR angulo fuera de 0..180`, `ERR comando desconocido`, `ERR linea demasiado larga` |

Notas:

- Abrir el puerto reinicia el UNO. Por eso el puente espera la línea `READY` antes de enviar nada.
- El primer movimiento de un servo tiene un asentamiento: el firmware no dice `DONE` hasta que ese movimiento termina.
- `READY` que aparece en medio de una prueba significa que la placa se reinició. Es un problema de alimentación, no de código (RG-01).

### 6.3 Un comando de válvula de punta a punta

1. El operador pulsa Abrir en la interfaz. El backend crea el comando en `PENDING`.
2. El puente llama a `GET /devices/commands` (cada 2 s). Recibe el comando, que pasa a `DELIVERED`.
3. El puente traduce `OPEN` al ángulo abierto de la configuración de esa válvula (`open_angle_deg`) y envía `S<canal> <grados>` por serie.
4. La placa responde `OK S<canal> <grados>` y, al terminar el movimiento, `DONE S<canal> <grados>`.
5. Si `hold_torque_after_move` es `false`, el puente envía `R<canal>` tras el `DONE`. Luego confirma con `POST /devices/commands/{id}/ack` y `result = ACKED`, con `detail` de la respuesta de la placa. El comando pasa a `ACKED`.
6. Si no llega `DONE` dentro de `max_travel_seconds`, o la placa responde `ERR`, el puente confirma `FAILED`.

El firmware no sabe qué es un comando de válvula, solo mueve servos a ángulos crudos.

Objetivo de latencia: `ACKED` en menos de unos 5 segundos con el puente conectado (ver [Requerimientos.md](Requerimientos.md)).

### 6.4 Lo que el puente no hace

- No abre ni cierra válvulas por su cuenta. Solo ejecuta comandos firmados que vienen del backend.
- Guarda en disco los puntos que no pudo enviar y los reenvía en lote cuando vuelve la conexión. Un punto con `observed_at` más viejo que `max_backdate_hours` se rechaza (`TIMESTAMP_TOO_OLD`). La aplicación web no tiene cola sin conexión.
- No calibra. La calibración se hace desde la aplicación con las lecturas en vivo. El comando `calibrar` de `caudal-sim` queda para banco de pruebas.

## 7. Comportamiento ante fallas

| Falla | Qué pasa | Por qué es seguro |
|---|---|---|
| El backend no responde | El puente reintenta con espera creciente. No abre ni cierra válvulas por su cuenta, tampoco con el piloto en `AUTOMATIC`: el piloto decide en el backend y el dispositivo solo ejecuta comandos firmados. Los comandos sin entregar vencen (`EXPIRED`). | La válvula queda en su última posición |
| El puente o la placa pierden contacto | La válvula se queda en la última posición. Si la válvula tiene `fail_safe_position` distinto de `KEEP`, la posición segura se aplicaría al perder la comunicación. La lógica exacta, el tiempo de espera y quién la ejecuta (el puente o la placa) están por definir. La maqueta todavía no la implementa. | Cada válvula define su posición segura en `org.valves.fail_safe_position` (`KEEP`, `OPEN` o `CLOSED`) |
| Un comando llega tarde | Se descarta si pasó `expires_at` | Evita abrir agua fuera de la ventana |
| Falla la firma o la clave | El dispositivo no envía ni ejecuta. El puente muestra el error en su consola. La página de estado está por definir. | Un dispositivo sin autenticación no puede mover válvulas |
| La válvula no responde al comando | La placa no envía `DONE` a tiempo. El puente confirma `FAILED`. Aparece el evento `COMMAND_FAILED`. | El sistema no asume que la válvula se movió |
| La placa se reinicia (`READY` en medio de una prueba) | Se trata como falla de alimentación, no de código. El puente lo registra. Qué muestra la válvula después (`UNKNOWN` o el último ack) está por definir; [Riesgos.md](Riesgos.md), RG-01, propone `UNKNOWN`. | La placa reiniciada no conoce su posición |
| El sensor manda valores pegados o sin eco | El punto se guarda. El tanque muestra `NO_ECHO` o `SENSOR_FAULT` ([Reglas-de-negocio.md](Reglas-de-negocio.md), sección 7). | El sistema no actúa sobre un dato sospechoso |
| Se rota la clave | Ver sección 1.3 | Ventana corta y coordinada |

Válvula manual: en campo, cada válvula con actuador tendría una válvula manual en paralelo, para que el agua siempre se pueda operar sin el sistema (propuesta para hardware de campo). La maqueta no tiene bypass: las mangueras se pisan y se sueltan a mano.

## 8. Eventos internos

Eventos de dominio que emite el módulo de dispositivos (patrón `DomainEventPublisher`):

| Evento (clase, en inglés) | Cuándo | Quién escucha |
|---|---|---|
| `SensorPointAccepted` | Un punto `DISTANCE` queda guardado | Estado en vivo (recalcula el sensor) y tendencia |
| `ValveCommandDelivered` | Un comando pasa a `DELIVERED` | Auditoría |
| `ValveCommandConfirmed` | Un comando pasa a `ACKED` o `FAILED` | Auditoría y eventos de la institución (`COMMAND_FAILED`) |
| `DeviceKeyRevoked` | Una clave se revoca | Auditoría |

## 9. Errores del módulo de dispositivos

| Código | HTTP | Mensaje | Cuándo |
|---|---|---|---|
| `INVALID_SIGNATURE` | 401 | La firma del dispositivo no es válida. | Firma, clave o dispositivo desconocido |
| `NONCE_REPLAY` | 401 | Petición repetida. | Nonce ya visto |
| `TIMESTAMP_OUT_OF_WINDOW` | 401 | La hora de la petición está fuera de la ventana permitida. | Fuera de ±300 s |
| `DEVICE_INACTIVE` | 403 | El dispositivo no está activo. | Suspendido o retirado |
| `INVALID_KEY` | 422 | La clave pública no es válida. | No decodifica a 32 bytes |
| `PAYLOAD_TOO_LARGE` | 413 | El cuerpo de la petición es demasiado grande. | Más de 256 KiB o 64 KiB según la ruta |
| `RATE_LIMITED` | 429 | Hiciste demasiadas solicitudes. Espera un momento. | Más de 60 por minuto |
| `DISTANCE_OUT_OF_RANGE` | 422 | La distancia del sensor está fuera de su alcance. | Punto `DISTANCE` menor que 0 (distinto de `-1`) o mayor que 4000 |
| `INVALID_STATE_TRANSITION` | 409 | Esta acción no es válida en el estado actual. | `ack` sobre comando `EXPIRED` o `CANCELLED` |

Un error de firma nunca revela qué parte falló (clave, nonce o firma), salvo en el registro de seguridad.

## 10. Pruebas

- Pruebas de firma: los vectores de la sección 2.3 se ejecutan en el backend (con la librería de Ed25519 de Java, propuesta: BouncyCastle, que ya usa el proyecto para Argon2id) y en el simulador (`cryptography`). Ambos deben obtener el mismo resultado.
- Pruebas de idempotencia: el mismo punto de telemetría dos veces no duplica lecturas.
- Pruebas de sin eco: un punto `-1` se guarda, no produce nivel y cuenta para `SENSOR_FAULT` tras la ventana.
- Pruebas de expiración: un comando después de `expires_at` nunca pasa a `DELIVERED`.
- Pruebas de orden: la validación responde en el orden de la sección 3.1. Cada paso tiene una prueba que demuestra que el paso siguiente no se ejecuta.
- Prueba de ausencia de nonce: un nonce rechazado por firma inválida no queda registrado.
- Prueba de banco de la maqueta (manual): abrir y cerrar cada válvula desde la aplicación, con `ACKED` en menos de unos 5 segundos, con la manguera pisada o libre.

Relacionados: [API.md](API.md), [Reglas-de-negocio.md](Reglas-de-negocio.md), [Simulador-y-hardware.md](Simulador-y-hardware.md), [Riesgos.md](Riesgos.md), [Contrato-IA.md](Contrato-IA.md), [Diccionario-de-datos.md](Diccionario-de-datos.md)
