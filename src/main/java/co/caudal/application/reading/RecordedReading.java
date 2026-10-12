package co.caudal.application.reading;

import java.util.List;

/**
 * Result of recording a reading.
 *
 * @param reading the stored reading
 * @param issues the problems found, empty if none
 * @param replay true if the reading already existed with the same content
 */
public record RecordedReading(ReadingView reading, List<IssueView> issues, boolean replay) {

  /** Copies the list so the record is immutable. */
  public RecordedReading {
    issues = List.copyOf(issues);
  }
}
