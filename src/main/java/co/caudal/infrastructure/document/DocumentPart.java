package co.caudal.infrastructure.document;

/** A piece of a document made by one {@link DocumentFactory}. */
public interface DocumentPart {

  /**
   * The family that made it.
   *
   * @return for example "PDF" or "HTML"
   */
  String format();
}
