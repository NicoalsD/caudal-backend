package co.caudal.application.reading;

import java.math.BigDecimal;

/**
 * A reading in a list, with its effective value.
 *
 * @param reading the reading as observed
 * @param effectiveGaugeValue the last correction or the observed value
 * @param correctionCount how many corrections it has
 */
public record ListedReading(
    ReadingView reading, BigDecimal effectiveGaugeValue, int correctionCount) {}
