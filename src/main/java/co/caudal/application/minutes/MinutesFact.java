package co.caudal.application.minutes;

import java.time.Instant;
import java.util.Map;

/**
 * A record of the period as the database gives it, before it is classified.
 *
 * @param source reading, correction, incident, forecast, proposal, anomaly or execution
 * @param id the id of the record
 * @param at when it happened
 * @param status its status, or null
 * @param values the values its sentence needs
 */
public record MinutesFact(
    String source, String id, Instant at, String status, Map<String, String> values) {

  /** Freezes the values. */
  public MinutesFact {
    values = Map.copyOf(values);
  }
}
