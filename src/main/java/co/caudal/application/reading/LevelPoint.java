package co.caudal.application.reading;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An effective level (the last correction or the observed value) at a moment.
 *
 * @param value the level
 * @param observedAt when it was observed
 */
public record LevelPoint(BigDecimal value, Instant observedAt) {}
