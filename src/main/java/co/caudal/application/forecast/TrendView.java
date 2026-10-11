package co.caudal.application.forecast;

import java.math.BigDecimal;

/**
 * Where the level of a tank is going, as the status shows it.
 *
 * @param slopePerDay change per day
 * @param label {@code DOWN}, {@code STABLE} or {@code UP}
 * @param labelEs the words people read
 */
public record TrendView(BigDecimal slopePerDay, String label, String labelEs) {}
