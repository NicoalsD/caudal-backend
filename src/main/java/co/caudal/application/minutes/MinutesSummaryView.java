package co.caudal.application.minutes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Minutes as they appear in the list (docs/API.md, section 13.8). It leaves out the sections, which
 * the detail of the minutes carries.
 *
 * @param id the minutes
 * @param periodFrom first day
 * @param periodTo last day
 * @param status DRAFT or FINAL
 * @param generatedAt when the minutes were generated
 * @param finalizedAt when the minutes were finalized, or null
 * @param pdfSha256 hash of the final PDF, or null while it is a draft
 */
public record MinutesSummaryView(
    UUID id,
    LocalDate periodFrom,
    LocalDate periodTo,
    String status,
    Instant generatedAt,
    Instant finalizedAt,
    String pdfSha256) {}
