package co.caudal.infrastructure.document;

import java.util.List;

/**
 * A family of document parts: each implementation makes every part in one output format, and the
 * assembler that joins them. A generator uses one family from start to end; mixing families is an
 * error.
 *
 * @pattern P03 Abstract Factory
 */
public interface DocumentFactory {

  /**
   * Makes the top of the document.
   *
   * @param metadata title, subtitle and banner
   * @return the header
   */
  DocumentHeader createHeader(DocumentMetadata metadata);

  /**
   * Makes a section.
   *
   * @param content heading and lines
   * @return the section
   */
  DocumentSection createSection(SectionContent content);

  /**
   * Makes the signature lines.
   *
   * @param signatories who signs
   * @return the signatures
   */
  DocumentSignatures createSignatures(List<String> signatories);

  /**
   * A new assembler of this family.
   *
   * @return the assembler
   */
  DocumentAssembler createAssembler();

  /**
   * The name of the family.
   *
   * @return for example "PDF" or "HTML"
   */
  String format();
}
