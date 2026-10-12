package co.caudal.application.minutes;

import java.util.List;

/**
 * What the PDF of the minutes says.
 *
 * @param banner a mark above the title, such as "BORRADOR", or null
 * @param title the title
 * @param subtitle the aqueduct and the period
 * @param sections the four sections
 * @param signatories the signature lines
 */
public record MinutesDocument(
    String banner,
    String title,
    String subtitle,
    List<DocumentSection> sections,
    List<String> signatories) {

  /** Copies the lists. */
  public MinutesDocument {
    sections = List.copyOf(sections);
    signatories = List.copyOf(signatories);
  }

  /**
   * A section of the document.
   *
   * @param heading its title
   * @param lines its lines
   */
  public record DocumentSection(String heading, List<String> lines) {

    /** Copies the lines. */
    public DocumentSection {
      lines = List.copyOf(lines);
    }
  }
}
