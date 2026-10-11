package co.caudal.application.network;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/** Handlers that add and change valves (docs/API.md, sections 5.7 and 5.8). */
public final class ValveCommandHandlers {

  /** Position a valve takes when its device loses control, unless told otherwise. */
  static final String DEFAULT_FAIL_SAFE = "KEEP";

  private ValveCommandHandlers() {}

  /**
   * Adds a valve to a sector of the aqueduct; a code already used in the sector is a conflict.
   *
   * @pattern P13 Command
   */
  public static final class Create implements CommandHandler<CreateValveCommand, ValveView> {

    private final NetworkPort network;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param network the network
     * @param unitOfWork transaction boundary
     */
    public Create(NetworkPort network, UnitOfWorkPort unitOfWork) {
      this.network = network;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public ValveView handle(CreateValveCommand command) {
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            network
                .sector(command.sectorId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            return network
                .insertValve(
                    command.sectorId(),
                    command.code(),
                    command.name().strip(),
                    blankToNull(command.locationHint()),
                    Boolean.TRUE.equals(command.motorized()),
                    command.failSafePosition() == null
                        ? DEFAULT_FAIL_SAFE
                        : command.failSafePosition())
                .orElseThrow(
                    () -> new DomainException(ErrorCode.CONFLICT, Map.of("field", "code")));
          });
    }

    @Override
    public Class<CreateValveCommand> commandType() {
      return CreateValveCommand.class;
    }
  }

  /**
   * Changes a valve of the aqueduct.
   *
   * @pattern P13 Command
   */
  public static final class Update implements CommandHandler<UpdateValveCommand, ValveView> {

    private final NetworkPort network;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param network the network
     * @param unitOfWork transaction boundary
     */
    public Update(NetworkPort network, UnitOfWorkPort unitOfWork) {
      this.network = network;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public ValveView handle(UpdateValveCommand command) {
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            ValveView current =
                network
                    .valve(command.valveId())
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            return network.updateValve(
                new ValveView(
                    current.id(),
                    current.code(),
                    command.name() == null ? current.name() : command.name().strip(),
                    command.locationHint() == null
                        ? current.locationHint()
                        : blankToNull(command.locationHint()),
                    command.motorized() == null ? current.motorized() : command.motorized(),
                    command.failSafePosition() == null
                        ? current.failSafePosition()
                        : command.failSafePosition(),
                    command.active() == null ? current.active() : command.active()));
          });
    }

    @Override
    public Class<UpdateValveCommand> commandType() {
      return UpdateValveCommand.class;
    }
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
