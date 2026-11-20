package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.application.port.out.InsightsPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.AutopilotSettings;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * One call for a question to the assistant (HU-43): it takes the snapshot of the aqueduct, asks the
 * assistant outside any transaction and answers with the text and where it came from. If the
 * assistant service fails the answer comes from the rules, so there is always one.
 *
 * @pattern P10 Facade
 */
public class AssistantFacade {

  private final SnapshotBuilder snapshots;
  private final AutopilotSettingsPort settings;
  private final AutopilotDecisionPort decisions;
  private final InsightsPort insights;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the facade.
   *
   * @param snapshots takes the snapshot of the aqueduct
   * @param settings the autopilot settings
   * @param decisions the decisions, for the last human action
   * @param insights the assistant
   * @param unitOfWork transaction boundary
   */
  public AssistantFacade(
      SnapshotBuilder snapshots,
      AutopilotSettingsPort settings,
      AutopilotDecisionPort decisions,
      InsightsPort insights,
      UnitOfWorkPort unitOfWork) {
    this.snapshots = snapshots;
    this.settings = settings;
    this.decisions = decisions;
    this.insights = insights;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Answers a question.
   *
   * @param caller who asks
   * @param question the question, 3 to 500 characters
   * @return the answer
   * @throws DomainException with {@code VALIDATION_ERROR} for a question out of its length, and
   *     with {@code NO_LEVEL_DATA} when the aqueduct has no tank to talk about
   */
  public InsightsAnswer ask(Caller caller, String question) {
    String text = question == null ? "" : question.strip();
    if (text.length() < FieldLimits.ASSISTANT_QUESTION_MIN
        || text.length() > FieldLimits.ASSISTANT_QUESTION_MAX) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", "question"));
    }
    InsightsSnapshot snapshot =
        unitOfWork
            .executeAs(caller.scope(), () -> snapshot(caller))
            .orElseThrow(() -> new DomainException(ErrorCode.NO_LEVEL_DATA));
    return insights.ask(text, snapshot);
  }

  private Optional<InsightsSnapshot> snapshot(Caller caller) {
    AutopilotSettings current = settings.getOrCreate(caller.aqueductId());
    Instant human = decisions.lastHumanActionAt().orElse(null);
    return snapshots.build(caller.aqueductId(), current, human);
  }
}
