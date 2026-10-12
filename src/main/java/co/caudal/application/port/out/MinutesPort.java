package co.caudal.application.port.out;

import co.caudal.application.minutes.MinutesView;
import co.caudal.domain.minutes.Minutes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Minutes of the Board. Row level security limits the aqueduct. */
public interface MinutesPort {

  /**
   * Stores new minutes as a draft.
   *
   * @param aqueductId the aqueduct
   * @param minutes the content
   * @param generatedBy who generated them
   * @return their id
   */
  UUID insert(UUID aqueductId, Minutes minutes, UUID generatedBy);

  /**
   * Reads minutes.
   *
   * @param id the minutes
   * @return the minutes, or empty
   */
  Optional<MinutesView> find(UUID id);

  /**
   * Turns a draft into the final minutes; a final document never changes again.
   *
   * @param id the minutes
   * @param finalizedBy who finalizes them
   * @param finalizedAt when
   * @param pdfSha256 hash of the final PDF, or null
   * @param auditHeadHash head of the audit chain, or null
   * @return true if it was a draft and changed
   */
  boolean finalizeDraft(
      UUID id, UUID finalizedBy, Instant finalizedAt, String pdfSha256, String auditHeadHash);

  /**
   * The hash of the last row of the audit chain of the aqueduct.
   *
   * @return the hash, or empty if the chain is empty
   */
  Optional<String> auditHeadHash();

  /**
   * Tells whether a final minutes covers every day of a period.
   *
   * @param from first day of the period
   * @param to last day of the period
   * @return true if a {@code FINAL} minutes starts on or before {@code from} and ends on or after
   *     {@code to}
   */
  boolean hasFinalCovering(LocalDate from, LocalDate to);
}
