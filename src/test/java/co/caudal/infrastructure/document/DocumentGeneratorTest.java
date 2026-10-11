package co.caudal.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentGeneratorTest {

  /** A family that writes down the order in which its parts are used. */
  static final class RecordingFactory implements DocumentFactory {
    final List<String> calls = new ArrayList<>();

    record Part(String name) implements DocumentHeader, DocumentSection, DocumentSignatures {
      @Override
      public String format() {
        return "TEST";
      }
    }

    @Override
    public DocumentHeader createHeader(DocumentMetadata metadata) {
      return new Part("header");
    }

    @Override
    public DocumentSection createSection(SectionContent content) {
      return new Part("section:" + String.join("|", content.lines()));
    }

    @Override
    public DocumentSignatures createSignatures(List<String> signatories) {
      return new Part("signatures");
    }

    @Override
    public DocumentAssembler createAssembler() {
      return new DocumentAssembler() {
        @Override
        public void add(DocumentPart part) {
          calls.add(((Part) part).name());
        }

        @Override
        public byte[] finish() {
          calls.add("finish");
          return String.join(",", calls).getBytes(StandardCharsets.UTF_8);
        }
      };
    }

    @Override
    public String format() {
      return "TEST";
    }
  }

  static final DocumentContent CONTENT =
      new DocumentContent(
          new DocumentMetadata("Acta", null, null),
          List.of(
              new SectionContent("Observado", List.of("a")),
              new SectionContent("Estimado", List.of())),
          List.of("Presidente"));

  @Test
  void everyDocumentGoesHeaderSectionsSignaturesAndEnd() {
    RecordingFactory factory = new RecordingFactory();

    new PosterGenerator(factory).generate(CONTENT);

    assertThat(factory.calls)
        .containsExactly("header", "section:a", "section:", "signatures", "finish");
  }

  @Test
  void theMinutesSayWhenASectionIsEmpty() {
    RecordingFactory factory = new RecordingFactory();

    new MinutesGenerator(factory).generate(CONTENT);

    assertThat(factory.calls)
        .containsExactly(
            "header", "section:a", "section:" + MinutesGenerator.EMPTY, "signatures", "finish");
  }
}
