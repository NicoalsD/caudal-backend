package co.caudal.application.rules;

import co.caudal.application.auth.Caller;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Returns the ACTIVE rules of the aqueduct of the session, with the gauge of its tank. */
public class GetCurrentRuleSetUseCase {

  /**
   * The current rules.
   *
   * @param rules the active version
   * @param gauge the first active tank, whose gauge the rules apply to, or null
   * @param serverTime the server's clock
   */
  public record CurrentRuleSet(RuleSetView rules, TankView gauge, Instant serverTime) {}

  private final RuleSetPort ruleSets;
  private final NetworkPort network;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param ruleSets rule sets
   * @param network network
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GetCurrentRuleSetUseCase(
      RuleSetPort ruleSets, NetworkPort network, UnitOfWorkPort unitOfWork, Clock clock) {
    this.ruleSets = ruleSets;
    this.network = network;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Reads the active rules.
   *
   * @param caller who asks
   * @return the rules
   * @throws DomainException with {@code NOT_FOUND} if there is no active version
   */
  public CurrentRuleSet execute(Caller caller) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          RuleSetView rules =
              ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          Optional<TankView> tank = network.tanks().stream().filter(TankView::active).findFirst();
          return new CurrentRuleSet(rules, tank.orElse(null), clock.instant());
        });
  }
}
