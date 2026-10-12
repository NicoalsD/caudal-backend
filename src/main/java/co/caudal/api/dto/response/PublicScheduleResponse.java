package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The published schedule of a day, without personal data.
 *
 * @param aqueduct the aqueduct
 * @param serviceDate the day of service
 * @param publication the publication
 * @param items the shifts
 */
@Schema(description = "Horario publicado de un día, sin datos personales.")
public record PublicScheduleResponse(
    Aqueduct aqueduct, LocalDate serviceDate, Publication publication, List<Item> items) {

  /**
   * The aqueduct.
   *
   * @param name its name
   * @param isDemo true if its data are simulated
   * @param banner the notice of simulated data, or null
   */
  @Schema(description = "Acueducto.")
  public record Aqueduct(
      String name,
      boolean isDemo,
      @Schema(nullable = true, example = "Datos simulados") String banner) {}

  /**
   * The publication.
   *
   * @param id the publication
   * @param publishedAt when
   */
  @Schema(description = "Publicación.")
  public record Publication(UUID id, Instant publishedAt) {}

  /**
   * A shift.
   *
   * @param sectorName the sector
   * @param startAt start
   * @param endAt end
   */
  @Schema(description = "Turno de un sector.")
  public record Item(String sectorName, Instant startAt, Instant endAt) {}
}
