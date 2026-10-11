package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * An account as the Board administers it.
 *
 * @param id account id
 * @param username username
 * @param fullName full name (confidential)
 * @param status account status
 * @param lockedUntil end of a lock caused by failed logins
 * @param mustChangePassword true until the person sets a password
 * @param lastLoginAt last successful login
 */
@Schema(description = "Cuenta del acueducto vista por la Junta.")
public record ManagedUserResponse(
    @Schema(
            description = "Identificador de la cuenta.",
            example = "0190f3a2-0000-7000-8000-000000000001")
        UUID id,
    @Schema(description = "Nombre de usuario.", example = "operador.ejemplo") String username,
    @Schema(description = "Nombre completo (dato confidencial).", example = "Persona de Ejemplo")
        String fullName,
    @Schema(description = "Estado: ACTIVE, LOCKED o DISABLED.", example = "ACTIVE") String status,
    @Schema(
            description = "Fin del bloqueo por intentos fallidos; nulo si no hay o no tiene fin.",
            nullable = true)
        Instant lockedUntil,
    @Schema(description = "Verdadero hasta que la persona fije su contraseña.")
        boolean mustChangePassword,
    @Schema(description = "Último ingreso exitoso; nulo si nunca ingresó.", nullable = true)
        Instant lastLoginAt) {}
