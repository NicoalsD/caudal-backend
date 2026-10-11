package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * An acceptance of the privacy notice.
 *
 * @param userId the account
 * @param version the version
 * @param acceptedAt when
 */
@Schema(description = "Aceptación del aviso de privacidad.")
public record PrivacyAcceptanceResponse(
    @Schema(description = "Cuenta.") UUID userId,
    @Schema(description = "Versión aceptada.", example = "1") int version,
    @Schema(description = "Momento de la aceptación.") Instant acceptedAt) {}
