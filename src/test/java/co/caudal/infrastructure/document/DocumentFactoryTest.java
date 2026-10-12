package co.caudal.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentFactoryTest {

  static final DocumentMetadata META = new DocumentMetadata("Horario", "Acueducto <b>", "Aviso");
  static final SectionContent SECTION = new SectionContent("Turnos", List.of("Alto & Bajo"));

  @Test
  void everyPartOfAFamilyReportsTheSameFormat() {
    for (DocumentFactory factory :
        List.<DocumentFactory>of(new PdfDocumentFactory(), new HtmlDocumentFactory())) {
      assertThat(
              List.of(
                  factory.createHeader(META).format(),
                  factory.createSection(SECTION).format(),
                  factory.createSignatures(List.of("Presidente")).format()))
          .containsOnly(factory.format());
    }
  }

  @Test
  void htmlEscapesEverything() {
    HtmlDocumentFactory html = new HtmlDocumentFactory();
    DocumentAssembler assembler = html.createAssembler();
    assembler.add(html.createHeader(META));
    assembler.add(html.createSection(SECTION));
    assembler.add(html.createSignatures(List.of("O'Neil \"Tesorera\"")));

    String page = new String(assembler.finish(), StandardCharsets.UTF_8);

    assertThat(page)
        .contains("Acueducto &lt;b&gt;")
        .contains("Alto &amp; Bajo")
        .contains("O&#39;Neil &quot;Tesorera&quot;")
        .doesNotContain("<b>");
  }

  @Test
  void mixingFamiliesIsRefused() {
    DocumentAssembler pdf = new PdfDocumentFactory().createAssembler();
    DocumentAssembler html = new HtmlDocumentFactory().createAssembler();

    assertThatThrownBy(() -> pdf.add(new HtmlDocumentFactory().createHeader(META)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> html.add(new PdfDocumentFactory().createHeader(META)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void thePdfIsAlwaysTheSameForTheSameContent() {
    PdfDocumentFactory pdf = new PdfDocumentFactory();
    byte[] first = render(pdf);
    byte[] second = render(pdf);

    assertThat(first).isEqualTo(second);
    String text = new String(first, StandardCharsets.US_ASCII);
    assertThat(text)
        .startsWith("%PDF-1.4")
        .endsWith("%%EOF\n")
        .contains("/BaseFont /Helvetica")
        .doesNotContain("/CreationDate")
        .doesNotContain("/ID");
  }

  @Test
  void pdfTextIsEscapedAndInWindows1252() {
    assertThat(SimplePdf.escape("Turno (Alto) \\ sí")).isEqualTo("Turno \\(Alto\\) \\\\ s\\355");
  }

  @Test
  void longLinesWrapAndManyLinesMakeMorePages() {
    String longLine = "palabra ".repeat(60).strip();
    assertThat(SimplePdf.wrap(new PdfLine(longLine, 13, false, 0))).hasSizeGreaterThan(1);

    PdfDocumentFactory pdf = new PdfDocumentFactory();
    DocumentAssembler assembler = pdf.createAssembler();
    assembler.add(
        pdf.createSection(new SectionContent(null, java.util.Collections.nCopies(80, "x"))));
    String text = new String(assembler.finish(), StandardCharsets.US_ASCII);

    assertThat(text).contains("/Count 2");
  }

  private static byte[] render(PdfDocumentFactory pdf) {
    DocumentAssembler assembler = pdf.createAssembler();
    assembler.add(pdf.createHeader(META));
    assembler.add(pdf.createSection(SECTION));
    assembler.add(pdf.createSignatures(List.of("Presidente")));
    return assembler.finish();
  }
}
