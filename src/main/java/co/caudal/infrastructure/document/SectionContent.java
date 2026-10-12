package co.caudal.infrastructure.document;

import java.util.List;

/**
 * A section of a document.
 *
 * @param heading its heading, or null
 * @param lines its lines
 */
public record SectionContent(String heading, List<String> lines) {

  /** Copies the lines. */
  public SectionContent {
    lines = List.copyOf(lines);
  }
}
