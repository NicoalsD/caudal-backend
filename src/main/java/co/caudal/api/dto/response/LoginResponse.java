package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Answer of a successful login. The refresh token is not in the body: it travels in the {@code
 * caudal_rt} cookie.
 *
 * @param accessToken signed access token
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the access token expires
 * @param user the person and their active session
 */
@Schema(description = "Sesión iniciada. El refresh token va en la cookie caudal_rt.")
public record LoginResponse(
    @Schema(description = "Access token JWT de 15 minutos.") String accessToken,
    @Schema(description = "Tipo de token.", example = "Bearer") String tokenType,
    @Schema(description = "Segundos hasta que vence el access token.", example = "900")
        long expiresIn,
    @Schema(description = "Persona y sesión activa.") SessionUserResponse user) {

  /**
   * The person and their active session.
   *
   * @param id account id
   * @param username username
   * @param fullName full name
   * @param role role in the active aqueduct
   * @param aqueductId active aqueduct
   * @param mustChangePassword true until the first password change
   */
  @Schema(description = "Persona que inició sesión.")
  public record SessionUserResponse(
      @Schema(
              description = "Identificador de la cuenta.",
              example = "0190f3a2-0000-7000-8000-000000000001")
          UUID id,
      @Schema(description = "Nombre de usuario.", example = "test.operator") String username,
      @Schema(description = "Nombre completo.", example = "Persona de Prueba Uno") String fullName,
      @Schema(description = "Rol en el acueducto activo.", example = "OPERATOR") String role,
      @Schema(description = "Acueducto activo.", example = "0190f3a2-0000-7000-8000-0000000000aa")
          UUID aqueductId,
      @Schema(description = "Debe cambiar la contraseña antes de continuar.", example = "false")
          boolean mustChangePassword) {}
}
