package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * First code of the authenticator app.
 *
 * @param code the six digit TOTP code
 */
@Schema(description = "Primer código de la aplicación de autenticación, para activar MFA.")
public record ConfirmMfaRequest(
    @Schema(description = "Código TOTP de 6 dígitos.", example = "123456")
        @NotNull
        @Pattern(regexp = FieldLimits.TOTP_CODE_PATTERN)
        String code) {

  @Override
  public String toString() {
    return "ConfirmMfaRequest[REDACTED]";
  }
}
