package co.caudal.application.minutes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stored minutes.
 *
 * @param id the minutes
 * @param periodFrom first day
 * @param periodTo last day
 * @param status DRAFT or FINAL
 * @param content the four sections by key, each in time order
 * @param generatedBy who generated them
 * @param generatedAt when
 * @param pdfSha256 hash of the final PDF, or null
 * @param auditHeadHash head of the audit chain when finalized, or null
 * @param finalizedBy who finalized them, or null
 * @param finalizedAt when, or null
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public record MinutesView(
    UUID id,
    LocalDate periodFrom,
    LocalDate periodTo,
    String status,
    Map<String, List<Entry>> content,
    UUID generatedBy,
    Instant generatedAt,
    String pdfSha256,
    String auditHeadHash,
    UUID finalizedBy,
    Instant finalizedAt) {

  /** Freezes the content. */
  public MinutesView {
    content = Map.copyOf(content);
  }

  /**
   * One entry of a section.
   *
   * @param source the kind of record
   * @param sourceId its id
   * @param at when
   * @param summary the sentence
   */
  public record Entry(String source, String sourceId, Instant at, String summary) {}
}
