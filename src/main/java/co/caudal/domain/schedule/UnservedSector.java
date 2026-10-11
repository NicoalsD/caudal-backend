package co.caudal.domain.schedule;

import java.util.UUID;

/**
 * A sector without a shift today and why (docs/Reglas-de-negocio.md, section 6.3).
 *
 * @param sectorId the sector
 * @param reason {@code NO_BUDGET}, {@code CUOTA_INSUFICIENTE} or {@code NO_INCLUIDO}
 */
public record UnservedSector(UUID sectorId, String reason) {}
