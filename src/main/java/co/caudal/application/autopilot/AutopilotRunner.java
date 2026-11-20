package co.caudal.application.autopilot;

import co.caudal.application.port.out.AqueductPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the cycle of the autopilot for every aqueduct, one after the other. A failure in one
 * aqueduct is logged and does not stop the others.
 */
public class AutopilotRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AutopilotRunner.class);

  private final AqueductPort aqueducts;
  private final AutopilotCycle cycle;

  /**
   * Creates the runner.
   *
   * @param aqueducts the aqueducts
   * @param cycle the cycle of one aqueduct
   */
  public AutopilotRunner(AqueductPort aqueducts, AutopilotCycle cycle) {
    this.aqueducts = aqueducts;
    this.cycle = cycle;
  }

  /** Runs one cycle for each aqueduct. */
  public void runAll() {
    for (UUID aqueductId : aqueducts.allIds()) {
      try {
        cycle.run(aqueductId);
      } catch (RuntimeException failure) {
        LOG.error("Autopilot cycle failed", failure);
      }
    }
  }
}
