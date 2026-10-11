package co.caudal.application.forecast;

import co.caudal.application.reading.LevelPoint;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The history of a tank sent to forecast it (docs/Contrato-IA.md, section 3.1).
 *
 * @param seriesId the tank
 * @param points effective levels, oldest first
 * @param horizonDays days to forecast
 * @param zone time zone of the aqueduct, where the forecast days start at midnight
 */
public record ForecastSeries(UUID seriesId, List<LevelPoint> points, int horizonDays, ZoneId zone) {

  /** Copies the points. */
  public ForecastSeries {
    Objects.requireNonNull(seriesId, "seriesId");
    Objects.requireNonNull(zone, "zone");
    points = List.copyOf(points);
  }
}
