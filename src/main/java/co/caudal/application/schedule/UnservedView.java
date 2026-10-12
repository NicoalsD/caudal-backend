package co.caudal.application.schedule;

import java.util.UUID;

/**
 * A sector left without a shift and why.
 *
 * @param sectorId the sector
 * @param reason the code of the reason
 */
public record UnservedView(UUID sectorId, String reason) {}
