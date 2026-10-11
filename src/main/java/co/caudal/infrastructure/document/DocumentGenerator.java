package co.caudal.infrastructure.document;

/** Turns the content of one kind of document into bytes, with one family of parts. */
public interface DocumentGenerator {

  /**
   * Generates the document.
   *
   * @param content what it says
   * @return its bytes
   */
  byte[] generate(DocumentContent content);
}
