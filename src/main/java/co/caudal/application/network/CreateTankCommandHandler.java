package co.caudal.application.network;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.FieldValidationException;

/**
 * Creates a tank. The maximum of the gauge must be greater than the minimum, and the precision
 * greater than zero.
 *
 * @pattern P13 Command
 */
public class CreateTankCommandHandler implements CommandHandler<CreateTankCommand, TankView> {

  private final NetworkPort network;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param network network
   * @param unitOfWork transaction boundary
   */
  public CreateTankCommandHandler(NetworkPort network, UnitOfWorkPort unitOfWork) {
    this.network = network;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public TankView handle(CreateTankCommand command) {
    if (command.gaugeMax().compareTo(command.gaugeMin()) <= 0) {
      throw new FieldValidationException("gauge_max", "FIELD_OUT_OF_RANGE");
    }
    if (command.gaugeStep().signum() <= 0) {
      throw new FieldValidationException("gauge_step", "FIELD_OUT_OF_RANGE");
    }
    return unitOfWork.executeAs(
        command.caller().scope(),
        () ->
            network.insertTank(
                command.caller().aqueductId(),
                command.name().strip(),
                command.gaugeMin(),
                command.gaugeMax(),
                command.gaugeStep(),
                command.capacityLiters()));
  }

  @Override
  public Class<CreateTankCommand> commandType() {
    return CreateTankCommand.class;
  }
}
