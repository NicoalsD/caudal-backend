package co.caudal.infrastructure.document;

/**
 * Creates the generator of one kind of document. Each subclass decides which generator to build;
 * the client never names a concrete generator.
 *
 * @pattern P02 Factory Method
 */
public abstract class DocumentGeneratorCreator {

  /**
   * The factory method.
   *
   * @param factory the family of parts
   * @return the generator
   */
  protected abstract DocumentGenerator createGenerator(DocumentFactory factory);

  /**
   * The generator for a family of parts.
   *
   * @param factory the family
   * @return the generator
   */
  public final DocumentGenerator generatorFor(DocumentFactory factory) {
    return createGenerator(factory);
  }
}
