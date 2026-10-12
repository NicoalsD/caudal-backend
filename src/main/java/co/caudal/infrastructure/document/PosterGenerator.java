package co.caudal.infrastructure.document;

import java.util.Objects;

/** The printed poster of a published schedule: a big header and one section per block. */
public final class PosterGenerator implements DocumentGenerator {

  private final DocumentFactory factory;

  /**
   * Creates the generator.
   *
   * @param factory the family of parts it uses from start to end
   */
  public PosterGenerator(DocumentFactory factory) {
    this.factory = Objects.requireNonNull(factory, "factory");
  }

  @Override
  public byte[] generate(DocumentContent content) {
    DocumentAssembler assembler = factory.createAssembler();
    assembler.add(factory.createHeader(content.metadata()));
    content.sections().forEach(section -> assembler.add(factory.createSection(section)));
    return assembler.finish();
  }
}
