package co.caudal.domain.minutes;

import java.time.Instant;
import java.util.Objects;

/**
 * One fact of the period as the minutes tell it.
 *
 * @param source what kind of record it comes from, such as "reading" or "incident"
 * @param sourceId the id of the record
 * @param at when it happened
 * @param summary the sentence in Spanish, without personal data
 */
public record MinutesEntry(String source, String sourceId, Instant at, String summary) {

  /** Requires every part. */
  public MinutesEntry {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(at, "at");
    Objects.requireNonNull(summary, "summary");
  }
}
