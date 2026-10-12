package co.caudal.domain.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The level of a tank at the end of a day: its last effective reading of that day.
 *
 * @param day the day, in the time zone of the aqueduct
 * @param value the level
 */
public record DailyLevel(LocalDate day, BigDecimal value) {}
