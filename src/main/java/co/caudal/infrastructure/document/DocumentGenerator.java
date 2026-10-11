package co.caudal.infrastructure.document;

import java.util.List;
import java.util.Objects;

/**
 * The fixed order of every document: header, sections, signatures, then the end. The template
 * method is final; a generator only fills the sections and the end, and never rewrites the header
 * or the signatures. It uses one family of parts from start to end.
 *
 * @pattern P20 Template Method
 */
public abstract class DocumentGenerator {

  private final DocumentFactory factory;

  /**
   * Creates the generator.
   *
   * @param factory the family of parts
   */
  protected DocumentGenerator(DocumentFactory factory) {
    this.factory = Objects.requireNonNull(factory, "factory");
  }

  /**
   * The template method.
   *
   * @param content what the document says
   * @return its bytes
   */
  public final byte[] generate(DocumentContent content) {
    DocumentAssembler assembler = factory.createAssembler();
    assembler.add(factory.createHeader(content.metadata()));
    writeSections(content.sections(), factory, assembler);
    assembler.add(factory.createSignatures(content.signatories()));
    return finish(assembler);
  }

  /**
   * Adds the sections in the way of this kind of document.
   *
   * @param sections the sections
   * @param factory the family of parts
   * @param assembler where the parts go
   */
  protected abstract void writeSections(
      List<SectionContent> sections, DocumentFactory factory, DocumentAssembler assembler);

  /**
   * Ends the document.
   *
   * @param assembler the parts so far
   * @return the bytes
   */
  protected abstract byte[] finish(DocumentAssembler assembler);
}
