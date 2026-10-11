package co.caudal.infrastructure.document;

import co.caudal.application.minutes.MinutesDocument;
import co.caudal.application.port.out.DocumentRenderingPort;
import co.caudal.application.publication.PosterData;
import java.util.List;

/**
 * Renders documents through the creators, never through a concrete generator: the poster with
 * {@link PosterGeneratorCreator}, the minutes with {@link MinutesGeneratorCreator}, both in the PDF
 * family.
 *
 * @pattern P06 Adapter
 */
public class DocumentRenderingAdapter implements DocumentRenderingPort {

  private final DocumentGeneratorCreator posters = new PosterGeneratorCreator();
  private final DocumentGeneratorCreator minutes = new MinutesGeneratorCreator();
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

  @Override
  public byte[] minutes(MinutesDocument document) {
    return minutes
        .generatorFor(pdf)
        .generate(
            new DocumentContent(
                new DocumentMetadata(document.title(), document.subtitle(), document.banner()),
                document.sections().stream()
                    .map(s -> new SectionContent(s.heading(), s.lines()))
                    .toList(),
                document.signatories()));
  }
}
