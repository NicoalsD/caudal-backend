package co.caudal.application.reading;

import java.math.BigDecimal;

/**
 * A new correction and the effective value of the reading after it.
 *
 * @param correction the correction
 * @param effectiveGaugeValue the value that counts now, from {@code ops.effective_readings}
 */
public record CorrectionResult(CorrectionView correction, BigDecimal effectiveGaugeValue) {}
