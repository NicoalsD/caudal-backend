package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The privacy notice in effect.
 *
 * @param version its number
 * @param textEs the text
 * @param textSha256 SHA-256 of the text
 * @param effectiveAt since when it applies
 */
@Schema(description = "Aviso de privacidad vigente.")
public record PrivacyNoticeResponse(
    @Schema(description = "Versión.", example = "1") int version,
    @Schema(description = "Texto del aviso.") String textEs,
    @Schema(description = "SHA-256 del texto, para probar cuál se aceptó.") String textSha256,
    @Schema(description = "Desde cuándo aplica.") Instant effectiveAt) {}
