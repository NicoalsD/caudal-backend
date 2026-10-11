package co.caudal.application.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The forecast of one day: the level with 10 %, 50 % and 90 % probability of not being exceeded.
 *
 * @param targetDate the day, in the time zone of the aqueduct
 * @param p10 low end of the range
 * @param p50 most likely level
 * @param p90 high end of the range
 */
public record ForecastPoint(LocalDate targetDate, BigDecimal p10, BigDecimal p50, BigDecimal p90) {}
