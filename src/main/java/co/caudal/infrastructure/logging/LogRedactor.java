package co.caudal.infrastructure.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes credentials from text before it reaches a log line.
 *
 * <p>Covers the values that the security policy forbids in logs: {@code Authorization}, {@code
 * Cookie}, bearer tokens and any {@code password}, {@code token} or {@code secret} pair, either as
 * {@code key=value}, {@code key: value} or JSON.
 */
public final class LogRedactor {

  /** Text written in place of a sensitive value. */
  public static final String MASK = "[REDACTED]";

  private static final List<Pattern> HEADER_PATTERNS =
      List.of(
          Pattern.compile(
              "(?i)\\b(authorization|proxy-authorization)(\"?\\s*[:=]\\s*\"?)[^\"\\r\\n,}]+"),
          Pattern.compile("(?i)\\b(set-cookie|cookie)(\"?\\s*[:=]\\s*\"?)[^\"\\r\\n}]+"));

  private static final Pattern KEY_VALUE =
      Pattern.compile(
          "(?i)(?<key>\"?[a-z_\\-]*(?:password|passwd|token|secret|api[_-]?key)[a-z_\\-]*\"?)"
              + "(?<sep>\\s*[:=]\\s*)(?<value>\"[^\"]*\"|[^\\s,;&}\\]]+)");

  private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+");

  private LogRedactor() {}

  /**
   * Returns the text with every sensitive value masked.
   *
   * @param text text that may contain credentials, possibly {@code null}
   * @return the redacted text, or {@code null} when the input is {@code null}
   */
  public static String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    for (Pattern pattern : HEADER_PATTERNS) {
      result = pattern.matcher(result).replaceAll("$1$2" + Matcher.quoteReplacement(MASK));
    }
    result = BEARER.matcher(result).replaceAll(Matcher.quoteReplacement("Bearer " + MASK));
    Matcher matcher = KEY_VALUE.matcher(result);
    StringBuilder buffer = new StringBuilder();
    while (matcher.find()) {
      String value = matcher.group("value");
      String masked = value.startsWith("\"") ? "\"" + MASK + "\"" : MASK;
      matcher.appendReplacement(
          buffer, Matcher.quoteReplacement(matcher.group("key") + matcher.group("sep") + masked));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }
}
