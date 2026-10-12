package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Change of password. Only the size of the input is checked here; the rules of a new password
 * (length of 12, common passwords, the username, the history) are applied by the use case and
 * answer {@code 422 PASSWORD_POLICY_VIOLATION}.
 *
 * @param currentPassword the password in force
 * @param newPassword the new password
 */
@Schema(description = "Cambio de contraseña de la persona autenticada.")
public record ChangePasswordRequest(
    @Schema(
            description = "Contraseña actual.",
            example = "contraseña-temporal-ejemplo",
            format = "password")
        @NotNull
        @Size(min = 1, max = FieldLimits.PASSWORD_MAX)
        String currentPassword,
    @Schema(
            description =
                "Contraseña nueva: de 12 a 128 caracteres, que no sea común, no contenga el"
                    + " usuario ni esté entre las últimas 5 usadas.",
            example = "nueva-contraseña-de-ejemplo",
            format = "password")
        @NotNull
        @Size(max = FieldLimits.PASSWORD_MAX)
        String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[REDACTED]";
  }
}
