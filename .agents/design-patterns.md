# Patrones de diseño del backend (guía operativa para agentes)

> Versión operativa de [Patrones de diseño](../docs/Patrones-de-diseno.md). Para cada patrón del backend da el esqueleto de interfaz en Java y las reglas que deben cumplirse. Identificadores y comentarios en inglés. Textos de esta guía en español.

## 1. Reglas generales

1. **Implementación explícita.** Cada patrón tiene sus propias interfaces y clases, aunque Spring, Java o Lombok ofrezcan un equivalente. La razón está en la [introducción del catálogo](../docs/Patrones-de-diseno.md#11-regla-implementación-explícita-aunque-el-framework-ya-lo-ofrezca).
2. **Una prueba por patrón.** Cada patrón tiene al menos una prueba unitaria sin Spring con el nombre indicado en su sección. Un patrón sin prueba no se integra.
3. **Javadoc con `@pattern`.** La clase principal del patrón lleva esta etiqueta en su Javadoc:

   ```java
   /**
    * Proposal lifecycle as explicit states with guarded transitions.
    *
    * @pattern P18 State
    */
   ```

4. **Sin frameworks en el dominio.** Las clases de `co.caudal.domain` no usan anotaciones de Spring, JPA ni Jackson (`ARCH-01`).
5. **Sin reloj del sistema.** Ninguna clase de patrón llama a `Instant.now()`. Recibe un `java.time.Clock`, salvo `SystemClock`.
6. **Patrones en la plantilla de PR.** Todo PR que agregue un patrón lo lista en la sección "Patrones de diseño involucrados".
7. **Identificadores fijos.** El identificador `Pnn` del catálogo no cambia. Si un patrón se elimina, se documenta en el catálogo antes de quitarlo del código.

## 2. Mapa de patrones del backend

| ID | Patrón | Paquete | Prueba |
|---|---|---|---|
| P01 | Singleton | `co.caudal.shared.time` | `SystemClockTest` |
| P02 | Factory Method | `co.caudal.infrastructure.document` | `DocumentGeneratorCreatorTest` |
| P03 | Abstract Factory | `co.caudal.infrastructure.document` | `DocumentFactoryTest` |
| P04 | Builder | `co.caudal.domain.schedule`, `co.caudal.domain.minutes` | `ScheduleProposalBuilderTest`, `MinutesBuilderTest` |
| P05 | Prototype | `co.caudal.domain.rules` | `RuleSetPrototypeTest` |
| P06 | Adapter | `co.caudal.infrastructure.forecast`, `co.caudal.infrastructure.device` | `IaForecastClientAdapterTest`, `DeviceTelemetryAdapterTest` |
| P07 | Bridge | `co.caudal.application.publication`, `co.caudal.infrastructure.publication` | `NoticeChannelBridgeTest` |
| P08 | Composite | `co.caudal.domain.network` | `NetworkNodeTest` |
| P09 | Decorator | `co.caudal.application.command` | `AuditingCommandHandlerTest` |
| P10 | Facade | `co.caudal.application.facade` | `TankStatusFacadeTest` |
| P11 | Proxy | `co.caudal.infrastructure.publication`, `co.caudal.infrastructure.forecast` | `PublicScheduleProxyTest`, `RemoteForecasterProxyTest` |
| P12 | Chain of Responsibility | `co.caudal.domain.reading.validation` | `ReadingValidationHandlerTest` |
| P13 | Command | `co.caudal.application.command` | `CommandBusTest` |
| P14 | Iterator | `co.caudal.domain.schedule` | `SectorTurnIteratorTest` |
| P16 | Memento | `co.caudal.domain.schedule` | `ProposalMementoTest` |
| P17 | Observer | `co.caudal.application.event`, `co.caudal.domain.event`, `co.caudal.infrastructure.event` | `DomainEventPublisherTest` |
| P18 | State | `co.caudal.domain.schedule`, `co.caudal.domain.tank`, `co.caudal.infrastructure.forecast` | `ProposalStateTest`, `CircuitBreakerStateTest` |
| P19 | Strategy | `co.caudal.domain.schedule`, `co.caudal.domain.tank` | `TurnAllocationStrategyTest`, `TrendStrategyTest` |
| P20 | Template Method | `co.caudal.infrastructure.document` | `DocumentGeneratorTest` |
| P21 | Visitor | `co.caudal.domain.minutes` | `MinutesSectionVisitorTest` |
| P22 | Interpreter (opcional) | `co.caudal.shared.text` | `MessageTemplateTest` |
| P23 | Flyweight (opcional) | `co.caudal.domain.catalog` | `CatalogItemFlyweightFactoryTest` |

Los nombres de clase de prueba son propuestos. Se confirman al implementar cada patrón. P15 (Mediator) es solo del frontend y no aparece aquí.

P02 aparece en esta guía aunque en el brief de la documentación no se listó: la especificación del proyecto le asigna `DocumentGeneratorCreator` en el backend, y P03 y P20 dependen de él.

## 3. P01 Singleton

```java
package co.caudal.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The only place in the backend that reads the system clock. Always UTC.
 *
 * @pattern P01 Singleton
 */
public final class SystemClock extends Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    public static SystemClock getInstance() {
        return INSTANCE;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("SystemClock is always UTC");
    }

    @Override
    public Instant instant() {
        return Instant.now();
    }
}
```

Reglas:
- `SystemClock` es la única clase autorizada para llamar a `Instant.now()`. La regla `ARCH-11` de `ArchitectureTest` lo verifica y excluye solo esta clase.
- `SystemClockTest` verifica que la zona es UTC y que `getInstance()` siempre devuelve la misma instancia.
- Los casos de uso reciben un `Clock` en el constructor y no referencian `SystemClock` directamente.

## 4. P02 Factory Method

```java
package co.caudal.infrastructure.document;

/**
 * Creates the document generator for one document kind.
 *
 * @pattern P02 Factory Method
 */
public abstract class DocumentGeneratorCreator {

    /** Factory method. Each subclass decides which generator to build. */
    protected abstract DocumentGenerator createGenerator(DocumentFactory factory);

    /** Client-facing operation. Never calls a concrete generator directly. */
    public final DocumentGenerator generatorFor(DocumentFactory factory) {
        return createGenerator(factory);
    }
}

public final class PosterGeneratorCreator extends DocumentGeneratorCreator {
    @Override
    protected DocumentGenerator createGenerator(DocumentFactory factory) {
        return new PosterGenerator(factory);
    }
}
```

Reglas:
- Hay un creador concreto por cada tipo: cartel (`PosterGeneratorCreator`), acta (`MinutesGeneratorCreator`) y resumen (`SummaryGeneratorCreator`).
- El caso de uso depende del puerto `DocumentRenderingPort` (en `application.port.out`). Su adaptador `DocumentRenderingAdapter` usa `DocumentGeneratorCreator`, no los generadores concretos.
- `DocumentGeneratorCreatorTest` verifica el tipo devuelto por cada creador.

## 5. P03 Abstract Factory

```java
package co.caudal.infrastructure.document;

/**
 * Family of document components. Each implementation produces one output format.
 *
 * @pattern P03 Abstract Factory
 */
public interface DocumentFactory {

    DocumentHeader createHeader(DocumentMetadata metadata);

    DocumentSection createSection(SectionContent content);

    DocumentSignatures createSignatures(List<Signatory> signatories);

    /** Output family name, for example "PDF" or "HTML". */
    String format();
}

public final class PdfDocumentFactory implements DocumentFactory {
    // Builds components with OpenPDF. Same contract as HtmlDocumentFactory.
}

public final class HtmlDocumentFactory implements DocumentFactory {
    // Builds components with escaped HTML. Same contract as PdfDocumentFactory.
}
```

Reglas:
- Un generador usa una sola fábrica durante toda su ejecución. Mezclar familias es un error.
- Las piezas HTML escapan el contenido. Nunca se usa contenido sin escapar.
- `DocumentFactoryTest` verifica que todas las piezas de una familia reportan el mismo `format()`.

## 6. P04 Builder

```java
package co.caudal.domain.schedule;

/**
 * Builds a schedule proposal and validates its invariants at build time.
 *
 * @pattern P04 Builder
 */
public final class ScheduleProposalBuilder {

    private LocalDate serviceDate;
    private RuleSet ruleSet;
    private final List<ScheduleItem> items = new ArrayList<>();

    public ScheduleProposalBuilder forDate(LocalDate serviceDate) {
        this.serviceDate = Objects.requireNonNull(serviceDate);
        return this;
    }

    public ScheduleProposalBuilder withRuleSet(RuleSet ruleSet) {
        this.ruleSet = Objects.requireNonNull(ruleSet);
        return this;
    }

    public ScheduleProposalBuilder addItem(ScheduleItem item) {
        items.add(Objects.requireNonNull(item));
        return this;
    }

    /** Validates overlaps and band hours, then returns an immutable proposal. */
    public ScheduleProposal build() {
        // 1. Required fields present.
        // 2. No two items overlap for the same sector or the same valve.
        // 3. Total hours fit the tank band of the rule set.
        // 4. Each item respects min_shift_hours and max_shift_hours.
        return new ScheduleProposal(serviceDate, ruleSet, List.copyOf(items));
    }
}
```

Reglas:
- `build()` es el único punto de validación. Una propuesta inválida nunca existe como objeto.
- Las listas se copian con `List.copyOf` para que el resultado sea inmutable.
- `ScheduleProposalBuilderTest` cubre solapes, horas fuera de banda y campos obligatorios faltantes.
- `MinutesBuilder` sigue la misma forma y exige las cuatro secciones: observado, estimado, inferido y confirmado.

## 7. P05 Prototype

```java
package co.caudal.domain.rules;

/**
 * A versioned rule set. Copying creates a new DRAFT version with the same content.
 *
 * @pattern P05 Prototype
 */
public final class RuleSet {

    public RuleSet copyForNewVersion(UUID newId, int nextVersion, UUID createdBy, Instant createdAt) {
        if (nextVersion <= this.version) {
            throw new IllegalArgumentException("nextVersion must be greater than the current version");
        }
        return new RuleSet(
                newId,                        // identifier supplied by the caller (UUIDv7 from the database or the use case)
                this.aqueductId,
                nextVersion,
                RuleSetStatus.DRAFT,
                this.id,                      // based_on_rule_set_id
                List.copyOf(this.bands),
                List.copyOf(this.sectorSettings),
                List.copyOf(this.valveOrders),
                List.copyOf(this.operatingWindows),
                this.parameters.copy(),
                createdBy,
                createdAt);
    }
}
```

Reglas:
- La copia es profunda: bandas, sectores, orden de válvulas y ventanas se copian.
- El borrador siempre nace en `DRAFT`, con `based_on_rule_set_id` apuntando al origen.
- El origen no cambia. `RuleSetPrototypeTest` lo verifica comparando antes y después.
- No se usa `Cloneable`: la copia tiene reglas de negocio.

## 8. P06 Adapter

```java
package co.caudal.application.port.out;

/** Outbound port: forecast from the AI service, in domain terms. */
public interface ForecastPort {
    ForecastResult forecast(ForecastRequest request);
}

package co.caudal.infrastructure.forecast;

/**
 * Translates the AI service contract into the domain ForecastPort.
 *
 * @pattern P06 Adapter
 */
public final class IaForecastClientAdapter implements ForecastPort {

    private final RestClient client;

    public IaForecastClientAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public ForecastResult forecast(ForecastRequest request) {
        // 1. Build the wire body: series_id, timestamps, values, prediction_length, quantile_levels.
        // 2. Call POST /v1/forecasts with the service token.
        // 3. Map each point to the domain. Reject non-increasing quantiles or non-finite values.
        // 4. Map 422, 401 and 503 to domain exceptions. Never leak the wire format.
        throw new UnsupportedOperationException("sketch");
    }
}
```

Reglas:
- El adaptador es el único que conoce el formato JSON de la IA. El dominio y el caso de uso no.
- Las respuestas se validan al convertirse: cuantiles en orden (`p10 <= p50 <= p90`), valores finitos.
- `IaForecastClientAdapterTest` usa un servidor HTTP simulado y prueba respuestas válidas, `422`, `401` y `503`.
- Los errores del adaptador son excepciones de dominio con `ErrorCode`, no excepciones HTTP.

## 9. P07 Bridge

```java
package co.caudal.application.publication;

/**
 * Abstraction: what is announced. Independent from where it is shown.
 *
 * @pattern P07 Bridge
 */
public abstract class Notice {

    private final PublicationChannel channel;

    protected Notice(PublicationChannel channel) {
        this.channel = Objects.requireNonNull(channel);
    }

    public final void publish() {
        channel.deliver(toMessage());
    }

    /** Builds the text. Personal data is never included here (see PublicScheduleProxy). */
    protected abstract NoticeMessage toMessage();
}

/** Implementor: where the notice is shown. */
public interface PublicationChannel {
    void deliver(NoticeMessage message);
}
```

Reglas:
- Un aviso nuevo es una subclase de `Notice`. Un canal nuevo es una implementación de `PublicationChannel`. Ninguna combinación exige una clase nueva.
- `toMessage()` no incluye nombres ni teléfonos. Si el acueducto es demo, el mensaje lleva la marca "Datos simulados".
- `NoticeChannelBridgeTest` prueba cada combinación principal.

## 10. P08 Composite

```java
package co.caudal.domain.network;

/**
 * A node of the distribution network: a sector (composite) or a valve (leaf).
 *
 * @pattern P08 Composite
 */
public sealed interface NetworkNode permits SectorNode, ValveNode {

    String id();

    /** Number of valves under this node, including nested sectors. */
    int valveCount();
}

public record SectorNode(String id, List<NetworkNode> children) implements NetworkNode {
    public SectorNode {
        children = List.copyOf(children);
    }

    @Override
    public int valveCount() {
        return children.stream().mapToInt(NetworkNode::valveCount).sum();
    }
}

public record ValveNode(String id) implements NetworkNode {
    @Override
    public int valveCount() {
        return 1;
    }
}
```

Reglas:
- El cliente trata a `SectorNode` y `ValveNode` del mismo modo, a través de `NetworkNode`.
- No se permiten ciclos. La construcción del árbol lo valida.
- `NetworkNodeTest` cubre un sector con subsectores, una hoja sola y un ciclo rechazado.

## 11. P09 Decorator

```java
package co.caudal.application.command;

/**
 * Adds an audit record around any command handler without changing it.
 *
 * @pattern P09 Decorator
 */
public final class AuditingCommandHandler<C extends Command> implements CommandHandler<C> {

    private final CommandHandler<C> delegate;
    private final AuditLogPort auditLog;

    public AuditingCommandHandler(CommandHandler<C> delegate, AuditLogPort auditLog) {
        this.delegate = Objects.requireNonNull(delegate);
        this.auditLog = Objects.requireNonNull(auditLog);
    }

    @Override
    public void handle(C command) {
        try {
            delegate.handle(command);
            auditLog.recordSuccess(command);
        } catch (RuntimeException failure) {
            auditLog.recordFailure(command, failure);
            throw failure;
        }
    }

    @Override
    public Class<C> commandType() {
        return delegate.commandType();
    }
}
```

Reglas:
- El decorador no cambia el resultado del handler interno. Solo agrega el registro.
- El registro de fallo no incluye el mensaje de la excepción si contiene datos sensibles.
- `AuditingCommandHandlerTest` verifica: éxito deja un registro, fallo deja un registro y relanza, y el handler interno se llama una vez.

## 12. P10 Facade

```java
package co.caudal.application.facade;

/**
 * One entry point for the tank status: last level, staleness, trend and forecast.
 *
 * @pattern P10 Facade
 */
public final class TankStatusFacade {

    private final EffectiveReadingQueryPort readings;     // reads ops.effective_readings
    private final ForecastQueryPort forecasts;            // latest forecast run and points
    private final TrendStrategy trend;                // P19
    private final Clock clock;

    public TankStatusFacade(EffectiveReadingQueryPort readings, ForecastQueryPort forecasts,
                            TrendStrategy trend, Clock clock) {
        this.readings = readings;
        this.forecasts = forecasts;
        this.trend = trend;
        this.clock = clock;
    }

    public TankStatusView statusOf(UUID tankId, RuleSetView rules) {
        // Compose the four queries. The controller never calls them directly.
        throw new UnsupportedOperationException("sketch");
    }
}
```

Reglas:
- Los controladores llaman a la fachada, no a las consultas internas.
- La fachada no llama a la IA. Solo lee el último pronóstico guardado.
- `TankStatusFacadeTest` cubre: lectura vigente, lectura vieja (aviso `STALE`) y tanque sin pronóstico.

## 13. P11 Proxy

```java
package co.caudal.infrastructure.forecast;

/**
 * Protects the AI call: opens the circuit, calls the remote adapter, and falls back.
 *
 * @pattern P11 Proxy
 */
public final class RemoteForecasterProxy implements ForecastPort {

    private final ForecastPort remote;             // IaForecastClientAdapter
    private final ForecastPort fallback;           // naive persistence estimate
    private final CircuitBreakerState circuit;     // P18

    public RemoteForecasterProxy(ForecastPort remote, ForecastPort fallback, CircuitBreakerState circuit) {
        this.remote = remote;
        this.fallback = fallback;
        this.circuit = circuit;
    }

    @Override
    public ForecastResult forecast(ForecastRequest request) {
        if (!circuit.allowsCall()) {
            return fallback.forecast(request).asFallback(FallbackReason.CIRCUIT_OPEN);
        }
        try {
            ForecastResult result = remote.forecast(request);
            circuit.recordSuccess();
            return result;
        } catch (ForecastUnavailableException failure) {
            circuit.recordFailure();
            return fallback.forecast(request).asFallback(FallbackReason.IA_ERROR);
        }
    }
}
```

Reglas:
- El proxy tiene la misma firma que el puerto. El caso de uso no sabe si hay respaldo.
- Cada respaldo registra su motivo (`IA_TIMEOUT`, `CIRCUIT_OPEN`, `IA_ERROR`, `INSUFFICIENT_HISTORY`).
- El respaldo de pronóstico es `NaivePersistenceForecastAdapter` (implementa `ForecastPort`).
- `PublicScheduleProxy` implementa el puerto de lectura pública. Quita nombres y teléfonos y exige que el horario esté publicado. `PublicScheduleProxyTest` cubre ambos casos.

## 14. P12 Chain of Responsibility

```java
package co.caudal.domain.reading.validation;

/**
 * One check in the reading validation chain. Each handler may add an issue and passes on.
 *
 * @pattern P12 Chain of Responsibility
 */
public abstract class ReadingValidationHandler {

    private ReadingValidationHandler next;

    /** Returns the next handler so the chain can be built in one expression. */
    public ReadingValidationHandler setNext(ReadingValidationHandler next) {
        this.next = next;
        return next;
    }

    public final ValidationOutcome validate(ReadingCandidate candidate, ValidationContext context,
                                            ValidationOutcome outcome) {
        ValidationOutcome current = check(candidate, context, outcome);
        if (current.hasBlockingIssue() || next == null) {
            return current;
        }
        return next.validate(candidate, context, current);
    }

    /** One check. Returns the outcome with its issue added, or unchanged. */
    protected abstract ValidationOutcome check(ReadingCandidate candidate, ValidationContext context,
                                               ValidationOutcome outcome);
}

// Order: RangeCheckHandler -> DateCheckHandler -> DuplicateCheckHandler
//        -> JumpCheckHandler -> AgeCheckHandler
```

Reglas:
- El orden de la cadena es fijo: rango, fecha, duplicado, salto brusco, antigüedad.
- `MISSING_TIMESTAMP` no es un manejador de la cadena: lo rechaza la validación de forma antes del caso de uso.
- `STALE` no genera incidencia. Solo marca el estado del tanque.
- Cada manejador tiene su prueba (`RangeCheckHandlerTest`, etc.) y `ReadingValidationHandlerTest` prueba el orden y la detención por bloqueo.

## 15. P13 Command

```java
package co.caudal.application.command;

/** Marker for board decisions. */
public interface Command {
}

public interface CommandHandler<C extends Command> {
    void handle(C command);

    Class<C> commandType();
}

public interface CommandBus {
    <C extends Command> void dispatch(C command);
}

/** Board decision: approve a proposal. Lock version guards against concurrent edits. */
public record ApproveProposalCommand(UUID proposalId, int expectedLockVersion, UUID decidedBy)
        implements Command {
}

public final class SimpleCommandBus implements CommandBus {

    private final Map<Class<?>, CommandHandler<?>> handlers;

    public SimpleCommandBus(Map<Class<?>, CommandHandler<?>> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Command> void dispatch(C command) {
        CommandHandler<C> handler = (CommandHandler<C>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler for " + command.getClass().getSimpleName());
        }
        handler.handle(command);
    }
}
```

Reglas:
- El bus solo despacha comandos de la Junta. Las lecturas van por su caso de uso.
- `RejectProposalCommand` y `ModifyProposalCommand` exigen motivo de 10 a 500 caracteres. La validación está en el handler, no en el controlador. Si falta el motivo, responde `422 REASON_REQUIRED`.
- `CommandBusTest` verifica el despacho y el error sin handler.
- Ver P09 para la auditoría de cada handler.

## 16. P14 Iterator

```java
package co.caudal.domain.schedule;

/**
 * Yields sectors in allocation order: priority rank first, then longest time without water.
 *
 * @pattern P14 Iterator
 */
public final class SectorTurnIterator implements Iterator<SectorCandidate> {

    private final Iterator<SectorCandidate> ordered;

    public SectorTurnIterator(List<SectorCandidate> candidates) {
        this.ordered = candidates.stream()
                .sorted(Comparator
                        .comparing(SectorCandidate::isPriority).reversed()
                        .thenComparing(SectorCandidate::priorityRank)
                        .thenComparing(SectorCandidate::hoursWithoutService, Comparator.reverseOrder()))
                .iterator();
    }

    @Override
    public boolean hasNext() {
        return ordered.hasNext();
    }

    @Override
    public SectorCandidate next() {
        return ordered.next();
    }
}
```

Reglas:
- El iterador no cambia la lista original.
- El orden es parte de la regla de asignación (`PRIORITY_THEN_LONGEST_WAIT`). Si cambia, cambia la estrategia (P19), no el iterador.
- `SectorTurnIteratorTest` verifica que los prioritarios salen primero y que entre los demás sale el de mayor espera.

## 17. P16 Memento

```java
package co.caudal.domain.schedule;

/**
 * Immutable snapshot of a proposal before the board changes it.
 *
 * @pattern P16 Memento
 */
public record ProposalMemento(
        UUID proposalId,
        int lockVersion,
        List<ScheduleItemSnapshot> items,
        String strategyCode,
        Instant takenAt) {

    public ProposalMemento {
        items = List.copyOf(items);
    }
}

public final class ScheduleProposal {

    public ProposalMemento createMemento(Instant takenAt) {
        return new ProposalMemento(id, lockVersion, snapshotItems(), strategyCode, takenAt);
    }
}
```

Reglas:
- El memento no tiene métodos que modifiquen la propuesta.
- El memento se guarda como `jsonb` en `ops.proposal_snapshots` antes de que la Junta cambie algo.
- `ProposalMementoTest` verifica que un cambio posterior en la propuesta no altera el memento.

## 18. P17 Observer

```java
package co.caudal.application.event;

/** Outbound port used by use cases to announce domain events. */
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}

package co.caudal.domain.event;

public interface DomainEvent {
    Instant occurredAt();
}

public record ReadingAccepted(UUID readingId, UUID tankId, Instant occurredAt) implements DomainEvent {
}

package co.caudal.infrastructure.event;

/**
 * Delivers events through Spring after the transaction commits.
 *
 * @pattern P17 Observer
 */
public final class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
```

Reglas:
- Los oyentes usan `@TransactionalEventListener(phase = AFTER_COMMIT)`. Un evento nunca se entrega a una transacción revertida.
- Los nombres de evento están en inglés y en pasado: `ReadingAccepted`, `RuleSetActivated`, `SchedulePublished`.
- `DomainEventPublisherTest` verifica la entrega después del commit y que una transacción revertida no entrega nada.

## 19. P18 State

```java
package co.caudal.domain.schedule;

/**
 * Proposal lifecycle. Each state decides which transitions are legal.
 *
 * @pattern P18 State
 */
public interface ProposalState {

    String code();

    /** Creation: the proposal is born in PENDING_REVIEW (DRAFT is internal only). */
    ProposalState submitForReview();

    /** PENDING_REVIEW -> APPROVED. */
    ProposalState approve();

    /** PENDING_REVIEW -> APPROVED_WITH_CHANGES. Requires a reason from 10 to 500 characters. */
    ProposalState approveWithChanges(String reason);

    /** PENDING_REVIEW -> REJECTED. REJECTED is terminal. Requires a reason. */
    ProposalState reject(String reason);

    /** APPROVED or APPROVED_WITH_CHANGES -> PUBLISHED. */
    ProposalState publish();

    /** PUBLISHED -> CLOSED. */
    ProposalState close();
}

// Each state is a class: DraftState, PendingReviewState, ApprovedState,
// ApprovedWithChangesState, RejectedState, PublishedState, ClosedState.
// Illegal transitions throw InvalidProposalTransitionException.
```

Reglas:
- `REJECTED` es terminal: no tiene transiciones de salida. Una transición no permitida lanza `InvalidProposalTransitionException`, que responde `409 INVALID_STATE_TRANSITION`.
- Sin aprobación no hay publicación. `publish()` solo existe en los estados aprobados.
- El estado es el mismo que la columna `status` de `ops.schedule_proposals` (`DRAFT`, `PENDING_REVIEW`, `APPROVED`, `APPROVED_WITH_CHANGES`, `REJECTED`, `PUBLISHED`, `CLOSED`).
- `ProposalStateTest` prueba cada transición válida, cada transición inválida y que `REJECTED` no tiene salidas.
- `TankLevelState` (`HIGH`, `LOW`, `CRITICAL`) sigue la misma forma, con `CircuitBreakerState` en `co.caudal.infrastructure.forecast` (`CLOSED`, `OPEN`, `HALF_OPEN`). `CircuitBreakerStateTest` verifica la apertura tras los fallos configurados y el paso a `HALF_OPEN` al cumplirse el tiempo.

## 20. P19 Strategy

```java
package co.caudal.domain.schedule;

/**
 * Allocates available hours to sectors. The active implementation is stored in the proposal as strategy_code.
 *
 * @pattern P19 Strategy
 */
public interface TurnAllocationStrategy {

    /** Code stored in ops.schedule_proposals.strategy_code, for example PRIORITY_THEN_LONGEST_WAIT. */
    String code();

    AllocationResult allocate(AllocationInput input);
}

public final class PriorityThenLongestWaitStrategy implements TurnAllocationStrategy {
    @Override public String code() { return "PRIORITY_THEN_LONGEST_WAIT"; }
    @Override public AllocationResult allocate(AllocationInput input) { /* uses SectorTurnIterator */ return null; }
}

package co.caudal.domain.tank;

/** Decides whether the level is falling, using rule set trend_threshold_per_day. */
public interface TrendStrategy {
    TrendDirection direction(List<LevelPoint> recentPoints, BigDecimal thresholdPerDay);
}
```

Reglas:
- La estrategia viene de la regla vigente (`allocation_strategy`). Una propuesta guarda el código con el que se generó.
- Cada estrategia tiene su prueba. Para la misma entrada, las estrategias son deterministas.
- `TurnAllocationStrategyTest` y `TrendStrategyTest` cubren los casos del umbral (justo en el límite, por encima y por debajo).

## 21. P20 Template Method

```java
package co.caudal.infrastructure.document;

/**
 * Fixed order for every document: header, sections, signatures.
 *
 * @pattern P20 Template Method
 */
public abstract class DocumentGenerator {

    private final DocumentFactory factory;

    protected DocumentGenerator(DocumentFactory factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    /** Template method. Final so subclasses cannot change the order. */
    public final byte[] generate(DocumentContent content) {
        writeHeader(factory.createHeader(content.metadata()));
        writeSections(content.sections(), factory);
        writeSignatures(factory.createSignatures(content.signatories()));
        return finish();
    }

    protected abstract void writeSections(List<SectionContent> sections, DocumentFactory factory);

    protected abstract byte[] finish();

    private void writeHeader(DocumentHeader header) { /* shared behaviour */ }

    private void writeSignatures(DocumentSignatures signatures) { /* shared behaviour */ }
}
```

Reglas:
- El método plantilla es `final`. Las subclases solo completan las secciones y el cierre.
- `DocumentGeneratorTest` verifica que las llamadas ocurren en el orden encabezado, secciones, firmas.
- Un generador nuevo no reescribe el encabezado ni las firmas.

## 22. P21 Visitor

```java
package co.caudal.domain.minutes;

/**
 * Epistemic status of a minutes record. Sealed, so every operation must handle every case.
 *
 * @pattern P21 Visitor
 */
public sealed interface EpistemicRecord
        permits ObservedRecord, EstimatedRecord, InferredRecord, ConfirmedRecord {

    <R> R accept(MinutesSectionVisitor<R> visitor);
}

public interface MinutesSectionVisitor<R> {
    R visitObserved(ObservedRecord record);
    R visitEstimated(EstimatedRecord record);
    R visitInferred(InferredRecord record);
    R visitConfirmed(ConfirmedRecord record);
}

public record ObservedRecord(UUID sourceId, Instant at, String summary) implements EpistemicRecord {
    @Override
    public <R> R accept(MinutesSectionVisitor<R> visitor) {
        return visitor.visitObserved(this);
    }
}
```

Reglas:
- Agregar un tipo de registro obliga a actualizar cada visitante. Esto es intencional.
- Los registros `OBSERVED` vienen de lecturas y reportes. `ESTIMATED` de pronósticos y horas disponibles. `INFERRED` de anomalías. `CONFIRMED` de ejecuciones e incidentes verificados.
- `MinutesSectionVisitorTest` verifica que cada registro cae en su sección.

## 23. P22 Interpreter (opcional)

```java
package co.caudal.shared.text;

/**
 * Grammar node for message templates such as "{sector} lleva {hours} horas sin servicio".
 *
 * @pattern P22 Interpreter
 */
public interface MessageExpression {
    String interpret(MessageContext context);
}

public record TextExpression(String text) implements MessageExpression {
    @Override public String interpret(MessageContext context) { return text; }
}

public record PlaceholderExpression(String name) implements MessageExpression {
    @Override public String interpret(MessageContext context) { return context.text(name); }
}

public record PluralExpression(String name, String singular, String plural) implements MessageExpression {
    @Override public String interpret(MessageContext context) {
        return context.number(name).compareTo(BigDecimal.ONE) == 0 ? singular : plural;
    }
}

public record DecimalExpression(String name) implements MessageExpression {
    @Override public String interpret(MessageContext context) {
        return context.decimalComma(name); // "2,1", never "2.1"
    }
}

public final class MessageTemplate {
    public static MessageTemplate parse(String template) { /* builds the expression tree */ return null; }
    public String render(MessageContext context) { /* interprets the tree */ return null; }
}
```

Reglas:
- Un marcador desconocido o un tipo incorrecto produce un error de dominio, no una cadena vacía.
- Los números se muestran con coma decimal.
- `MessageTemplateTest` cubre plurales ("1 hora", "2 horas"), coma decimal y marcadores desconocidos.

## 24. P23 Flyweight (opcional)

```java
package co.caudal.domain.catalog;

/**
 * Immutable catalog item. Intrinsic state only: shared across rows.
 *
 * @pattern P23 Flyweight
 */
public final class CatalogItem {

    private final CatalogKind kind;
    private final String code;
    private final String labelEs;

    CatalogItem(CatalogKind kind, String code, String labelEs) {
        this.kind = kind;
        this.code = code;
        this.labelEs = labelEs;
    }

    public CatalogKind kind() { return kind; }
    public String code() { return code; }
    public String labelEs() { return labelEs; }
}

public final class CatalogItemFlyweightFactory {

    private final Map<String, CatalogItem> pool = new ConcurrentHashMap<>();

    public CatalogItem get(CatalogKind kind, String code, String labelEs) {
        return pool.computeIfAbsent(kind + ":" + code, key -> new CatalogItem(kind, code, labelEs));
    }
}
```

Reglas:
- Solo el estado intrínseco se comparte. Un valor que varía por fila (orden o activación de una instalación) no entra al flyweight.
- `CatalogItemFlyweightFactoryTest` verifica que la misma clave devuelve la misma instancia.

Relacionados: [Patrones de diseño](../docs/Patrones-de-diseno.md), [Arquitectura operativa](architecture.md), [Plan de pruebas](testing-plan.md)
