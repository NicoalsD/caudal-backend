package co.caudal.application.retention;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.RetentionPolicyPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;

/**
 * Stores a new retention period. The purge functions of the database read it from the table, so the
 * code never holds a number of days (docs/Configuracion-sin-valores-quemados.md).
 *
 * @pattern P13 Command
 */
public class UpdateRetentionPolicyCommandHandler
    implements CommandHandler<UpdateRetentionPolicyCommand, RetentionPolicyView> {

  private final RetentionPolicyPort policies;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param policies retention periods
   * @param unitOfWork transaction boundary
   */
  public UpdateRetentionPolicyCommandHandler(
      RetentionPolicyPort policies, UnitOfWorkPort unitOfWork) {
    this.policies = policies;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public RetentionPolicyView handle(UpdateRetentionPolicyCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () ->
            policies
                .update(command.dataClass(), command.retentionDays(), command.caller().userId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
  }

  @Override
  public Class<UpdateRetentionPolicyCommand> commandType() {
    return UpdateRetentionPolicyCommand.class;
  }
}
