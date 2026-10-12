package co.caudal.application.network;

import java.util.UUID;

/**
 * A sector that receives water by shifts.
 *
 * @param id the sector
 * @param code the short code
 * @param name the name
 * @param parentSectorId parent sector, or null
 * @param householdsCount number of households (aggregate)
 * @param active true if in use
 */
public record SectorView(
    UUID id, String code, String name, UUID parentSectorId, int householdsCount, boolean active) {}
