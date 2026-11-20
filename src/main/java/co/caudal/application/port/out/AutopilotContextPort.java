package co.caudal.application.port.out;

import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ValveRow;
import java.time.Instant;
import java.util.List;

/**
 * Reads what the autopilot needs to know about the aqueduct besides the tank: the live state of the
 * valves, the shifts around now and the open anomalies. Runs inside a transaction opened with
 * {@link UnitOfWorkPort#executeAs}.
 */
public interface AutopilotContextPort {

  /**
   * The valves of the aqueduct with their live state and last command.
   *
   * @return the valves, ordered by code
   */
  List<ValveRow> valves();

  /**
   * The shifts of published schedules that overlap a period.
   *
   * @param from start of the period
   * @param to end of the period
   * @return the shifts, by start
   */
  List<InsightsSnapshot.Shift> shifts(Instant from, Instant to);

  /**
   * The anomalies still open.
   *
   * @return the anomalies with their severity
   */
  List<InsightsSnapshot.Anomaly> openAnomalies();
}
