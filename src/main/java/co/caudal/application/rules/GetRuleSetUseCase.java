package co.caudal.application.rules;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.UUID;

/** Reads one version of the rules of the aqueduct (docs/API.md, section 6.3). */
public class GetRuleSetUseCase {

  private final RuleSetPort ruleSets;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param ruleSets versions of the rules
   * @param unitOfWork transaction boundary
   */
  public GetRuleSetUseCase(RuleSetPort ruleSets, UnitOfWorkPort unitOfWork) {
    this.ruleSets = ruleSets;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a version.
   *
   * @param caller who asks
   * @param id the version
   * @return the version
   * @throws DomainException with {@code NOT_FOUND} if it does not exist in the aqueduct
   */
  public RuleSetDetail execute(Caller caller, UUID id) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> ruleSets.findDetail(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
  }
}
