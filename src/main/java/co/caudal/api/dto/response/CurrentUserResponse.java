package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Who the authenticated person is.
 *
 * @param id account id
 * @param username username
 * @param fullName full name
 * @param status account status
 * @param mustChangePassword true until the first password change
 * @param privacyAcceptedVersion latest privacy notice version accepted
 * @param memberships memberships in force
 * @param permissions permission codes of the role of the session
 */
@Schema(description = "Persona autenticada, sus membresías vigentes y sus permisos.")
public record CurrentUserResponse(
    @Schema(
            description = "Identificador de la cuenta.",
            example = "0190f3a2-0000-7000-8000-000000000001")
        UUID id,
    @Schema(description = "Nombre de usuario.", example = "test.operator") String username,
    @Schema(description = "Nombre completo.", example = "Persona de Prueba Uno") String fullName,
    @Schema(description = "Estado de la cuenta.", example = "ACTIVE") String status,
    @Schema(description = "Debe cambiar la contraseña antes de continuar.", example = "false")
        boolean mustChangePassword,
    @Schema(
            description = "Última versión del aviso de privacidad aceptada.",
            example = "1",
            nullable = true)
        Integer privacyAcceptedVersion,
    @Schema(description = "Membresías vigentes.") List<MembershipResponse> memberships,
    @Schema(description = "Permisos del rol de la sesión.", example = "[\"READING_CREATE\"]")
        List<String> permissions) {

  /**
   * One membership in force.
   *
   * @param aqueductId aqueduct
   * @param role role code
   * @param validFrom start of the validity
   * @param validTo end of the validity, null while open
   */
  @Schema(description = "Rol de la persona en un acueducto.")
  public record MembershipResponse(
      @Schema(
              description = "Identificador del acueducto.",
              example = "0190f3a2-0000-7000-8000-0000000000aa")
          UUID aqueductId,
      @Schema(description = "Código del rol.", example = "OPERATOR") String role,
      @Schema(description = "Inicio de la vigencia.") Instant validFrom,
      @Schema(description = "Fin de la vigencia; vacío si no vence.", nullable = true)
          Instant validTo) {}
}
