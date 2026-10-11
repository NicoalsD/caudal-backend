package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The version of the notice the person read.
 *
 * @param version the version in effect
 */
@Schema(description = "Versión del aviso de privacidad que la persona leyó.")
public record AcceptPrivacyNoticeRequest(
    @Schema(description = "Versión vigente.", example = "1") @NotNull @Positive Integer version) {}
