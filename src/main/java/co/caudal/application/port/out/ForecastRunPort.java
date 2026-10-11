package co.caudal.application.port.out;

import co.caudal.application.forecast.ForecastRunRecord;
import co.caudal.application.forecast.StoredForecast;
import java.util.Optional;
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

  /**
   * The latest primary run of a tank: the forecast shown to people, never the shadow.
   *
   * @param tankId the tank
   * @return the run with its points, or empty
   */
  Optional<StoredForecast> latestPrimary(UUID tankId);
}
