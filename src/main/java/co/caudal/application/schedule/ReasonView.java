package co.caudal.application.schedule;

import java.util.Map;

/**
 * A reason of a shift with its text.
 *
 * @param code the reason code
 * @param params its values
 * @param text the Spanish text
 */
public record ReasonView(String code, Map<String, Object> params, String text) {

  /** Freezes the values. */
  public ReasonView {
    params = Map.copyOf(params);
  }
}
