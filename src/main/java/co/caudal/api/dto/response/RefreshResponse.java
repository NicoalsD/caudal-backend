package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Answer of a successful refresh. The new refresh token is in the {@code caudal_rt} cookie.
 *
 * @param accessToken new access token
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the access token expires
 */
@Schema(description = "Access token nuevo. El refresh token rotado va en la cookie caudal_rt.")
public record RefreshResponse(
    @Schema(description = "Access token JWT de 15 minutos.") String accessToken,
    @Schema(description = "Tipo de token.", example = "Bearer") String tokenType,
    @Schema(description = "Segundos hasta que vence el access token.", example = "900")
        long expiresIn) {}
