package co.caudal.domain.minutes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the minutes of a period step by step and checks them only at the end: the period must be
 * set and not inverted, and the four sections always exist, even when one is empty. Each section
 * comes out in time order.
 *
 * @pattern P04 Builder
 */
public final class MinutesBuilder {

  private static final Comparator<MinutesEntry> BY_TIME =
      Comparator.comparing(MinutesEntry::at).thenComparing(MinutesEntry::sourceId);

  private static final SectionClassifier CLASSIFIER = new SectionClassifier();

  private LocalDate periodFrom;
  private LocalDate periodTo;
  private final Map<MinutesSection, List<MinutesEntry>> sections =
      new EnumMap<>(MinutesSection.class);

  /** Starts empty minutes with the four sections. */
  public MinutesBuilder() {
    for (MinutesSection section : MinutesSection.values()) {
      sections.put(section, new ArrayList<>());
    }
  }

  /**
   * Sets the period.
   *
   * @param from first day
   * @param to last day
   * @return this builder
   */
  public MinutesBuilder period(LocalDate from, LocalDate to) {
    this.periodFrom = Objects.requireNonNull(from, "from");
    this.periodTo = Objects.requireNonNull(to, "to");
    return this;
  }

  /**
   * Adds an entry to a section.
   *
   * @param section the section
   * @param entry the entry
   * @return this builder
   */
  public MinutesBuilder add(MinutesSection section, MinutesEntry entry) {
    sections.get(Objects.requireNonNull(section, "section")).add(Objects.requireNonNull(entry));
    return this;
  }

  /**
   * Adds a record in the section its kind says, through the {@link SectionClassifier}.
   *
   * @param record the record
   * @return this builder
   */
  public MinutesBuilder add(EpistemicRecord record) {
    return add(record.accept(CLASSIFIER), record.entry());
  }

  /**
   * The finished minutes.
   *
   * @return the minutes
   * @throws IllegalStateException without a period or with an inverted one
   */
  public Minutes build() {
    if (periodFrom == null || periodTo == null) {
      throw new IllegalStateException("The minutes need a period");
    }
    if (periodTo.isBefore(periodFrom)) {
      throw new IllegalStateException("The period of the minutes is inverted");
    }
    Map<MinutesSection, List<MinutesEntry>> sorted = new EnumMap<>(MinutesSection.class);
    sections.forEach(
        (section, entries) -> sorted.put(section, entries.stream().sorted(BY_TIME).toList()));
    return new Minutes(periodFrom, periodTo, sorted);
  }
}
