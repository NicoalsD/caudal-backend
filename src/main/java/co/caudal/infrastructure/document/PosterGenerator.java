package co.caudal.infrastructure.document;

import java.util.List;

/** The printed poster of a published schedule: a big header and one section per block. */
public final class PosterGenerator extends DocumentGenerator {

  /**
   * Creates the generator.
   *
   * @param factory the family of parts it uses from start to end
   */
  public PosterGenerator(DocumentFactory factory) {
    super(factory);
  }

  @Override
  protected void writeSections(
      List<SectionContent> sections, DocumentFactory factory, DocumentAssembler assembler) {
    sections.forEach(section -> assembler.add(factory.createSection(section)));
  }

  @Override
  protected byte[] finish(DocumentAssembler assembler) {
    return assembler.finish();
  }
}
