package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import java.util.List;

/** Forces a cycle of the autopilot now, for the demo (docs/API.md, autopilot section). */
public class RunAutopilotUseCase {

  private final AutopilotCycle cycle;

  /**
   * Creates the use case.
   *
   * @param cycle the cycle of one aqueduct
   */
  public RunAutopilotUseCase(AutopilotCycle cycle) {
    this.cycle = cycle;
  }

  /**
   * Runs a cycle for the aqueduct of the session.
   *
   * @param caller who asks
   * @return the decisions of the cycle
   */
  public List<DecisionView> execute(Caller caller) {
    return cycle.run(caller.aqueductId());
  }
}
