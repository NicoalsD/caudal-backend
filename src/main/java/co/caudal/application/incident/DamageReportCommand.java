package co.caudal.application.incident;

import co.caudal.application.command.Command;
import java.util.UUID;

/**
 * Someone reports damage from the public page, without login (docs/API.md, section 15.4; HU-27).
 *
 * @param aqueductSlug the aqueduct
 * @param categoryCode a code of {@code DAMAGE_CATEGORY}
 * @param sectorId the sector, or null
 * @param description what happened
 * @param locationHint where, or null
 * @param ipAddress the address of the request, kept only as an HMAC
 */
public record DamageReportCommand(
    String aqueductSlug,
    String categoryCode,
    UUID sectorId,
    String description,
    String locationHint,
    String ipAddress)
    implements Command<DamageReportReceipt> {}
