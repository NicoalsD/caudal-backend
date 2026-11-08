package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.DecisionStatus;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;

/** Lists the decisions of the autopilot, the newest first (HU-41, HU-42). */
public class ListDecisionsUseCase {

  private final AutopilotDecisionPort decisions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param decisions the decisions
   * @param unitOfWork transaction boundary
   */
  public ListDecisionsUseCase(AutopilotDecisionPort decisions, UnitOfWorkPort unitOfWork) {
    this.decisions = decisions;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Lists decisions.
   *
   * @param caller who asks
   * @param status only this status, or null for all
   * @param limit the most to return, or null for the maximum
   * @return the decisions, the newest first
   * @throws DomainException with {@code VALIDATION_ERROR} for an unknown status or a limit out of
   *     range
   */
  public List<DecisionView> execute(Caller caller, String status, Integer limit) {
    DecisionStatus filter = null;
    if (status != null && !status.isBlank()) {
      try {
        filter = DecisionStatus.of(status);
      } catch (IllegalArgumentException unknown) {
        throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", "status"));
      }
    }
    int max = limit == null ? FieldLimits.AUTOPILOT_LIST_MAX : limit;
    if (max < 1 || max > FieldLimits.AUTOPILOT_LIST_MAX) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", "limit"));
    }
    DecisionStatus wanted = filter;
    return unitOfWork.executeAs(caller.scope(), () -> decisions.list(wanted, max));
  }
}
