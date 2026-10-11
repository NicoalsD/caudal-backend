package co.caudal.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.publication.PosterData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentGeneratorCreatorTest {

  @Test
  void thePosterCreatorBuildsAPosterGenerator() {
    assertThat(new PosterGeneratorCreator().generatorFor(new PdfDocumentFactory()))
        .isInstanceOf(PosterGenerator.class);
  }

  @Test
  void theMinutesCreatorBuildsAMinutesGenerator() {
    assertThat(new MinutesGeneratorCreator().generatorFor(new HtmlDocumentFactory()))
        .isInstanceOf(MinutesGenerator.class);
  }

  @Test
  void theAdapterRendersTheMinutesWithSignatures() {
    byte[] pdf =
        new DocumentRenderingAdapter()
            .minutes(
                new co.caudal.application.minutes.MinutesDocument(
                    "BORRADOR",
                    "Acta del acueducto",
                    "Vereda, 1 al 7 de octubre",
                    List.of(
                        new co.caudal.application.minutes.MinutesDocument.DocumentSection(
                            "Observado", List.of("Lectura 2,10"))),
                    List.of("Presidente de la Junta")));

    assertThat(new String(pdf, StandardCharsets.US_ASCII))
        .contains("(BORRADOR) Tj")
        .contains("(Presidente de la Junta) Tj");
  }

  @Test
  void theAdapterRendersThePosterAsAPdf() {
    byte[] pdf =
        new DocumentRenderingAdapter()
            .poster(
                new PosterData(
                    null,
                    "Horario de agua",
                    "Acueducto Demo, lunes 12 de octubre",
                    List.of("Sector Alto: de 5:00 a. m. a 9:00 a. m."),
                    "Cuide el agua."));

    String text = new String(pdf, StandardCharsets.US_ASCII);
    assertThat(text).startsWith("%PDF-1.4").contains("(Horario de agua) Tj");
  }
}
