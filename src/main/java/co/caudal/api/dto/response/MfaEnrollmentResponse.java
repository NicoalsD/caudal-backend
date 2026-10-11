package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the person needs to add CAUDAL to an authenticator app; shown once.
 *
 * @param secret the secret in Base32
 * @param otpauthUri the URI for the QR code
 */
@Schema(description = "Secreto TOTP para la aplicación de autenticación; se muestra una sola vez.")
public record MfaEnrollmentResponse(
    @Schema(description = "Secreto en Base32, para escribirlo a mano.") String secret,
    @Schema(description = "URI otpauth:// para el código QR.") String otpauthUri) {

  @Override
  public String toString() {
    return "MfaEnrollmentResponse[REDACTED]";
  }
}
