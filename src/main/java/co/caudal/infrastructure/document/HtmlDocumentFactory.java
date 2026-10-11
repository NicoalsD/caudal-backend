package co.caudal.infrastructure.document;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The HTML family: every part is a fragment of escaped HTML, and the assembler wraps them in a
 * page. Content is always escaped; nothing is written raw.
 *
 * @pattern P03 Abstract Factory
 */
public final class HtmlDocumentFactory implements DocumentFactory {

  static final String FORMAT = "HTML";

  /** A part of the HTML family. */
  record HtmlPart(String html) implements DocumentHeader, DocumentSection, DocumentSignatures {

    @Override
    public String format() {
      return FORMAT;
    }
  }

  /**
   * Escapes text for HTML.
   *
   * @param text the text
   * @return the escaped text
   */
  static String escape(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (char c : text.toCharArray()) {
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  @Override
  public DocumentHeader createHeader(DocumentMetadata metadata) {
    StringBuilder html = new StringBuilder("<header>");
    if (metadata.banner() != null) {
      html.append("<p class=\"banner\">").append(escape(metadata.banner())).append("</p>");
    }
    html.append("<h1>").append(escape(metadata.title())).append("</h1>");
    if (metadata.subtitle() != null) {
      html.append("<p>").append(escape(metadata.subtitle())).append("</p>");
    }
    return new HtmlPart(html.append("</header>").toString());
  }

  @Override
  public DocumentSection createSection(SectionContent content) {
    StringBuilder html = new StringBuilder("<section>");
    if (content.heading() != null) {
      html.append("<h2>").append(escape(content.heading())).append("</h2>");
    }
    html.append("<ul>");
    content.lines().forEach(line -> html.append("<li>").append(escape(line)).append("</li>"));
    return new HtmlPart(html.append("</ul></section>").toString());
  }

  @Override
  public DocumentSignatures createSignatures(List<String> signatories) {
    StringBuilder html = new StringBuilder("<footer>");
    signatories.forEach(
        s -> html.append("<p class=\"signature\">").append(escape(s)).append("</p>"));
    return new HtmlPart(html.append("</footer>").toString());
  }

  @Override
  public DocumentAssembler createAssembler() {
    return new DocumentAssembler() {
      private final StringBuilder body = new StringBuilder();

      @Override
      public void add(DocumentPart part) {
        if (!(part instanceof HtmlPart html)) {
          throw new IllegalArgumentException("Part of another document family");
        }
        body.append(html.html());
      }

      @Override
      public byte[] finish() {
        return ("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"></head><body>"
                + body
                + "</body></html>")
            .getBytes(StandardCharsets.UTF_8);
      }
    };
  }

  @Override
  public String format() {
    return FORMAT;
  }
}
