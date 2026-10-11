package co.caudal.infrastructure.document;

import co.caudal.application.port.out.DocumentRenderingPort;
import co.caudal.application.publication.PosterData;
import java.util.List;

/**
 * Renders documents through the creators, never through a concrete generator: the poster with
 * {@link PosterGeneratorCreator} and the PDF family.
 *
 * @pattern P06 Adapter
 */
public class DocumentRenderingAdapter implements DocumentRenderingPort {

  private final DocumentGeneratorCreator posters = new PosterGeneratorCreator();
  private final DocumentFactory pdf = new PdfDocumentFactory();

  @Override
  public byte[] poster(PosterData data) {
    return posters
        .generatorFor(pdf)
        .generate(
            new DocumentContent(
                new DocumentMetadata(data.title(), data.subtitle(), data.banner()),
                List.of(
                    new SectionContent(null, data.lines()),
                    new SectionContent(null, List.of(data.closing()))),
                List.of()));
  }
}
