package co.caudal.application.port.out;

import co.caudal.application.forecast.ForecastRunRecord;
import java.util.UUID;

/** Stores forecast runs and their points ({@code ops.forecast_runs}, {@code forecast_points}). */
public interface ForecastRunPort {

  /**
   * Stores a run with its points.
   *
   * @param run the run
   * @return its id
   */
  UUID insert(ForecastRunRecord run);
}
