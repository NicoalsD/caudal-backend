package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.command.CommandBus;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.application.port.out.InsightsPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.AutopilotSettings;
import co.caudal.domain.autopilot.CycleContext;
import co.caudal.domain.autopilot.DecisionStatus;
import co.caudal.domain.autopilot.ModeStrategy;
import co.caudal.domain.autopilot.Recommendation;
import co.caudal.domain.autopilot.Resolution;
import co.caudal.shared.error.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One cycle of the autopilot for one aqueduct (docs/Reglas-de-negocio.md, autopilot section):
 *
 * <ol>
 *   <li>takes the snapshot of the aqueduct in a transaction;
 *   <li>asks the assistant outside any transaction, because the call may wait for the network;
 *   <li>checks the recommendations, expires the pending decisions of the previous cycle and stores
 *       the new ones as the mode says (pending, or skipped by a guard) in a second transaction;
 *   <li>in {@code AUTOMATIC}, executes through the command bus each decision the mode resolved to
 *       execute, one transaction each, so a refused command does not undo the others.
 * </ol>
 */
public class AutopilotCycle {

  private static final Logger LOG = LoggerFactory.getLogger(AutopilotCycle.class);
  private static final String COMMAND_REFUSED = "COMMAND_REFUSED";

  private final SnapshotBuilder snapshots;
  private final AutopilotSettingsPort settings;
  private final AutopilotDecisionPort decisions;
  private final InsightsPort insights;
  private final OperatorPresenceTracker presence;
  private final CommandBus commandBus;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the cycle.
   *
   * @param snapshots takes the snapshot of the aqueduct
   * @param settings the autopilot settings
   * @param decisions the decisions
   * @param insights the assistant
   * @param presence the last human action seen in memory
   * @param commandBus executes the decisions
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  // Each collaborator is one step of the cycle; the cycle is their only user.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public AutopilotCycle(
      SnapshotBuilder snapshots,
      AutopilotSettingsPort settings,
      AutopilotDecisionPort decisions,
      InsightsPort insights,
      OperatorPresenceTracker presence,
      CommandBus commandBus,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.snapshots = snapshots;
    this.settings = settings;
    this.decisions = decisions;
    this.insights = insights;
    this.presence = presence;
    this.commandBus = commandBus;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /** What the first transaction reads. */
  private record Prepared(
      AutopilotSettings settings, InsightsSnapshot snapshot, Instant lastHumanAction) {}

  /** A stored decision and whether the mode resolved it to execute. */
  private record Stored(DecisionView view, boolean execute) {}

  /**
   * Runs a cycle.
   *
   * @param aqueductId the aqueduct
   * @return the decisions of this cycle, in the order of the recommendations; empty if there was
   *     nothing to decide
   */
  public List<DecisionView> run(UUID aqueductId) {
    SessionScope scope = new SessionScope(null, aqueductId);
    Prepared prepared = unitOfWork.executeAs(scope, () -> prepare(aqueductId));
    if (prepared == null) {
      return List.of();
    }
    InsightsRecommendations answer = insights.recommend(prepared.snapshot());
    List<Stored> stored = unitOfWork.executeAs(scope, () -> store(prepared, answer));
    List<DecisionView> result = new ArrayList<>();
    for (Stored entry : stored) {
      result.add(entry.execute() ? execute(aqueductId, scope, entry.view()) : entry.view());
    }
    return result;
  }

  private Prepared prepare(UUID aqueductId) {
    AutopilotSettings current = settings.getOrCreate(aqueductId);
    Instant human = lastHumanAction(aqueductId);
    return snapshots
        .build(aqueductId, current, human)
        .filter(snapshot -> snapshot.valves().stream().anyMatch(InsightsSnapshot.Valve::motorized))
        .map(snapshot -> new Prepared(current, snapshot, human))
        .orElse(null);
  }

  /** The latest of the database and the memory of this instance. */
  private Instant lastHumanAction(UUID aqueductId) {
    Instant stored = decisions.lastHumanActionAt().orElse(null);
    Instant remembered = presence.lastActionAt(aqueductId).orElse(null);
    if (stored == null) {
      return remembered;
    }
    return remembered != null && remembered.isAfter(stored) ? remembered : stored;
  }

  private List<Stored> store(Prepared prepared, InsightsRecommendations answer) {
    List<Recommendation> clean = RecommendationSanitizer.clean(answer, prepared.snapshot());
    if (clean.isEmpty()) {
      return List.of();
    }
    AutopilotSettings current = prepared.settings();
    CycleContext context =
        new CycleContext(
            prepared.snapshot().tank().levelPct(),
            current.reserveFloorPct(),
            prepared.lastHumanAction(),
            clock.instant(),
            current.operatorAbsenceMinutes());
    ModeStrategy strategy = ModeStrategy.of(current.mode());
    decisions.expirePending();
    List<Stored> stored = new ArrayList<>();
    for (Recommendation recommendation : clean) {
      Resolution resolution = strategy.resolve(recommendation, context);
      boolean skipped = resolution.outcome() == Resolution.Outcome.SKIPPED;
      DecisionView view =
          decisions.insert(
              new NewDecision(
                  current.aqueductId(),
                  answer.source(),
                  answer.model(),
                  recommendation.action().name(),
                  recommendation.valveId(),
                  recommendation.reason(),
                  recommendation.confidence(),
                  skipped ? DecisionStatus.SKIPPED.name() : DecisionStatus.PENDING.name(),
                  resolution.detail(),
                  inputs(prepared, answer)));
      stored.add(new Stored(view, resolution.outcome() == Resolution.Outcome.EXECUTE));
    }
    return stored;
  }

  private DecisionView execute(UUID aqueductId, SessionScope scope, DecisionView decision) {
    try {
      return commandBus.dispatch(
          new ExecuteDecisionCommand(Caller.autopilot(aqueductId), decision.id()));
    } catch (DomainException refused) {
      LOG.warn("Autopilot command refused by the command bus");
      return unitOfWork.executeAs(
          scope,
          () -> {
            decisions.transition(
                decision.id(),
                DecisionStatus.PENDING,
                DecisionStatus.SKIPPED,
                null,
                COMMAND_REFUSED,
                clock.instant());
            return decisions.find(decision.id()).orElse(decision);
          });
    }
  }

  /** The snapshot reduced to what explains the decision, stored with it. */
  private static Map<String, Object> inputs(Prepared prepared, InsightsRecommendations answer) {
    InsightsSnapshot snapshot = prepared.snapshot();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("mode", snapshot.mode());
    map.put("reserve_floor_pct", snapshot.reserveFloorPct());
    map.put("level_pct", snapshot.tank().levelPct());
    map.put("band", snapshot.tank().band());
    map.put("trend", snapshot.tank().trend());
    map.put("operator_last_action_at", String.valueOf(snapshot.operatorLastActionAt()));
    map.put("valves", snapshot.valves().size());
    map.put("shifts", snapshot.schedule().size());
    map.put("anomalies", snapshot.anomalies().size());
    map.put("summary", answer.summary());
    return map;
  }
}
