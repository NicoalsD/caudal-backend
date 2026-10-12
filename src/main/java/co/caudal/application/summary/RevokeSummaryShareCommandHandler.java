package co.caudal.application.summary;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.SummarySharePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;

/**
 * Revokes an authorization once. An unknown one answers {@code NOT_FOUND} (also for other
 * aqueducts); one already revoked answers {@code CONFLICT}.
 *
 * @pattern P13 Command
 */
public class RevokeSummaryShareCommandHandler
    implements CommandHandler<RevokeSummaryShareCommand, SummaryShareView> {

  private final SummarySharePort shares;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param shares authorizations
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public RevokeSummaryShareCommandHandler(
      SummarySharePort shares, UnitOfWorkPort unitOfWork, Clock clock) {
    this.shares = shares;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public SummaryShareView handle(RevokeSummaryShareCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          SummaryShareView current =
              shares
                  .find(command.shareId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          if (current.revokedAt() != null
              || !shares.revoke(current.id(), command.caller().userId(), clock.instant())) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          return shares.find(current.id()).orElseThrow();
        });
  }

  @Override
  public Class<RevokeSummaryShareCommand> commandType() {
    return RevokeSummaryShareCommand.class;
  }
}
