package co.caudal.application.publication;

import java.util.List;

/**
 * What a notice says, before a channel gives it its form.
 *
 * @param banner a notice above everything, such as the demo mark, or null
 * @param title the first line
 * @param lines the body, one item per line
 * @param closing the last line, or null
 */
public record NoticeMessage(String banner, String title, List<String> lines, String closing) {

  /** Copies the lines. */
  public NoticeMessage {
    lines = List.copyOf(lines);
  }
}
