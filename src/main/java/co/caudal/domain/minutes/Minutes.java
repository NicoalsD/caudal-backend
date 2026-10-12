package co.caudal.domain.minutes;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The content of the minutes of a period: the four sections, each in time order.
 *
 * @param periodFrom first day
 * @param periodTo last day
 * @param sections the entries of each section
 */
public record Minutes(
    LocalDate periodFrom, LocalDate periodTo, Map<MinutesSection, List<MinutesEntry>> sections) {

  /** Freezes the sections. */
  public Minutes {
    Map<MinutesSection, List<MinutesEntry>> copy = new EnumMap<>(MinutesSection.class);
    sections.forEach((section, entries) -> copy.put(section, List.copyOf(entries)));
    sections = Map.copyOf(copy);
  }

  /**
   * The entries of a section.
   *
   * @param section the section
   * @return its entries, in time order
   */
  public List<MinutesEntry> section(MinutesSection section) {
    return sections.get(section);
  }
}
