package co.caudal.api.dto.response;

import co.caudal.application.minutes.MinutesSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Minutes as they appear in the list, without the sections.
 *
 * @param id the minutes
 * @param periodFrom first day
 * @param periodTo last day
 * @param status DRAFT or FINAL
 * @param generatedAt when the minutes were generated
 * @param finalizedAt when the minutes were finalized
 * @param pdfSha256 hash of the final PDF
 */
@Schema(description = "Resumen de un acta, sin sus secciones.")
public record MinutesSummaryResponse(
    @Schema(description = "Identificador del acta.") UUID id,
    @Schema(description = "Primer día del periodo.", example = "2026-10-01") LocalDate periodFrom,
    @Schema(description = "Último día del periodo.", example = "2026-10-31") LocalDate periodTo,
    @Schema(description = "Estado del acta.", example = "DRAFT") String status,
    @Schema(description = "Momento en que se generó el acta.") Instant generatedAt,
    @Schema(description = "Momento del cierre; nulo mientras sea borrador.", nullable = true)
        Instant finalizedAt,
    @Schema(description = "Hash del PDF final; nulo mientras sea borrador.", nullable = true)
        String pdfSha256) {

  /**
   * Maps the summary of minutes.
   *
   * @param m the summary
   * @return the response
   */
  public static MinutesSummaryResponse of(MinutesSummaryView m) {
    return new MinutesSummaryResponse(
        m.id(),
        m.periodFrom(),
        m.periodTo(),
        m.status(),
        m.generatedAt(),
        m.finalizedAt(),
        m.pdfSha256());
  }
}
