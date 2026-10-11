package co.caudal.application.forecast;

import java.util.List;

/**
 * A forecast made by a model.
 *
 * @param modelName the model, for example {@code autogluon/chronos-2-small}
 * @param modelVersion its version
 * @param points one point per day of the horizon
 * @param latencyMs how long it took
 */
public record ModelForecast(
    String modelName, String modelVersion, List<ForecastPoint> points, long latencyMs) {

  /** Copies the points. */
  public ModelForecast {
    points = List.copyOf(points);
  }
}
