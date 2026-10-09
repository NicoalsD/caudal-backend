package co.caudal.api.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Canonical error body: {@code {"error": {"code", "message", "details", "request_id"}}}.
 *
 * @param error the error content
 */
@Schema(description = "Formato canónico de error de la API.")
public record ErrorResponse(@Schema(description = "Contenido del error.") Body error) {

  /**
   * Creates a response from its parts.
   *
   * @param code error code of the catalog
   * @param message message in Spanish for the user
   * @param details safe details, never secrets or traces
   * @param requestId correlation id, also sent in {@code X-Request-Id}
   * @return the error response
   */
  public static ErrorResponse of(
      String code, String message, Map<String, Object> details, String requestId) {
    return new ErrorResponse(new Body(code, message, Map.copyOf(details), requestId));
  }

  /**
   * Error content.
   *
   * @param code error code
   * @param message message in Spanish
   * @param details safe details
   * @param requestId correlation id
   */
  @Schema(description = "Detalle del error.")
  public record Body(
      @Schema(description = "Código del error, en inglés.", example = "GAUGE_OUT_OF_RANGE")
          String code,
      @Schema(
              description = "Mensaje en español para la persona usuaria.",
              example = "La lectura está fuera del rango de la regla del tanque.")
          String message,
      @Schema(description = "Detalles adicionales, sin trazas ni secretos.")
          Map<String, Object> details,
      @Schema(
              description = "Identificador de la petición, igual al de los logs.",
              example = "7f0c2a1e-5b8d-4c1f-9a3e-2d6b8e4f1a90")
          String requestId) {}
}
