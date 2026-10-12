package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A one-time reset code, shown only in this response.
 *
 * @param id the grant
 * @param code the code
 * @param expiresAt when it stops working
 */
@Schema(description = "Código de un solo uso para fijar una contraseña; se muestra una sola vez.")
public record ResetGrantResponse(
    @Schema(description = "Identificador del código.") UUID id,
    @Schema(description = "Código, por ejemplo ABCD-EFGH-JKMN.") String code,
    @Schema(description = "Momento en que deja de servir.") Instant expiresAt) {

  @Override
  public String toString() {
    return "ResetGrantResponse[id=" + id + ", code=REDACTED]";
  }
}
