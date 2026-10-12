package co.caudal.infrastructure.document;

/**
 * Creates the generator of posters.
 *
 * @pattern P02 Factory Method
 */
public final class PosterGeneratorCreator extends DocumentGeneratorCreator {

  @Override
  protected DocumentGenerator createGenerator(DocumentFactory factory) {
    return new PosterGenerator(factory);
  }
}
