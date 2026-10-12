package co.caudal.application.port.out;

import co.caudal.domain.evaluation.EvaluationSample;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Pairs stored forecasts with the real levels. Row level security limits the aqueduct. */
public interface EvaluationPort {

  /**
   * The days of a period where a forecast of the AI, its simple estimate made from the same history
   * and an effective reading exist for the same horizon.
   *
   * @param horizonDays days between the last data and the day forecast
   * @param from first day forecast
   * @param to last day forecast
   * @param zone time zone of the aqueduct, to cut the days
   * @return the comparable days
   */
  List<EvaluationSample> samples(int horizonDays, LocalDate from, LocalDate to, ZoneId zone);
}
