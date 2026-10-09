package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Credentials typed by the person. Only the input size is checked here: the format of the username
 * and the password rules are not applied at login, so a wrong format is answered like any other
 * failed login (a generic {@code INVALID_CREDENTIALS}) and old accounts can still sign in.
 *
 * @param username username as typed
 * @param password password as typed
 */
@Schema(description = "Credenciales para iniciar sesión.")
public record LoginRequest(
    @Schema(description = "Nombre de usuario.", example = "junta.presidencia")
        @NotNull
        @Size(min = 1, max = FieldLimits.LOGIN_USERNAME_INPUT_MAX)
        String username,
    @Schema(
            description = "Contraseña.",
            example = "contraseña-de-ejemplo-larga",
            format = "password")
        @NotNull
        @Size(min = 1, max = FieldLimits.PASSWORD_MAX)
        String password) {

  @Override
  public String toString() {
    return "LoginRequest[REDACTED]";
  }
}
