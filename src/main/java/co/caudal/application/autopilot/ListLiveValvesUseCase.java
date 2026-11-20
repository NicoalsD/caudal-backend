package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AutopilotContextPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.List;

/** Lists the live state of the valves and their last command, for the 3D scene. */
public class ListLiveValvesUseCase {

  private final AutopilotContextPort context;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param context valves
   * @param unitOfWork transaction boundary
   */
  public ListLiveValvesUseCase(AutopilotContextPort context, UnitOfWorkPort unitOfWork) {
    this.context = context;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Lists the active valves.
   *
   * @param caller who asks
   * @return the valves with their state
   */
  public List<LiveValveView> execute(Caller caller) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> context.valves().stream().filter(ValveRow::active).map(ValveRow::live).toList());
  }
}
