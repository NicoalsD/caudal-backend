package co.caudal.domain.autopilot;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a cycle of the autopilot knows about the aqueduct when it resolves a recommendation.
 *
 * @param levelPct tank level in percent of the gauge, or null when it is unknown
 * @param reserveFloorPct the reserve floor, in percent
 * @param lastHumanActionAt the last manual command, acceptance or rejection, or null if none
 * @param now the moment of the cycle
 * @param operatorAbsenceMinutes minutes without human action before {@code AUTOMATIC} acts
 */
public record CycleContext(
    BigDecimal levelPct,
    BigDecimal reserveFloorPct,
    Instant lastHumanActionAt,
    Instant now,
    int operatorAbsenceMinutes) {}
