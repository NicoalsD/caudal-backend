package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Setting a password with a one-time code. Only the size of the input is checked here: a code or a
 * username with the wrong format is answered like an unknown code (a generic {@code UNAUTHORIZED}),
 * and the rules of the new password are applied by the use case ({@code 422}).
 *
 * @param username the account
 * @param code the one-time code
 * @param newPassword the new password
 */
@Schema(description = "Fijar la contraseña con el código de un solo uso que dio la Junta.")
public record ResetPasswordRequest(
    @Schema(description = "Nombre de usuario.", example = "operador.ejemplo")
        @NotNull
        @Size(min = 1, max = FieldLimits.LOGIN_USERNAME_INPUT_MAX)
        String username,
    @Schema(
            description = "Código de un solo uso de 12 caracteres.",
            example = "ABCD-EFGH-JKMN",
            format = "password")
        @NotNull
        @Size(min = 1, max = FieldLimits.RESET_CODE_INPUT_MAX)
        String code,
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
    return "ResetPasswordRequest[REDACTED]";
  }
}
