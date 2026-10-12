package co.caudal.application.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hides the values of the keys that may hold secrets or personal data before a state of the audit
 * log leaves the API (docs/Seguridad-de-la-base-de-datos.md, section 11). The writers already
 * redact; this is the second line, so a new writer that forgets does not leak a password or a name
 * through the Board's view.
 */
public final class AuditStateRedactor {

  /** Text that replaces a redacted value. */
  public static final String REDACTED = "[redactado]";

  private static final Pattern SENSITIVE_KEY =
      Pattern.compile(
          "(?i)password|secret|token|hash|cipher|cookie|api_?key|private_?key|full_?name|email"
              + "|phone|(^|_)ip(_|$)");

  private AuditStateRedactor() {}

  /**
   * Returns a copy of a state with the sensitive values replaced.
   *
   * @param state the state, may be null
   * @return the redacted copy, or null if the state was null
   */
  public static Map<String, Object> redact(Map<String, Object> state) {
    if (state == null) {
      return null;
    }
    Map<String, Object> copy = new LinkedHashMap<>();
    state.forEach(
        (key, value) ->
            copy.put(key, SENSITIVE_KEY.matcher(key).find() ? REDACTED : redactValue(value)));
    return copy;
  }

  private static Object redactValue(Object value) {
    if (value instanceof Map<?, ?> nested) {
      Map<String, Object> typed = new LinkedHashMap<>();
      nested.forEach((k, v) -> typed.put(String.valueOf(k), v));
      return redact(typed);
    }
    if (value instanceof List<?> items) {
      List<Object> copy = new ArrayList<>(items.size());
      items.forEach(item -> copy.add(redactValue(item)));
      return copy;
    }
    return value;
  }
}
