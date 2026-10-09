# Documentación de la API (OpenAPI y Swagger)

> Reglas para documentar `/api/v1` con springdoc-openapi 3.x. La documentación es parte del contrato: el frontend genera sus tipos desde ella, así que un endpoint sin documentar no está terminado. Descripciones en español. Identificadores en inglés.

## 1. Configuración base

| Elemento | Valor | Estado |
|---|---|---|
| Biblioteca | springdoc-openapi 3.x, con soporte para Spring Boot 4.1.x | Verificar el artefacto exacto y su compatibilidad. |
| Swagger UI | `/swagger-ui.html` | Canónico. |
| Documento OpenAPI | `/v3/api-docs` (JSON) | Canónico. |
| Apagado | `API_DOCS_ENABLED=false` desactiva ambas rutas | Canónico. |
| Activo en todos los entornos | Sí. Solo documenta; ejecutar requiere JWT. | Canónico. |

Propiedades previstas en `application.yml` (verificar nombres con la versión instalada):

```yaml
springdoc:
  api-docs:
    enabled: ${API_DOCS_ENABLED:true}
    path: /v3/api-docs
  swagger-ui:
    enabled: ${API_DOCS_ENABLED:true}
    path: /swagger-ui.html
```

El bean `OpenApiConfig` (en `co.caudal.infrastructure.config`) define:
- `info`: título "CAUDAL API", descripción en español, versión tomada del `pom.xml`.
- `servers`: una entrada por entorno, con URL de Render y `http://localhost:8080` para local.
- `components.securitySchemes`: los tres esquemas de la sección 2.
- `OpenApiCustomizer`: agrega las respuestas de error comunes (sección 5) a cada operación.

## 2. Esquemas de seguridad

Hay tres modos. Cada endpoint declara cuál usa con `@SecurityRequirement`. Un endpoint sin declaración se considera un error de revisión.

| Modo | Nombre del esquema | Endpoints | Declaración |
|---|---|---|---|
| JWT bearer | `bearerAuth` | Casi todos los de `/api/v1`, incluidos los de la Junta, el fontanero, el equipo y la importación. | `@SecurityRequirement(name = "bearerAuth")` |
| Sin autenticación | (ninguno) | `/api/v1/public/**`, `/api/v1/auth/login`, `/api/v1/auth/refresh` (usa cookie), `/actuator/health`, documentación. | `@SecurityRequirements` vacío. |
| Firma de dispositivo Ed25519 | `deviceSignature` | `/api/v1/devices/telemetry`, `/api/v1/devices/commands`, `/api/v1/devices/commands/{id}/ack`. | `@SecurityRequirement(name = "deviceSignature")` |

Detalle de cada esquema:

- `bearerAuth`: `type: http`, `scheme: bearer`, `bearerFormat: JWT`. Descripción: "Access token de 15 minutos. Se obtiene con `POST /api/v1/auth/login`."
- `deviceSignature`: `type: apiKey`, `in: header`, con las cabeceras `X-Device-Id`, `X-Timestamp`, `X-Nonce` y `X-Signature`. Como springdoc modela un solo `name` por esquema `apiKey`, la descripción debe listar las cuatro cabeceras. Alternativa: documentar las cuatro como parámetros de cabecera en cada operación (decisión por tomar, verificar). La cadena firmada y el formato de cada cabecera están en `../docs/Protocolo-de-dispositivos.md`.
- El refresh token viaja en cookie `HttpOnly` (nombre `caudal_rt`). Se documenta el endpoint `POST /api/v1/auth/refresh` sin esquema, con una descripción que explique la cookie. No se modela como `apiKey` para que Swagger UI no pida un valor que el navegador no puede mostrar.

## 3. Reglas por operación

Cada operación del controlador debe tener:

| Anotación | Obligatoria | Contenido |
|---|---|---|
| `@Operation(summary, description)` | Sí | `summary` corto en español (máximo 60 caracteres). `description` explica la regla de negocio, el efecto y la idempotencia si aplica. |
| `@Tag` (en la clase) | Sí | Un tag por recurso, en español (ver sección 4). |
| `@SecurityRequirement` o `@SecurityRequirements` vacío | Sí | Según la tabla de la sección 2. |
| `@ApiResponse` para el caso feliz | Sí | Código correcto: `200`, `201` o `204`. Con el esquema de respuesta. |
| `@ApiResponse` para cada error posible | Sí | Solo los que el endpoint puede devolver. Los comunes vienen del `OpenApiCustomizer`. |
| `@Parameter(description, example)` | Sí, en parámetros de ruta y de consulta | Descripción en español y un ejemplo que no sea dato real. |

Ejemplo de referencia:

```java
@Operation(
        summary = "Registrar una lectura del tanque",
        description = "Registra la lectura que toma el fontanero en la regla pintada. "
                + "Es idempotente por el UUID que envía el cliente: repetir la misma "
                + "petición devuelve el resultado original. Si el valor queda fuera de la "
                + "regla, la API responde 422 GAUGE_OUT_OF_RANGE y la lectura no se guarda.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "201", description = "Lectura registrada",
        content = @Content(schema = @Schema(implementation = ReadingResponse.class)))
@ApiResponse(responseCode = "409", description = "El UUID ya existe con otro contenido")
@PostMapping("/api/v1/readings")
public ResponseEntity<ReadingResponse> create(@Valid @RequestBody CreateReadingRequest request) {
    // delegates to RecordReadingUseCase
    return null;
}
```

Reglas:
- Las descripciones van en español, en tono directo: qué hace, qué regla aplica, qué devuelve.
- No se describe la implementación ("usa JPA"). Se describe el comportamiento.
- No se repite el código de error como única descripción. El código y el mensaje se documentan en la respuesta.

## 4. Tags por recurso

Los nombres de tag son etiquetas visibles y van en español.

| Tag | Recurso (prefijo) | Seguridad predominante |
|---|---|---|
| Autenticación | `/api/v1/auth` | Mixta (login sin auth, resto JWT). |
| Usuarios | `/api/v1/users`, `/api/v1/memberships` | JWT. |
| Metadatos y catálogos | `/api/v1/meta`, `/api/v1/catalogs` | JWT. |
| Aviso de privacidad | `/api/v1/privacy-notice` | JWT. |
| Red | `/api/v1/tanks`, `/api/v1/sectors`, `/api/v1/valves` | JWT. |
| Reglas | `/api/v1/rule-sets` | JWT. |
| Lecturas | `/api/v1/readings` | JWT. |
| Estado y pronóstico | `/api/v1/tanks/{id}/status`, `/api/v1/tanks/{id}/forecasts` | JWT. |
| Anomalías | `/api/v1/anomalies` | JWT. |
| Propuestas de turnos | `/api/v1/schedule-proposals` | JWT. |
| Cierre del día | `/api/v1/schedule-items`, `/api/v1/days` | JWT. |
| Incidentes | `/api/v1/incidents` | JWT (la creación pública está en Público). |
| Actas y resúmenes | `/api/v1/minutes`, `/api/v1/summary-shares`, `/api/v1/summaries` | JWT. |
| Evaluación | `/api/v1/forecast-evaluation` | JWT. |
| Público | `/api/v1/public` | Sin autenticación. |
| Dispositivos | `/api/v1/devices`, `/api/v1/valve-commands` | Firma Ed25519 y JWT (administración). |
| Importación simulada | `/api/v1/imports` | JWT, solo `PROJECT_TEAM`, solo acueducto demo. |
| Auditoría | `/api/v1/audit-log`, `/api/v1/security-events` | JWT. |
| Salud | `/actuator/health` | Sin autenticación. |

## 5. Respuestas de error

Todas las respuestas de error usan el formato canónico:

```json
{
  "error": {
    "code": "GAUGE_OUT_OF_RANGE",
    "message": "El valor 8,00 está fuera de la regla del tanque (0 a 5).",
    "details": { "min": "0", "max": "5" },
    "request_id": "7f0c2a1e-5b8d-4c1f-9a3e-2d6b8e4f1a90"
  }
}
```

El esquema `ErrorResponse` se define una vez en `api.error` con `@Schema(description = "...")` y un ejemplo. Las respuestas comunes que `OpenApiCustomizer` agrega a cada operación:

| HTTP | Descripción en Swagger | Ejemplo de `code` |
|---|---|---|
| `401` | Token ausente, inválido o vencido. | `UNAUTHORIZED` |
| `403` | Sin permiso para esta acción o recurso. | `FORBIDDEN` |
| `400` | Formato, tipo, longitud, campo desconocido o JSON mal formado. | `VALIDATION_ERROR` |
| `404` | Recurso inexistente o fuera del acueducto. | `NOT_FOUND` |
| `409` | Conflicto de versión, de estado o de idempotencia. | `CONFLICT`, `INVALID_STATE_TRANSITION`, `DUPLICATE_READING` |
| `413` | Cuerpo demasiado grande. | `PAYLOAD_TOO_LARGE` |
| `422` | Regla de negocio no cumplida. | `GAUGE_OUT_OF_RANGE`, `REASON_REQUIRED`, `RULE_SET_INVALID` |
| `429` | Límite de peticiones alcanzado. | `RATE_LIMITED` |
| `500` | Error interno. Sin detalles. | `INTERNAL_ERROR` |

Los códigos específicos de cada endpoint se documentan en su `@ApiResponse` con su descripción. Los códigos y sus estados HTTP están en `../docs/API.md` (sección de errores). Esta guía no define códigos nuevos.

Regla de seguridad: el campo `message` nunca revela si un usuario existe. El login responde "Usuario o contraseña incorrectos" en todos los casos.

## 6. Esquemas y ejemplos

- Cada DTO (`*Request`, `*Response`) es un `record` con `@Schema(description = "...")` en el tipo y en cada componente.
- Los ejemplos se escriben con `@Schema(example = "...")` o con `@ExampleObject` en la respuesta.
- Los ejemplos no contienen datos reales. Usan valores de prueba evidentes:
  - Nombres: "Persona de Prueba".
  - Usuarios: `test.operator`.
  - Acueducto: "Vereda de Prueba", slug `prueba-vereda`.
  - UUID: `00000000-0000-7000-8000-000000000001` (formato válido, sin significado).
  - Teléfonos: no aparecen en ningún ejemplo.
  - Fechas: fechas fijas de ejemplo, en ISO-8601 con zona (`2026-10-09T14:30:00Z`).
- Los valores numéricos de niveles usan coma en la UI, pero en JSON son números con punto (`2.1`).
- Los campos desconocidos se rechazan en el servidor. La documentación no incluye campos que la API no acepta.
- Los campos de contraseña y de token se documentan con `writeOnly` o con descripción que indique que no se devuelven.
- Los campos de cadena se documentan con `maxLength` y `minLength` tomados de `FieldLimits`, no de un número escrito a mano.

## 7. Paginación

Todos los listados usan cursor opaco:

| Parámetro | Tipo | Límites | Descripción |
|---|---|---|---|
| `limit` | entero | 1 a 100, por defecto 20 | Tamaño de la página. |
| `cursor` | cadena | Opaca, firmada | Cursor de la página siguiente. Se devuelve en la respuesta. |

La respuesta de un listado tiene `items` y `nextCursor` (nulo si no hay más). Los parámetros se documentan con `@Parameter` y los límites en la descripción.

## 8. Tipos para el frontend

El frontend genera sus tipos con `openapi-typescript` a partir del documento de la API:

```bash
npx openapi-typescript https://<api>/v3/api-docs -o src/api/schema.d.ts
```

Para trabajar sin conexión a la API, el documento se guarda como archivo y se genera desde ahí:

```bash
npx openapi-typescript ./docs/openapi.json -o src/api/schema.d.ts
```

Reglas (propuestas, verificar):
- `docs/openapi.json` es una copia del documento generada por una prueba (`OpenApiSnapshotIT`) y se versiona en el repositorio del backend.
- Un cambio en la API que modifique el documento exige actualizar la copia en el mismo PR. Una prueba falla si las dos no coinciden.
- El frontend no escribe tipos a mano para lo que la API expone. Los adaptadores DTO a vista (P06) usan los tipos generados.
- El archivo generado en el frontend no se edita a mano.

## 9. Publicación de `/v3/api-docs`

- El JSON se sirve desde la misma aplicación en `/v3/api-docs`. No hay un archivo estático separado.
- `/v3/api-docs` y `/swagger-ui.html` son públicos (solo documentación). Ejecutar peticiones desde Swagger requiere JWT.
- Con `API_DOCS_ENABLED=false` la ruta responde `404`.
- La política de cabeceras de Swagger UI debe ser distinta de la de la API (ver [Despliegue](deployment.md), sección 3.3).
- Cada despliegue publica el documento con la versión de la aplicación. La versión sale de `pom.xml` y de la etiqueta de release (`vX.Y.Z`).

## 10. Lista de verificación de un endpoint nuevo

- [ ] `@Operation` con `summary` y `description` en español.
- [ ] `@Tag` del recurso, de la sección 4.
- [ ] `@SecurityRequirement` o `@SecurityRequirements` vacío, según la sección 2.
- [ ] Respuesta de éxito con esquema.
- [ ] Respuestas de error específicas documentadas.
- [ ] Parámetros con `@Parameter` y ejemplo no real.
- [ ] Ejemplos sin datos personales ni valores reales.
- [ ] La copia `docs/openapi.json` se regeneró en el mismo PR.
- [ ] Una prueba de `@WebMvcTest` verifica el código de éxito y el formato de error.

Relacionados: [Arquitectura operativa](architecture.md), [Plan de pruebas](testing-plan.md), [Despliegue del backend](deployment.md), [Arquitectura](../docs/Arquitectura.md)
