package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * MFA is on; the recovery codes are shown once.
 *
 * @param recoveryCodes one-time codes for when the authenticator app is lost
 */
@Schema(description = "MFA activo y sus códigos de respaldo, que se muestran una sola vez.")
public record MfaConfirmationResponse(
    @Schema(description = "Códigos de respaldo de un solo uso, por ejemplo ABCD-EFGH-JKMN.")
        List<String> recoveryCodes) {

  @Override
  public String toString() {
    return "MfaConfirmationResponse[REDACTED]";
  }
}
