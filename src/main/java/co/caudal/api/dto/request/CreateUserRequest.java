package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Data to create an account. The username and the full name are normalized and checked again by the
 * domain; here only the size of the input and the shape of the role code are checked.
 *
 * @param username username, lower case ASCII
 * @param fullName full name of the person
 * @param aqueductId aqueduct of the first membership; must be the one of the session
 * @param role role code of the first membership
 */
@Schema(description = "Alta de una persona con su primera membresía.")
public record CreateUserRequest(
    @Schema(
            description = "Nombre de usuario: de 3 a 32 caracteres ASCII en minúscula.",
            example = "operador.ejemplo")
        @NotNull
        @Size(min = FieldLimits.USERNAME_MIN, max = FieldLimits.USERNAME_MAX)
        String username,
    @Schema(
            description = "Nombre completo: de 2 a 80 letras, espacios, punto, apóstrofo y guion.",
            example = "Persona de Ejemplo")
        @NotNull
        @Size(max = FieldLimits.PERSON_NAME_MAX * 2)
        String fullName,
    @Schema(
            description = "Acueducto de la membresía; debe ser el de la sesión.",
            example = "0190f3a2-0000-7000-8000-0000000000aa")
        @NotNull
        UUID aqueductId,
    @Schema(
            description =
                "Rol: BOARD_ADMIN, BOARD_MEMBER, OPERATOR, PROJECT_TEAM o SUPPORT_ENTITY.",
            example = "OPERATOR")
        @NotNull
        @Size(min = FieldLimits.CATALOG_CODE_MIN, max = FieldLimits.CATALOG_CODE_MAX)
        @Pattern(regexp = FieldLimits.CATALOG_CODE_PATTERN)
        String role) {}
