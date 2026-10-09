# Contrato entre el backend y caudal-ia

Este documento define el contrato HTTP entre `caudal-backend` (cliente) y `caudal-ia` (servicio de pronóstico). Es la referencia para implementar cualquiera de los dos lados y para escribir las pruebas de contrato. Los hechos canónicos (secciones 6, 7, 11 y 14) son la fuente de las cifras.

Principios:

- `caudal-ia` no tiene estado y no accede a la base de datos. Recibe una serie, devuelve cuantiles.
- El backend decide cuándo pedir un pronóstico, guarda el resultado y, si la IA falla, responde con la estimación simple (respaldo).
- Ningún lado confía en el otro: cada uno valida lo que recibe.

## 1. Endpoints

| Método | Ruta | Autenticación | Uso |
|---|---|---|---|
| POST | `/v1/forecasts` | Token de servicio | Pronóstico de una serie diaria |
| GET | `/v1/health` | Ninguna | Vivo (liveness). No revela nada del modelo. |
| GET | `/v1/ready` | Ninguna | Listo para atender (modelo cargado) |
| GET | `/v1/models` | Token de servicio | Modelos disponibles y su estado |

Documentación interactiva del servicio: Swagger en `/docs` (FastAPI). Se apaga con `API_DOCS_ENABLED=false` (propuesta de nombre).

## 2. Autenticación y rotación del token de servicio

### 2.1 Mecanismo

- Cabecera: `Authorization: Bearer <token>`.
- El token tiene al menos 32 bytes aleatorios. Generación recomendada: `python -c "import secrets; print(secrets.token_urlsafe(32))"` (43 caracteres).
- La comparación usa tiempo constante (`hmac.compare_digest`).
- El token nunca aparece en logs. Los logs redactan la cabecera `Authorization`.
- Si el token falta o no coincide: `401 UNAUTHORIZED` con la cabecera `WWW-Authenticate: Bearer`. El cuerpo no dice si el token existe.

### 2.2 Rotación sin corte

El servicio acepta dos tokens: el actual (`IA_SERVICE_TOKEN`) y el anterior (`IA_SERVICE_TOKEN_PREVIOUS`, opcional). La rotación se hace en tres pasos:

| Paso | Dónde | Acción | Resultado |
|---|---|---|---|
| 1 | `caudal-ia` | `IA_SERVICE_TOKEN=<nuevo>`, `IA_SERVICE_TOKEN_PREVIOUS=<viejo>` | El servicio acepta ambos |
| 2 | `caudal-backend` | `IA_SERVICE_TOKEN=<nuevo>` | El backend usa el nuevo; el servicio sigue aceptando el viejo para peticiones en vuelo |
| 3 | `caudal-ia` | Quitar `IA_SERVICE_TOKEN_PREVIOUS` | Solo queda el nuevo |

Si el paso 2 se hace antes del paso 1, el backend recibe `401` y el circuito se abre. Por eso el orden es obligatorio. Cada rotación queda en el registro de auditoría de la operación (por definir dónde).

Un token filtrado se rota de inmediato con el mismo procedimiento. Los hechos dicen "rotación documentada"; esta es la documentación.

## 3. `POST /v1/forecasts`

### 3.1 Petición

Esquema (JSON Schema 2020-12, resumido):

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["series_id", "timestamps", "values", "prediction_length", "quantile_levels"],
  "properties": {
    "series_id": { "type": "string", "format": "uuid" },
    "timestamps": {
      "type": "array", "minItems": 1, "maxItems": 2000,
      "items": { "type": "string", "format": "date-time" }
    },
    "values": {
      "type": "array", "minItems": 1, "maxItems": 2000,
      "items": { "type": "number" }
    },
    "prediction_length": { "type": "integer", "minimum": 1, "maximum": 30 },
    "quantile_levels": { "type": "array", "const": [0.1, 0.5, 0.9] }
  }
}
```

Reglas que el esquema no expresa (las valida la IA con código):

| Regla | Límite | Error si falla |
|---|---|---|
| Misma longitud en `timestamps` y `values` | Igual | `422 INVALID_SERIES` (`reason = LENGTH_MISMATCH`) |
| `timestamps` estrictamente crecientes | Sin repetidos | `422 INVALID_SERIES` (`reason = TIMESTAMPS_NOT_INCREASING`) |
| `timestamps` parseables con zona | ISO-8601 con offset | `422 INVALID_SERIES` (`reason = TIMESTAMP_PARSE_ERROR`) |
| `values` finitos | Sin `NaN`, `Infinity` ni `-Infinity` (JSON estándar no los admite, pero Python sí los parsea: se rechazan explícitamente) | `422 INVALID_SERIES` (`reason = NOT_FINITE_VALUE`) |
| Historia mínima | `FORECAST_MIN_POINTS` (variable de entorno del servicio; valor de ejemplo: 7) | `422 INSUFFICIENT_HISTORY` |
| Serie máxima | 2.000 puntos (hechos, sección 11) | `422 INVALID_SERIES` (`reason = TOO_MANY_POINTS`) |
| `prediction_length` | 1 a 30 (técnico, hechos, sección 11). El horizonte de negocio es 1 a 3 y lo valida el backend. | `400 VALIDATION_ERROR` |
| `quantile_levels` | Exactamente `[0.1, 0.5, 0.9]` en v1 | `400 VALIDATION_ERROR` |
| Campos desconocidos | Se rechazan | `400 VALIDATION_ERROR` |
| Tamaño del cuerpo | 256 KiB (propuesta, verificar) | `413 PAYLOAD_TOO_LARGE` |

Remuestreo: el servicio agrega la serie a frecuencia diaria con `RESAMPLE_AGGREGATION` (por defecto `last`: el último valor del día). Los días sin lectura quedan como hueco que Chronos debe tolerar. El soporte de huecos en Chronos está por verificar; si no lo soporta, el servicio rellena con interpolación lineal y lo informa en `model.notes` (propuesta).

Ejemplo de petición (valores ficticios, 7 días de lecturas efectivas de las 18:00, pronóstico de 1 día):

```json
{
  "series_id": "0190f3a2-8888-7000-8000-000000000001",
  "timestamps": [
    "2026-10-02T18:00:00-05:00",
    "2026-10-03T18:00:00-05:00",
    "2026-10-04T18:00:00-05:00",
    "2026-10-05T18:00:00-05:00",
    "2026-10-06T18:00:00-05:00",
    "2026-10-07T18:00:00-05:00",
    "2026-10-08T18:00:00-05:00"
  ],
  "values": [3.10, 3.02, 2.90, 2.75, 2.60, 2.35, 2.10],
  "prediction_length": 1,
  "quantile_levels": [0.1, 0.5, 0.9]
}
```

### 3.2 Respuesta 200

Esquema:

```json
{
  "type": "object",
  "required": ["model", "generated_at", "frequency", "points"],
  "properties": {
    "model": {
      "type": "object", "required": ["name", "version"],
      "properties": {
        "name": { "type": "string", "maxLength": 60 },
        "version": { "type": "string", "maxLength": 40 },
        "notes": { "type": "string", "maxLength": 200 }
      }
    },
    "generated_at": { "type": "string", "format": "date-time" },
    "frequency": { "const": "D" },
    "points": {
      "type": "array", "minItems": 1, "maxItems": 30,
      "items": {
        "type": "object", "required": ["timestamp", "p10", "p50", "p90"],
        "properties": {
          "timestamp": { "type": "string", "format": "date-time" },
          "p10": { "type": "number" },
          "p50": { "type": "number" },
          "p90": { "type": "number" }
        }
      }
    }
  }
}
```

Ejemplo (valores ficticios):

```json
{
  "model": { "name": "autogluon/chronos-2-small", "version": "2.0.0" },
  "generated_at": "2026-10-09T09:30:00Z",
  "frequency": "D",
  "points": [
    { "timestamp": "2026-10-09T00:00:00-05:00", "p10": 1.80, "p50": 2.10, "p90": 2.40 }
  ]
}
```

Reglas de la respuesta (el servicio las garantiza y el backend las vuelve a verificar):

- `len(points) == prediction_length`.
- Los `timestamp` son días consecutivos, que empiezan el día siguiente al último día de la serie. Se expresan a medianoche en `America/Bogota` (`-05:00`).
- `p10 <= p50 <= p90`, todos finitos.
- `frequency` siempre es `D`.

Si la respuesta no cumple estas reglas, el backend la trata como `IA_ERROR` y usa el respaldo.

### 3.3 Errores

Forma de error: igual que la de la API del backend (`error.code`, `error.message`, `error.details`, `error.request_id`), para que el backend use un solo parser.

| HTTP | `code` | Cuándo | `details` |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Esquema, campos desconocidos, `prediction_length` fuera de 1 a 30, `quantile_levels` distinto | `field`, `reason` |
| 401 | `UNAUTHORIZED` | Token faltante o no válido | Ninguno |
| 413 | `PAYLOAD_TOO_LARGE` | Cuerpo mayor de 256 KiB | `limit_bytes` |
| 422 | `INSUFFICIENT_HISTORY` | Menos de `FORECAST_MIN_POINTS` valores | `received`, `minimum` |
| 422 | `INVALID_SERIES` | Longitud, orden, parseo, valores no finitos, más de 2.000 puntos | `reason`, `index` (primera posición que falla) |
| 500 | `INTERNAL_ERROR` | Error no controlado | Ninguno |
| 503 | `MODEL_NOT_READY` | Modelo no cargado. Cabecera `Retry-After: 30`. | `model_name` |

Ejemplo de error:

```json
{
  "error": {
    "code": "INVALID_SERIES",
    "message": "La serie tiene un valor que no es un número finito.",
    "details": { "reason": "NOT_FINITE_VALUE", "index": 6 },
    "request_id": "9c1e0f2a-3b4d-4e5f-8a6b-7c8d9e0f1a2b"
  }
}
```

### 3.4 Cómo reacciona el backend a cada error

| Resultado de la IA | Respaldo (`fallback_reason`) | Cuenta para el circuit breaker | Acción |
|---|---|---|---|
| 200 válido | Ninguno (`is_fallback = false`) | Éxito | Guardar `forecast_runs` y `forecast_points` |
| 200 inválido (no cumple 3.2) | `IA_ERROR` | Sí, como fallo | Registrar el detalle en logs sin datos de la serie |
| 422 `INSUFFICIENT_HISTORY` | `INSUFFICIENT_HISTORY` | No | Es esperado con poco historial |
| 422 `INVALID_SERIES` | `IA_ERROR` | No, pero genera alerta | Error del backend al armar la serie: se corrige en código |
| 400 `VALIDATION_ERROR` | `IA_ERROR` | No, pero genera alerta | Contrato desalineado: se corrige en código |
| 401 | `IA_ERROR` | Sí | Alerta crítica: token mal configurado |
| 413, 500, 503 | `IA_ERROR` | Sí | Reintento no automático |
| Timeout de conexión (2 s) o de lectura (10 s) | `IA_TIMEOUT` | Sí | Respaldo inmediato |
| Error de red | `IA_ERROR` | Sí | Respaldo inmediato |
| Circuito abierto | `CIRCUIT_OPEN` | No se llama a la IA | Respaldo inmediato |

Nota sobre el 422: como el backend arma la serie, un `INVALID_SERIES` es un defecto del backend y no debe abrir el circuito. Un 401 o un 503 sí son fallos del servicio.

## 4. Timeouts y circuit breaker del backend

### 4.1 Timeouts (semilla)

| Concepto | Valor | Fuente |
|---|---|---|
| Conexión | 2 segundos | Hechos, sección 11 |
| Lectura | 10 segundos | Hechos, sección 11 |
| Variables de configuración | `IA_CONNECT_TIMEOUT_MS=2000`, `IA_READ_TIMEOUT_MS=10000` | |

Sin reintentos automáticos en el mismo ciclo de petición. El reintento lo hace el siguiente cálculo programado o la siguiente petición del usuario. Así una IA lenta no multiplica la espera del usuario.

### 4.2 Circuit breaker

Estados (`CircuitBreakerState`, patrón P18): `CLOSED` (cerrado, llama a la IA), `OPEN` (abierto, no llama) y `HALF_OPEN` (semiabierto, deja pasar una prueba).

| Transición | Condición | Valor inicial |
|---|---|---|
| `CLOSED` a `OPEN` | Fallos seguidos que cuentan para el circuito | 5 fallos seguidos |
| `OPEN` a `HALF_OPEN` | Tiempo de espera | 60 segundos |
| `HALF_OPEN` a `CLOSED` | Las llamadas de prueba responden bien | 2 llamadas (`CIRCUIT_BREAKER_HALF_OPEN_CALLS`) |
| `HALF_OPEN` a `OPEN` | La prueba falla | Vuelve a esperar 60 segundos |

Variables (`.env.example`): `CIRCUIT_BREAKER_FAILURE_THRESHOLD=5`, `CIRCUIT_BREAKER_OPEN_SECONDS=60`, `CIRCUIT_BREAKER_HALF_OPEN_CALLS=2`.

El estado vive en memoria de cada instancia del backend (propuesta). Con varias instancias, cada una abre su circuito por separado. Aceptable mientras haya una sola instancia en Render; verificar si se escala.

La columna "Cuenta para el circuit breaker" de la sección 3.4 indica qué respuestas suman fallos: timeouts, errores de red, 5xx, 401 y respuestas inválidas. Los 422 de datos no abren el circuito.

### 4.3 Respaldo: estimación simple

Cuando la IA no responde o el circuito está abierto, el backend calcula:

- Modelo `naive-persistence` (`forecast_runs.model_name`): el nivel de mañana es igual al último nivel efectivo ("seguirá igual que hoy").
- Rango: cuantiles empíricos de los cambios diarios históricos del mismo tanque, aplicados al último nivel (`p10` y `p90` a partir de la distribución de cambios; `p50` es el último nivel más la mediana del cambio, que se asume cero si no hay historia suficiente).
- `is_fallback = true` y `fallback_reason` según la sección 3.4.
- Se guarda en `forecast_runs` con `model_version` igual a la versión del algoritmo de respaldo (propuesta: `1`).

Si tampoco hay historia suficiente para el rango, el respaldo responde con el último nivel como p10, p50 y p90 y el motivo `INSUFFICIENT_HISTORY`. Eso lo deja claro en la pantalla.

El backend guarda siempre ambos pronósticos (IA y respaldo) para compararlos en `forecast_evaluations`.

## 5. Salud, disponibilidad y modelos

### 5.1 `GET /v1/health`

- Sin autenticación. Respuesta 200: `{"status": "ok"}`. No incluye versión del modelo ni detalles.

### 5.2 `GET /v1/ready`

- Sin autenticación. 200 con `{"status": "ready", "model_loaded": true}` cuando el modelo está en memoria. 503 `MODEL_NOT_READY` en otro caso.
- La plataforma usa `ready` para decidir si envía tráfico. El backend no lo consulta en cada petición.

### 5.3 `GET /v1/models`

- Con token de servicio (verificar si debe ser público). Respuesta:

```json
{
  "models": [
    {
      "name": "autogluon/chronos-2-small",
      "version": "2.0.0",
      "loaded": true,
      "quantile_levels": [0.1, 0.5, 0.9],
      "max_prediction_length": 30,
      "parameters_millions": 28
    }
  ],
  "active": "autogluon/chronos-2-small"
}
```

- Modelos: `autogluon/chronos-2-small` (28 M, por defecto) o `amazon/chronos-bolt-small` (48 M). Se elige con `CHRONOS_MODEL_ID`. Ambos corren en CPU.

## 6. Variables de entorno

### 6.1 Backend (`caudal-backend`)

| Variable | Obligatoria | Valor de ejemplo | Descripción |
|---|---|---|---|
| `IA_SERVICE_URL` | Sí | `https://caudal-ia.example.invalid` | URL base del servicio, sin barra final |
| `IA_SERVICE_TOKEN` | Sí | `<secreto, nunca en git>` | Token de servicio actual |
| `IA_SERVICE_TOKEN_PREVIOUS` | No | `<secreto anterior>` | Solo durante una rotación. El backend no lo usa para llamar. |
| `IA_CONNECT_TIMEOUT_MS` | No (por defecto 2000) | `2000` | Timeout de conexión |
| `IA_READ_TIMEOUT_MS` | No (por defecto 10000) | `10000` | Timeout de lectura |
| `CIRCUIT_BREAKER_FAILURE_THRESHOLD` | No (por defecto 5) | `5` | Fallos seguidos para abrir el circuito |
| `CIRCUIT_BREAKER_OPEN_SECONDS` | No (por defecto 60) | `60` | Tiempo abierto antes de la prueba |
| `CIRCUIT_BREAKER_HALF_OPEN_CALLS` | No (por defecto 2) | `2` | Llamadas de prueba en semiabierto |

Los valores de negocio (horizonte, reserva, días de historia enviados a la IA) no van en variables de entorno. Vienen de `org.rule_sets` (`forecast_context_days`, `forecast_horizon_days`), según los hechos, sección 12.

### 6.2 Servicio de IA (`caudal-ia`)

| Variable | Obligatoria | Valor de ejemplo | Descripción |
|---|---|---|---|
| `IA_SERVICE_TOKEN` | Sí | `<secreto>` | Token que el servicio acepta |
| `IA_SERVICE_TOKEN_PREVIOUS` | No | `<secreto anterior>` | Segundo token aceptado durante la rotación |
| `CHRONOS_MODEL_ID` | No | `autogluon/chronos-2-small` | Modelo activo. Alternativa: `amazon/chronos-bolt-small` |
| `CHRONOS_DEVICE` | No | `cpu` | Dispositivo de inferencia |
| `FORECAST_MIN_POINTS` | No | `7` (ejemplo, verificar) | Historia mínima después del remuestreo |
| `RESAMPLE_AGGREGATION` | No | `last` | `last` o `mean` |
| `MAX_SERIES_POINTS` | No | `2000` | Límite de la serie |
| `MAX_PREDICTION_LENGTH` | No | `30` | Límite técnico del horizonte |
| `MAX_BODY_BYTES` | No | `262144` | 256 KiB |
| `API_DOCS_ENABLED` | No | `true` | Swagger en `/docs` |
| `LOG_LEVEL` | No | `INFO` | Nivel de logs |

Los nombres de la tabla 6.1 son los de `caudal-backend/.env.example`. Los de la tabla 6.2 se documentan en `.env.example` de `caudal-ia`, con marcadores, sin valores reales.

## 7. Pruebas de contrato

Ambos lados ejecutan el mismo conjunto de casos. Cada caso tiene su fixture (petición y respuesta esperada) en un directorio compartido (por definir: `contracts/forecast/v1/` en `caudal-ia`, copiado o referenciado por el backend).

| ID | Caso | Entrada | Resultado esperado | Lado |
|---|---|---|---|---|
| CT-01 | Pronóstico válido de 1 día | Ejemplo de la sección 3.1 | 200, un punto, `p10 <= p50 <= p90` | IA y backend |
| CT-02 | Horizonte máximo | `prediction_length = 30` | 200, 30 puntos consecutivos | IA y backend |
| CT-03 | Horizonte por encima del límite | `prediction_length = 31` | 400 `VALIDATION_ERROR` | IA |
| CT-04 | Horizonte cero | `prediction_length = 0` | 400 `VALIDATION_ERROR` | IA |
| CT-05 | Cuantiles distintos | `quantile_levels = [0.1, 0.9]` | 400 `VALIDATION_ERROR` | IA |
| CT-06 | Campo desconocido | Campo `foo` extra | 400 `VALIDATION_ERROR` | IA |
| CT-07 | Longitudes distintas | 7 timestamps, 6 values | 422 `INVALID_SERIES`, `LENGTH_MISMATCH` | IA |
| CT-08 | Fechas no crecientes | Dos timestamps iguales | 422 `INVALID_SERIES`, `TIMESTAMPS_NOT_INCREASING` | IA |
| CT-09 | Valor no finito | `NaN` en `values` | 422 `INVALID_SERIES`, `NOT_FINITE_VALUE` | IA |
| CT-10 | Historia insuficiente | Menos de `FORECAST_MIN_POINTS` | 422 `INSUFFICIENT_HISTORY` | IA y backend |
| CT-11 | Serie demasiado grande | 2.001 puntos | 422 `INVALID_SERIES`, `TOO_MANY_POINTS` | IA |
| CT-12 | Token faltante o incorrecto | Sin cabecera, o token distinto | 401 `UNAUTHORIZED` con `WWW-Authenticate` | IA y backend |
| CT-13 | Rotación | Token previo aceptado mientras `IA_SERVICE_TOKEN_PREVIOUS` esté definido | 200 con el token previo; 401 después de quitarlo | IA |
| CT-14 | Modelo no listo | `/v1/ready` antes de cargar el modelo | 503 `MODEL_NOT_READY` con `Retry-After` | IA |
| CT-15 | Timeout del backend | La IA tarda más de 10 s | Respaldo `IA_TIMEOUT`, `is_fallback = true` | Backend |
| CT-16 | Circuito abierto | 5 fallos seguidos | Respaldo `CIRCUIT_OPEN` sin llamar a la IA | Backend |
| CT-17 | Recuperación del circuito | Tras 60 s, 2 llamadas de prueba exitosas | Circuito `CLOSED` | Backend |
| CT-18 | Respuesta inválida | `p10 > p50` en la respuesta | Respaldo `IA_ERROR` | Backend |
| CT-19 | Número de puntos incorrecto | 2 puntos para `prediction_length = 1` | Respaldo `IA_ERROR` | Backend |
| CT-20 | Error no contable | 422 `INVALID_SERIES` desde la IA | Respaldo `IA_ERROR` sin abrir el circuito | Backend |
| CT-21 | Salud sin autenticación | `GET /v1/health` sin token | 200 `{"status": "ok"}` | IA |
| CT-22 | Respuesta tolerante | La IA agrega un campo nuevo en la respuesta | El backend lo ignora y guarda los puntos | Backend |

Reglas de las pruebas:

- Las pruebas de la IA usan `TestClient` de FastAPI y el modelo real o un doble de prueba (propuesta: un doble determinista en CI para la forma de la respuesta, y una prueba de humo con el modelo real fuera de CI).
- Las pruebas del backend usan un servidor HTTP de prueba que devuelve las respuestas de los fixtures (propuesta: WireMock o un servidor de Testcontainers). No se llama al servicio real en CI.
- Un cambio en el contrato rompe primero estas pruebas, no producción.

## 8. Versionado del contrato

- La versión está en la ruta: `/v1`. Un cambio incompatible (quitar campos, cambiar tipos o significados, cambiar la frecuencia) pasa a `/v2`, y el backend migra en un cambio coordinado.
- Cambios aditivos (campos nuevos en la respuesta) no cambian la versión. El backend ignora campos desconocidos en las respuestas. El servicio rechaza campos desconocidos en las peticiones: así el backend no puede enviar algo que la IA no entiende.
- `model.version` cambia cada vez que cambia el modelo o su configuración. No es la versión del contrato.
- Cada cambio al contrato se registra en el changelog del repositorio `caudal-ia` y en este documento, en la sección de cambios.

### 8.1 Historial de cambios

| Versión del contrato | Fecha | Cambio |
|---|---|---|
| `v1` | Por definir (Fase 6) | Primera versión: pronóstico diario con cuantiles p10, p50 y p90 |

Relacionados: [API.md](API.md), [Reglas-de-negocio.md](Reglas-de-negocio.md), [Protocolo-de-dispositivos.md](Protocolo-de-dispositivos.md), [Diccionario-de-datos.md](Diccionario-de-datos.md)
