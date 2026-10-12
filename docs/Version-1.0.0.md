# Versión 1.0.0 del backend

## Propósito

Resume el estado del backend al cerrar la Fase 12 (endurecimiento, despliegue y `v1.0.0`): qué controles se endurecieron, cómo se verificaron, qué se agregó fuera del [plan de commits](Plan-de-commits.md) y qué falta para poner la etiqueta.

## Alcance

Las fases 1 a 12 del backend están completas: fundaciones, base de datos (7 esquemas), seguridad y login con MFA, reglas y red, lecturas e importación de datos simulados, pronóstico con respaldo, turnos y decisión de la Junta, publicación y reportes de daño, cierre y actas, evaluación de la IA, dispositivos firmados con Ed25519 y endurecimiento. Los endpoints están en [API](API.md) y en Swagger (`/swagger-ui.html`).

## Endurecimiento

| Control | Cómo se verifica | Prueba |
|---|---|---|
| Toda ruta privada responde 401 sin credenciales; las de dispositivos exigen firma | Recorre todas las rutas registradas en Spring MVC (más de 50) y exige 401, salvo las públicas documentadas | `EndpointAuthorizationIT` |
| Aislamiento por acueducto con RLS forzada | Un usuario de otro acueducto no lee ni modifica tanques, dispositivos ni válvulas ajenos, no ve su bitácora ni sus eventos de seguridad, y un dispositivo ajeno no ve ni confirma sus comandos | `AqueductIsolationIT` |
| Ningún secreto en logs ni en errores | Contraseñas (login fallido, cambio y restablecimiento), JWT de acceso, refresh, material de claves Ed25519 y firmas no aparecen en la salida capturada ni en los cuerpos de error; los errores no exponen trazas, SQL, nombres de clases ni la URL de JDBC | `SecretRedactionIT` |
| Actuator sin `env` ni `configprops` | Las dos rutas no están expuestas | `SecretRedactionIT` |
| Nonce de dispositivo de un solo uso | El nonce se guarda aunque la petición firmada se rechace, para que no se pueda reintentar | `DeviceTelemetryIT` |
| Tamaño máximo del cuerpo | Peticiones mayores al límite de `FieldLimits` responden 413 | `RequestBodySizeIT` |
| Bloqueo por cobertura | JaCoCo vuelve a fallar el build si la cobertura de líneas baja de 90 % en el dominio o de 80 % en total (se había desactivado para la demo) | `./mvnw verify` |
| Dependencias vulnerables | El build falla con dependencias con CVE conocidas | Workflow de CI |

La política completa está en [Seguridad](Seguridad.md) y [Seguridad de la base de datos](Seguridad-de-la-base-de-datos.md).

## Verificación

Resultado de `./mvnw verify` sobre la rama `feature/evaluacion` (2026-10-12), con Testcontainers y PostgreSQL 18:

| Medida | Valor |
|---|---|
| Pruebas unitarias | 703, sin fallos |
| Pruebas de integración | 371, sin fallos |
| Cobertura de líneas del dominio | 99,2 % (mínimo 90 %) |
| Cobertura de líneas global | 96,7 % (mínimo 80 %) |
| Checkstyle, SpotBugs con FindSecBugs, Spotless | Sin hallazgos |
| ArchUnit (capas hexagonales) | En verde |

## Despliegue

- Imagen Docker de producción en dos etapas (JDK para compilar, JRE para ejecutar), con imágenes fijadas por digest y usuario sin privilegios (`10001`).
- La API se despliega en Render desde `develop` (workflow de CI) y usa la base `caudal` del proyecto Neon con TLS en JDBC. Detalle: [`.agents/deployment.md`](../.agents/deployment.md).
- La aplicación se conecta con el rol `caudal_app`, sujeto a RLS; las migraciones corren con el rol dueño.

## Cambios fuera del plan de commits

Durante la implementación se agregaron unidades que no estaban en el plan porque [API](API.md) ya las documentaba o porque las pruebas encontraron errores:

| Área | Cambios |
|---|---|
| Incidentes | `GET /api/v1/incidents`, `POST /api/v1/incidents` del operador, `PATCH /api/v1/incidents/{id}/status`; el fontanero avanza incidentes sin poder descartarlos |
| Resúmenes compartidos | `/api/v1/summary-shares` y `GET /api/v1/summaries` para entidades |
| Auditoría | `GET /api/v1/audit-log`, `GET /api/v1/security-events`, `PUT /api/v1/retention-policies/{data_class}` |
| Válvulas | `POST /api/v1/valve-commands/manual` y comandos generados al publicar los turnos |
| Red y cuentas | Sectores y válvulas, red compuesta con `SectorNode` y `ValveNode`, `GET /api/v1/users`, `reset-grants`, `switch-aqueduct`, aviso de privacidad |
| Correcciones | Cookie de sesión con CSRF, límites de `/meta/constraints` por grupo, tamaño de página en los casos de uso, hallazgos de SpotBugs y ArchUnit |

## Pendiente para la etiqueta `v1.0.0`

1. Fusionar en `develop`, en orden, las ramas apiladas de las fases 4 a 12 (ver [flujo de trabajo](../.agents/workflow.md)).
2. Implementar el frontend (`F001` en adelante) y cerrar las fases de `caudal-ia` y `caudal-simulador`, porque la versión es del sistema completo.
3. Revisar que Swagger no exponga datos en producción (pendiente anotado en [Seguridad](Seguridad.md)).
4. PR `develop → main`, etiqueta `v1.0.0` y release con notas en español.
