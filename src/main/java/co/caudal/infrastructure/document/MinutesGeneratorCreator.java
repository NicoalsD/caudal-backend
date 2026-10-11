package co.caudal.infrastructure.document;

/**
 * Creates the generator of minutes.
 *
 * @pattern P02 Factory Method
 */
public final class MinutesGeneratorCreator extends DocumentGeneratorCreator {

  @Override
  protected DocumentGenerator createGenerator(DocumentFactory factory) {
    return new MinutesGenerator(factory);
  }
}
