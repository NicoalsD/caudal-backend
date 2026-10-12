package co.caudal.application.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A reading of the tank as stored.
 *
 * @param id the id chosen by the client
 * @param tankId the tank
 * @param gaugeValue the value read
 * @param waterAppearanceCode the appearance of the water
 * @param damageNoticed true if damage was noticed
 * @param note free note, or null
 * @param observedAt when it was observed
 * @param receivedAt when the server received it
 * @param source MANUAL_APP, SENSOR or IMPORT
 * @param validationStatus ACCEPTED or FLAGGED
 * @param ruleSetId the rules in force when it was validated
 */
public record ReadingView(
    UUID id,
    UUID tankId,
    BigDecimal gaugeValue,
    String waterAppearanceCode,
    boolean damageNoticed,
    String note,
    Instant observedAt,
    Instant receivedAt,
    String source,
    String validationStatus,
    UUID ruleSetId) {}
