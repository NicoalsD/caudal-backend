package co.caudal.infrastructure.document;

import java.util.List;

/**
 * The minutes of a period: one section per epistemic status, and a line that says so when a section
 * has no records, so an empty section is never mistaken for a missing one.
 */
public final class MinutesGenerator extends DocumentGenerator {

  static final String EMPTY = "Sin registros en el periodo.";

  /**
   * Creates the generator.
   *
   * @param factory the family of parts it uses from start to end
   */
  public MinutesGenerator(DocumentFactory factory) {
    super(factory);
  }

  @Override
  protected void writeSections(
      List<SectionContent> sections, DocumentFactory factory, DocumentAssembler assembler) {
    for (SectionContent section : sections) {
      SectionContent shown =
          section.lines().isEmpty()
              ? new SectionContent(section.heading(), List.of(EMPTY))
              : section;
      assembler.add(factory.createSection(shown));
    }
  }

  @Override
  protected byte[] finish(DocumentAssembler assembler) {
    return assembler.finish();
  }
}
