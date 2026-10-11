package co.caudal.infrastructure.document;

/** Puts the parts of one family together into the final bytes. */
public interface DocumentAssembler {

  /**
   * Adds a part.
   *
   * @param part a part of the same family
   * @throws IllegalArgumentException if the part comes from another family
   */
  void add(DocumentPart part);

  /**
   * The finished document.
   *
   * @return its bytes
   */
  byte[] finish();
}
