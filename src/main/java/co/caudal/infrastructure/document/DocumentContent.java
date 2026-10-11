package co.caudal.infrastructure.document;

import java.util.List;

/**
 * Everything a document says, before a family gives it its format.
 *
 * @param metadata the top
 * @param sections the body
 * @param signatories who signs, may be empty
 */
public record DocumentContent(
    DocumentMetadata metadata, List<SectionContent> sections, List<String> signatories) {

  /** Copies the lists. */
  public DocumentContent {
    sections = List.copyOf(sections);
    signatories = List.copyOf(signatories);
  }
}
