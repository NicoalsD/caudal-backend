package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Public directory of institutions.
 *
 * @param items the institutions, by name
 */
@Schema(description = "Directorio público de instituciones activas.")
public record PublicInstitutionsResponse(
    @Schema(description = "Instituciones, ordenadas por nombre.") List<Item> items) {

  /**
   * One institution: nothing else is public.
   *
   * @param slug identifier used at login
   * @param name display name
   */
  @Schema(description = "Institución del directorio público.")
  public record Item(
      @Schema(description = "Identificador en minúsculas.", example = "maqueta-caudal") String slug,
      @Schema(description = "Nombre.", example = "Maqueta CAUDAL") String name) {}
}
