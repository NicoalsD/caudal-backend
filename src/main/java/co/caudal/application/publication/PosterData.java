package co.caudal.application.publication;

import java.util.List;

/**
 * What the poster of a schedule says.
 *
 * @param banner a notice above the title, or null
 * @param title the title
 * @param subtitle the aqueduct and the day
 * @param lines one line per shift
 * @param closing the last line
 */
public record PosterData(
    String banner, String title, String subtitle, List<String> lines, String closing) {

  /** Copies the lines. */
  public PosterData {
    lines = List.copyOf(lines);
  }
}
