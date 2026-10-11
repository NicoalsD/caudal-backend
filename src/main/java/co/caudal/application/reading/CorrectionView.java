package co.caudal.application.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A correction of a reading ({@code ops.reading_corrections}). The original reading never changes;
 * the latest correction gives its effective value.
 *
 * @param id the correction
 * @param readingId the reading
 * @param correctedGaugeValue the right value
 * @param reason why
 * @param correctedBy who corrected it
 * @param createdAt when
 */
public record CorrectionView(
    UUID id,
    UUID readingId,
    BigDecimal correctedGaugeValue,
    String reason,
    UUID correctedBy,
    Instant createdAt) {}
