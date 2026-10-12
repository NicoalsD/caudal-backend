package co.caudal.api.dto.response;

import co.caudal.application.minutes.MinutesView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minutes of the Board.
 *
 * @param id the minutes
 * @param periodFrom first day
 * @param periodTo last day
 * @param status DRAFT or FINAL
 * @param content the sections observed, estimated, inferred and confirmed
 * @param generatedBy who generated them
 * @param generatedAt when
 * @param pdfSha256 hash of the final PDF
 * @param auditHeadHash head of the audit chain when finalized
 * @param finalizedBy who finalized them
 * @param finalizedAt when
 */
@Schema(description = "Acta con sus cuatro secciones por estatus epistémico.")
@SuppressWarnings("checkstyle:ParameterNumber")
public record MinutesResponse(
    UUID id,
    LocalDate periodFrom,
    LocalDate periodTo,
    @Schema(example = "DRAFT") String status,
    Map<String, List<Entry>> content,
    UUID generatedBy,
    Instant generatedAt,
    @Schema(nullable = true) String pdfSha256,
    @Schema(nullable = true) String auditHeadHash,
    @Schema(nullable = true) UUID finalizedBy,
    @Schema(nullable = true) Instant finalizedAt) {

  /**
   * One entry of a section.
   *
   * @param source the kind of record
   * @param sourceId its id
   * @param at when
   * @param summary the sentence
   */
  @Schema(description = "Registro del acta.")
  public record Entry(String source, String sourceId, Instant at, String summary) {}

  /**
   * Maps minutes.
   *
   * @param m the minutes
   * @return the response
   */
  public static MinutesResponse of(MinutesView m) {
    Map<String, List<Entry>> content = new LinkedHashMap<>();
    for (String key : List.of("observed", "estimated", "inferred", "confirmed")) {
      content.put(
          key,
          m.content().getOrDefault(key, List.of()).stream()
              .map(e -> new Entry(e.source(), e.sourceId(), e.at(), e.summary()))
              .toList());
    }
    return new MinutesResponse(
        m.id(),
        m.periodFrom(),
        m.periodTo(),
        m.status(),
        content,
        m.generatedBy(),
        m.generatedAt(),
        m.pdfSha256(),
        m.auditHeadHash(),
        m.finalizedBy(),
        m.finalizedAt());
  }
}
