package co.caudal.application.port.out;

import co.caudal.application.minutes.MinutesFact;
import java.time.Instant;
import java.util.List;

/** Reads the records of a period for the minutes. Row level security limits the aqueduct. */
public interface MinutesSourcePort {

  /**
   * Every record of the period: readings, corrections, damage reports, forecasts, proposals,
   * anomalies and executions not replaced.
   *
   * @param from first moment, included
   * @param to last moment, excluded
   * @return the records
   */
  List<MinutesFact> facts(Instant from, Instant to);
}
