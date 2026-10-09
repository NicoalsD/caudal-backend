# Arquitectura operativa del backend (para agentes)

> Versión operativa de [Arquitectura](../docs/Arquitectura.md). Responde a una sola pregunta: dónde va cada cosa y cómo se llama. Si esta guía y la documentación canónica no coinciden, gana la documentación canónica y se corrige esta guía.

## 1. Paquetes y dónde va cada cosa

Paquete base: `co.caudal`. Las capas dependen así: `api` → `application` → `domain`, e `infrastructure` implementa los puertos de `application`. `shared` es transversal y no depende de nadie.

| Quiero agregar... | Paquete | Nombre de la clase | Notas |
|---|---|---|---|
| Un endpoint REST | `co.caudal.api.controller` | `*Controller` | Solo recibe, valida forma, llama un caso de uso y devuelve. Sin lógica. |
| Un DTO de entrada | `co.caudal.api.dto.request` | `*Request` (`record`) | Anotaciones de Bean Validation con límites de `FieldLimits`. |
| Un DTO de salida | `co.caudal.api.dto.response` | `*Response` (`record`) | Nunca expone entidades JPA ni objetos del dominio. |
| Un mapeo DTO a entrada de caso de uso | `co.caudal.api.mapper` | `*Mapper` | Convierte `*Request` en `*Input`, y resultados en `*Response`. |
| Una excepción de negocio | `co.caudal.domain.<área>` (extiende `DomainException`, que vive en `co.caudal.shared.error`) | `*Exception` | Cada excepción lleva un `ErrorCode`. El mapeo a HTTP vive en `ApiExceptionHandler` (`co.caudal.api.error`), que solo conoce `DomainException` y `ErrorCode`. |
| Un caso de uso | `co.caudal.application.usecase.<area>` | `*UseCase` | Clase con método `execute(*Input)`. Usa puertos y el dominio. Sin Spring Web, sin JPA. |
| Entrada de un caso de uso | `co.caudal.application.usecase.<area>` | `*Input` (`record`) | Datos ya validados en forma. |
| Un puerto de salida | `co.caudal.application.port.out` | `*Port` (interfaz) | Describe lo que el caso de uso necesita del mundo exterior. |
| Un comando de la Junta | `co.caudal.application.command` | `*Command` (`record`) que implementa `Command` | Solo para decisiones de la Junta (P13). |
| Un handler de comando | `co.caudal.application.command` | `*CommandHandler` | Implementa `CommandHandler<C>`. |
| Un evento de dominio | `co.caudal.domain.event` | Nombre en inglés, tiempo pasado (`ReadingAccepted`) | Lo registra el agregado. Lo publica el caso de uso por `DomainEventPublisher`. |
| Una regla de negocio pura | `co.caudal.domain.<área>` | Nombre del concepto (`RuleSet`, `ReadingValidationHandler`) | Java puro. Sin anotaciones de Spring, JPA ni Jackson. |
| Una entidad del modelo de negocio | `co.caudal.domain.<área>` | Nombre del concepto | Inmutable cuando sea posible. Los cambios devuelven una nueva instancia o registran un evento. |
| Un value object | `co.caudal.domain.<área>` | Nombre del concepto (`GaugeValue`, `ServiceDate`) | `record` con validación en el constructor compacto. |
| Un adaptador de salida (BD) | `co.caudal.infrastructure.persistence` | `*PersistenceAdapter` | Implementa un `*Port`. Usa `*JpaRepository` y convierte `*Entity` a dominio. |
| Una entidad JPA | `co.caudal.infrastructure.persistence` | `*Entity` | Solo en infraestructura. Nunca sale de ella. |
| Un repositorio Spring Data | `co.caudal.infrastructure.persistence` | `*JpaRepository` | Solo en infraestructura. Consultas parametrizadas. |
| Un adaptador de un servicio externo | `co.caudal.infrastructure.<servicio>` | `*Adapter` | Por ejemplo `IaForecastClientAdapter`. Traduce el contrato externo. |
| Un proxy o un respaldo | `co.caudal.infrastructure.<área>` | `*Proxy` | Por ejemplo `RemoteForecasterProxy` o `PublicScheduleProxy`. Implementa un `*Port`. El respaldo de pronóstico es un `*Adapter` (`NaivePersistenceForecastAdapter`). |
| Una constante de límite | `co.caudal.shared.FieldLimits` | Constante `public static final` | Única fuente de longitudes y patrones. Alimenta anotaciones, Flyway y `/meta/constraints`. |
| Un valor de negocio (horas, reserva, rango) | Base de datos (tabla de reglas) | Columna en `org.rule_sets` o en sus hijos | Nunca como constante en Java. |
| Una variable de entorno | `application.yml` con `${VAR}` y clase de propiedades en `infrastructure.config` | Propiedades tipadas (`@ConfigurationProperties`) | Documentada en `.env.example`. |
| Un texto para el usuario | `src/main/resources/messages_es.properties` | Clave en inglés con punto (`reading.out_of_range`) | Interpolación con `{0}` y similares. |
| Una migración | `src/main/resources/db/migration` | `V<n>__<descripcion>.sql` | Nunca editar una migración aplicada. |

## 2. Convenciones de nombres

| Tipo | Patrón | Ejemplo |
|---|---|---|
| Controlador | `*Controller` | `ReadingController` |
| Entrada HTTP | `*Request` | `CreateReadingRequest` |
| Salida HTTP | `*Response` | `ReadingResponse` |
| Entrada de caso de uso | `*Input` | `RecordReadingInput` |
| Caso de uso | `*UseCase` | `RecordReadingUseCase` |
| Puerto de salida | `*Port` | `ReadingPort`, `ForecastPort` |
| Adaptador | `*Adapter` | `ReadingPersistenceAdapter`, `IaForecastClientAdapter` |
| Repositorio JPA | `*JpaRepository` | `ReadingJpaRepository` |
| Entidad JPA | `*Entity` | `ReadingEntity` |
| Comando de la Junta | `*Command` | `ApproveProposalCommand` |
| Handler de comando | `*CommandHandler` | `ApproveProposalCommandHandler` |
| Evento de dominio | Concepto en pasado | `ReadingAccepted`, `SchedulePublished` |
| Excepción de negocio | `*Exception` | `ProposalStateException` |
| Prueba | `<Clase>Test` (unitaria), `<Clase>IT` (integración), `<Clase>WebTest` (slice web) | `RecordReadingUseCaseTest`, `ReadingPersistenceAdapterIT` |

Reglas de idioma: código en inglés. Textos de error para el usuario y documentación en español. Los códigos de error (`ErrorCode`) están en inglés y en mayúsculas.

## 3. Reglas que siempre aplican

1. El dominio no importa nada de `org.springframework`, `jakarta.persistence` ni `com.fasterxml.jackson`. La prueba `ARCH-01` lo verifica.
2. Un controlador no llama a un repositorio ni a un adaptador. Solo llama a un caso de uso o a una fachada.
3. Un caso de uso no abre transacciones directamente. Usa `UnitOfWork`.
4. Toda escritura pasa por un caso de uso. Ningún adaptador decide reglas de negocio.
5. El acueducto de la sesión se fija dentro de la transacción con `SET LOCAL app.aqueduct_id`. No se fija a nivel de conexión.
6. Los eventos de dominio se publican después del commit, nunca dentro de la transacción.
7. Las constantes técnicas y de seguridad van en `FieldLimits`. Los parámetros de negocio van en la base de datos. Las dos listas no se mezclan.
8. Ninguna clase de dominio usa la hora del sistema. Recibe un `java.time.Clock`.
9. Las excepciones de negocio llevan un `ErrorCode` y el mensaje va en `messages_es.properties`.
10. Un patrón de diseño se implementa con su interfaz y su prueba. El Javadoc incluye `@pattern` con el identificador (ver [Patrones para agentes](design-patterns.md)).

## 4. Checklist para agregar un endpoint

Usar esta lista en orden. Cada punto es un commit lógico (ver `CONTRIBUTING.md`).

1. **Contrato.** Confirmar método, ruta, permiso y errores en la especificación (`/api/v1/...`). Si no existe en la especificación, preguntar antes de inventarlo.
2. **Permiso.** Verificar que el permiso existe en `iam.permissions` y está asignado en `iam.role_permissions`. Si falta, agregarlo con una migración de datos (no en código).
3. **Límites.** Si el endpoint recibe texto o identificadores nuevos, agregar sus límites a `FieldLimits` y su prueba de deriva.
4. **Dominio.** Agregar o cambiar reglas en `co.caudal.domain.<área>` con pruebas unitarias sin Spring.
5. **Puerto.** Declarar en `application.port.out` lo que el caso de uso necesita (si no existe).
6. **Caso de uso.** Crear `*UseCase` con su `*Input`. Probarlo con puertos simulados (`*UseCaseTest`).
7. **Persistencia.** Crear o ajustar `*PersistenceAdapter`, `*JpaRepository` y `*Entity`. Probarlo con Testcontainers (`*AdapterIT`). Si hay tabla nueva, la migración va primero.
8. **Request y response.** Crear `*Request` con anotaciones y `*Response` como `record`. Mapper en `api.mapper`.
9. **Controlador.** Crear o ajustar `*Controller` con `@Operation`, `@Tag` y `@SecurityRequirement` según [Documentación de API](api-documentation.md).
10. **Pruebas de API.** `@WebMvcTest` para validación (422), autenticación (401), permiso (403) y el caso feliz (201 o 200).
11. **Errores.** Si hay una excepción nueva, agregar su código y su mensaje en español, y la prueba que verifica el formato canónico.
12. **Auditoría.** Si la acción cambia datos de la Junta o de la seguridad, registrar la entrada en `audit.audit_log` (vía el puerto de auditoría).
13. **Evento.** Si el cambio debe disparar efectos, registrar el evento de dominio y probar que se publica después del commit.
14. **Arquitectura.** Ejecutar `ArchitectureTest`.
15. **Documentación.** Actualizar la tabla de endpoints de la especificación si el contrato cambió, y el `.env.example` si hay variables nuevas.
16. **Cobertura.** Verificar que el dominio sigue en 90 % o más de cobertura de líneas ([Plan de pruebas](testing-plan.md)).

## 5. Qué no hacer

- No llamar a `Instant.now()`, `LocalDate.now()` ni `System.currentTimeMillis()` fuera de `SystemClock` o del reloj inyectado.
- No devolver una entidad JPA desde un controlador.
- No poner textos en español dentro del código. Van en `messages_es.properties`.
- No usar `SELECT *` ni concatenar SQL. Solo consultas parametrizadas.
- No borrar datos de lecturas, decisiones, ejecuciones ni auditoría. Usar una corrección o un reemplazo (`supersedes_id`).
- No usar `@Transactional` en el dominio.
- No crear una clase `Util` o `Helper` con lógica de negocio. La lógica va en un concepto con nombre.

Relacionados: [Arquitectura](../docs/Arquitectura.md), [Patrones para agentes](design-patterns.md), [Plan de pruebas](testing-plan.md), [Documentación de API](api-documentation.md)
