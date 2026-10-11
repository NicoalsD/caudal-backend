package co.caudal.infrastructure.document;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a small PDF 1.4 with the standard Helvetica fonts, A4 pages and lines of text. It writes
 * no dates and no random file id, so the same lines always give the same bytes and the same
 * SHA-256: the hash kept when a schedule is published still matches the poster served later.
 */
final class SimplePdf {

  private static final int PAGE_WIDTH = 595;
  private static final int PAGE_HEIGHT = 842;
  private static final int MARGIN = 56;
  private static final double AVERAGE_GLYPH = 0.52;
  private static final double LEADING = 1.35;
  private static final int FIRST_PAGE_OBJECT = 5;
  private static final int OBJECTS_PER_PAGE = 2;
  private static final int ASCII_LIMIT = 0x7F;
  private static final int BYTE_MASK = 0xFF;
  private static final Charset WIN_ANSI = Charset.forName("windows-1252");

  private SimplePdf() {}

  /**
   * Lays out the lines on pages and writes the PDF.
   *
   * @param lines the lines in order
   * @return the PDF bytes
   */
  static byte[] write(List<PdfLine> lines) {
    List<String> pages = layout(lines);
    List<String> objects = new ArrayList<>();
    StringBuilder kids = new StringBuilder();
    for (int i = 0; i < pages.size(); i++) {
      kids.append(FIRST_PAGE_OBJECT + i * OBJECTS_PER_PAGE).append(" 0 R ");
    }
    objects.add("<< /Type /Catalog /Pages 2 0 R >>");
    objects.add("<< /Type /Pages /Kids [ " + kids + "] /Count " + pages.size() + " >>");
    objects.add(
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
    objects.add(
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
    for (int i = 0; i < pages.size(); i++) {
      int content = FIRST_PAGE_OBJECT + i * OBJECTS_PER_PAGE + 1;
      objects.add(
          "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
              + PAGE_WIDTH
              + " "
              + PAGE_HEIGHT
              + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
              + content
              + " 0 R >>");
      String stream = pages.get(i);
      objects.add(
          "<< /Length "
              + stream.getBytes(StandardCharsets.US_ASCII).length
              + " >>\nstream\n"
              + stream
              + "endstream");
    }
    return serialize(objects);
  }

  private static byte[] serialize(List<String> objects) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write(out, "%PDF-1.4\n");
    List<Integer> offsets = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      offsets.add(out.size());
      write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
    }
    int xref = out.size();
    StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n");
    table.append("0000000000 65535 f \n");
    for (int offset : offsets) {
      table.append(String.format(Locale.ROOT, "%010d", offset)).append(" 00000 n \n");
    }
    table
        .append("trailer\n<< /Size ")
        .append(objects.size() + 1)
        .append(" /Root 1 0 R >>\nstartxref\n")
        .append(xref)
        .append("\n%%EOF\n");
    write(out, table.toString());
    return out.toByteArray();
  }

  /** One content stream per page; long lines wrap at spaces. */
  private static List<String> layout(List<PdfLine> lines) {
    List<String> pages = new ArrayList<>();
    StringBuilder page = new StringBuilder();
    double y = PAGE_HEIGHT - MARGIN;
    for (PdfLine line : lines) {
      List<String> pieces = wrap(line);
      for (int p = 0; p < pieces.size(); p++) {
        String piece = pieces.get(p);
        double step = line.size() * LEADING + (p == 0 ? line.gapBefore() : 0);
        if (y - step < MARGIN) {
          pages.add(page.toString());
          page = new StringBuilder();
          y = PAGE_HEIGHT - MARGIN;
        }
        y -= step;
        page.append("BT /")
            .append(line.bold() ? "F2 " : "F1 ")
            .append(line.size())
            .append(" Tf ")
            .append(MARGIN)
            .append(' ')
            .append(Math.round(y))
            .append(" Td (")
            .append(escape(piece))
            .append(") Tj ET\n");
      }
    }
    pages.add(page.toString());
    return pages;
  }

  static List<String> wrap(PdfLine line) {
    int max = (int) ((PAGE_WIDTH - 2 * MARGIN) / (line.size() * AVERAGE_GLYPH));
    List<String> pieces = new ArrayList<>();
    String rest = line.text() == null ? "" : line.text();
    while (rest.length() > max) {
      int cut = rest.lastIndexOf(' ', max);
      if (cut <= 0) {
        cut = max;
      }
      pieces.add(rest.substring(0, cut).stripTrailing());
      rest = rest.substring(cut).stripLeading();
    }
    pieces.add(rest);
    return pieces;
  }

  /** Text in Windows-1252, with PDF escapes and octal codes so the stream stays ASCII. */
  static String escape(String text) {
    StringBuilder out = new StringBuilder();
    for (byte b : encode(text)) {
      int c = b & BYTE_MASK;
      if (c == '(' || c == ')' || c == '\\') {
        out.append('\\').append((char) c);
      } else if (c > ASCII_LIMIT || c < ' ') {
        out.append('\\').append(String.format(Locale.ROOT, "%03o", c));
      } else {
        out.append((char) c);
      }
    }
    return out.toString();
  }

  private static byte[] encode(String text) {
    CharsetEncoder encoder =
        WIN_ANSI
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    try {
      ByteBuffer buffer = encoder.encode(CharBuffer.wrap(text));
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    } catch (CharacterCodingException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void write(ByteArrayOutputStream out, String text) {
    out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
  }
}
