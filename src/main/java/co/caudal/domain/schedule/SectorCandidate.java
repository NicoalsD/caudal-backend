package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sector that may get water today, with what decides its turn.
 *
 * @param sectorId the sector
 * @param code its code, the last tie breaker
 * @param name its name, for the explanations
 * @param included whether the rules include it
 * @param priority whether it goes first
 * @param priorityRank order among priority sectors, or null
 * @param hoursWithoutService hours since its last service
 * @param waitOrigin {@code CONFIRMED} when it comes from an execution, {@code ESTIMATED} when from
 *     a published schedule
 */
public record SectorCandidate(
    UUID sectorId,
    String code,
    String name,
    boolean included,
    boolean priority,
    Integer priorityRank,
    BigDecimal hoursWithoutService,
    String waitOrigin) {}
