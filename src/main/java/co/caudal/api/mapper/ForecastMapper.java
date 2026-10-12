package co.caudal.api.mapper;

import co.caudal.api.dto.response.ForecastRunResponse;
import co.caudal.api.dto.response.TankStatusResponse;
import co.caudal.application.facade.TankStatus;
import co.caudal.application.forecast.StoredForecast;

/** Maps forecasts and tank status to response DTOs. */
public final class ForecastMapper {

  private ForecastMapper() {}

  /**
   * Maps a forecast run.
   *
   * @param run the run, or null
   * @return the response, or null
   */
  public static ForecastRunResponse toResponse(StoredForecast run) {
    if (run == null) {
      return null;
    }
    return new ForecastRunResponse(
        run.runId(),
        run.modelName(),
        run.modelVersion(),
        run.fallback(),
        run.fallbackReason(),
        run.horizonDays(),
        run.createdAt(),
        run.contextFrom(),
        run.contextTo(),
        run.points().stream()
            .map(p -> new ForecastRunResponse.Point(p.targetDate(), p.p10(), p.p50(), p.p90()))
            .toList());
  }

  /**
   * Maps the status of a tank.
   *
   * @param status the status
   * @return the response
   */
  public static TankStatusResponse toResponse(TankStatus status) {
    return new TankStatusResponse(
        status.tankId(),
        status.level() == null
            ? null
            : new TankStatusResponse.Level(status.level(), status.observedAt(), status.hoursAgo()),
        status.trend() == null
            ? null
            : new TankStatusResponse.TrendView(
                status.trend().slopePerDay(), status.trend().label(), status.trend().labelEs()),
        new TankStatusResponse.Stale(status.stale(), status.staleAfterHours()),
        status.band(),
        toResponse(status.forecast()),
        status.openAnomalies());
  }
}
