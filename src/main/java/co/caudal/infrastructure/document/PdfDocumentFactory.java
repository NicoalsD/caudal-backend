package co.caudal.infrastructure.document;

import java.util.ArrayList;
import java.util.List;

/**
 * The PDF family: every part is a list of lines with their font, and the assembler lays them out
 * with {@link SimplePdf}.
 *
 * @pattern P03 Abstract Factory
 */
public final class PdfDocumentFactory implements DocumentFactory {

  static final String FORMAT = "PDF";

  private static final int BANNER_SIZE = 14;
  private static final int TITLE_SIZE = 26;
  private static final int SUBTITLE_SIZE = 16;
  private static final int HEADING_SIZE = 16;
  private static final int TEXT_SIZE = 13;
  private static final int GAP = 14;
  private static final String SIGNATURE_LINE = "______________________________";

  /** A part of the PDF family. */
  record PdfPart(List<PdfLine> lines)
      implements DocumentHeader, DocumentSection, DocumentSignatures {

    /** Copies the lines. */
    PdfPart {
      lines = List.copyOf(lines);
    }

    @Override
    public String format() {
      return FORMAT;
    }
  }

  @Override
  public DocumentHeader createHeader(DocumentMetadata metadata) {
    List<PdfLine> lines = new ArrayList<>();
    if (metadata.banner() != null) {
      lines.add(new PdfLine(metadata.banner(), BANNER_SIZE, true, 0));
    }
    lines.add(new PdfLine(metadata.title(), TITLE_SIZE, true, 0));
    if (metadata.subtitle() != null) {
      lines.add(new PdfLine(metadata.subtitle(), SUBTITLE_SIZE, false, 0));
    }
    return new PdfPart(lines);
  }

  @Override
  public DocumentSection createSection(SectionContent content) {
    List<PdfLine> lines = new ArrayList<>();
    if (content.heading() != null) {
      lines.add(new PdfLine(content.heading(), HEADING_SIZE, true, GAP));
    }
    for (int i = 0; i < content.lines().size(); i++) {
      boolean first = i == 0 && content.heading() == null;
      lines.add(new PdfLine(content.lines().get(i), TEXT_SIZE, false, first ? GAP : 0));
    }
    return new PdfPart(lines);
  }

  @Override
  public DocumentSignatures createSignatures(List<String> signatories) {
    List<PdfLine> lines = new ArrayList<>();
    for (String signatory : signatories) {
      lines.add(new PdfLine(SIGNATURE_LINE, TEXT_SIZE, false, GAP * 2));
      lines.add(new PdfLine(signatory, TEXT_SIZE, false, 0));
    }
    return new PdfPart(lines);
  }

  @Override
  public DocumentAssembler createAssembler() {
    return new DocumentAssembler() {
      private final List<PdfLine> lines = new ArrayList<>();

      @Override
      public void add(DocumentPart part) {
        if (!(part instanceof PdfPart pdf)) {
          throw new IllegalArgumentException("Part of another document family");
        }
        lines.addAll(pdf.lines());
      }

      @Override
      public byte[] finish() {
        return SimplePdf.write(lines);
      }
    };
  }

  @Override
  public String format() {
    return FORMAT;
  }
}
