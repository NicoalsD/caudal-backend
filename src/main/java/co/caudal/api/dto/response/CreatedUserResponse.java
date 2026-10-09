package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A new account and its one-time activation code.
 *
 * @param id account id
 * @param username username
 * @param fullName full name
 * @param status account status
 * @param mustChangePassword true until the first password change
 * @param membership the first membership
 * @param activationCode one-time code, shown only in this response
 * @param expiresAt when the code stops working
 */
@Schema(description = "Cuenta creada y su código de activación, que se muestra una sola vez.")
public record CreatedUserResponse(
    @Schema(
            description = "Identificador de la cuenta.",
            example = "0190f3a2-0000-7000-8000-000000000001")
        UUID id,
    @Schema(description = "Nombre de usuario.", example = "operador.ejemplo") String username,
    @Schema(description = "Nombre completo.", example = "Persona de Ejemplo") String fullName,
    @Schema(description = "Estado de la cuenta.", example = "ACTIVE") String status,
    @Schema(description = "Debe cambiar la contraseña tras el primer ingreso.", example = "true")
        boolean mustChangePassword,
    @Schema(description = "Primera membresía de la cuenta.") MembershipSummary membership,
    @Schema(
            description =
                "Código de activación de un solo uso. Se muestra solo en esta respuesta; la"
                    + " persona lo usa en POST /auth/reset-password para fijar su contraseña.",
            example = "ABCD-EFGH-JKMN")
        String activationCode,
    @Schema(description = "Vencimiento del código.", example = "2026-10-10T13:00:00Z")
        Instant expiresAt) {

  /**
   * Membership summary.
   *
   * @param id membership id
   * @param aqueductId aqueduct
   * @param role role code
   */
  @Schema(description = "Membresía creada con la cuenta.")
  public record MembershipSummary(
      @Schema(description = "Identificador de la membresía.") UUID id,
      @Schema(description = "Acueducto.") UUID aqueductId,
      @Schema(description = "Rol.", example = "OPERATOR") String role) {}

  @Override
  public String toString() {
    return "CreatedUserResponse[id=" + id + ", username=" + username + "]";
  }
}
