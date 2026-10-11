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
