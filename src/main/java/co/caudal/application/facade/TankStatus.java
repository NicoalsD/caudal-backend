package co.caudal.application.facade;

import co.caudal.application.forecast.StoredForecast;
import co.caudal.application.forecast.TrendView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Everything the panel shows about a tank at once (docs/API.md, section 8.1).
 *
 * @param tankId the tank
 * @param level the last effective level, or null
 * @param observedAt when it was read, or null
 * @param hoursAgo hours since then, or null
 * @param trend where it is going, or null with too little history
 * @param stale true when the level is older than {@code stale_reading_hours}
 * @param staleAfterHours {@code stale_reading_hours} of the rules
 * @param band the band of the level ({@code HIGH}, {@code LOW} or {@code CRITICAL}), or null
 * @param forecast the latest forecast shown to people, or null
 * @param openAnomalies anomalies still open in the aqueduct
 */
public record TankStatus(
    UUID tankId,
    BigDecimal level,
    Instant observedAt,
    BigDecimal hoursAgo,
    TrendView trend,
    boolean stale,
    int staleAfterHours,
    String band,
    StoredForecast forecast,
    int openAnomalies) {}
