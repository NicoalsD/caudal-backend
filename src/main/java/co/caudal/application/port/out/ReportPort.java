package co.caudal.application.port.out;

import java.time.Instant;
import java.util.Map;

/**
 * Computes the numbers of a period. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}.
 */
public interface ReportPort {

  /**
   * The aggregates of a period of the aqueduct of the session.
   *
   * @param from start of the period, inclusive
   * @param to end of the period, exclusive
   * @return groups of numbers by topic (levels, shifts, valve commands, autopilot, incidents)
   */
  Map<String, Object> aggregates(Instant from, Instant to);
}
