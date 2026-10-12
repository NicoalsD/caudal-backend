package co.caudal.application.minutes;

import co.caudal.domain.minutes.ConfirmedRecord;
import co.caudal.domain.minutes.EpistemicRecord;
import co.caudal.domain.minutes.EstimatedRecord;
import co.caudal.domain.minutes.InferredRecord;
import co.caudal.domain.minutes.Minutes;
import co.caudal.domain.minutes.MinutesBuilder;
import co.caudal.domain.minutes.MinutesEntry;
import co.caudal.domain.minutes.ObservedRecord;
import co.caudal.shared.text.MessageTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the records of a period into the minutes (docs/Reglas-de-negocio.md, section 16.1): it
 * decides the epistemic kind of each record, writes its sentence in Spanish and lets the {@link
 * MinutesBuilder} place it. A record with two readings goes to the more careful section.
 */
public final class MinutesAssembler {

  private static final Set<String> FIELD_CONFIRMED = Set.of("CONFIRMED", "RESOLVED");
  private static final Map<String, MessageTemplate> SENTENCES =
      Map.of(
          "reading", MessageTemplate.parse("Lectura del tanque: {valor} ({estado})."),
          "correction",
              MessageTemplate.parse("Corrección de una lectura a {valor}. Motivo: {motivo}"),
          "incident", MessageTemplate.parse("Reporte de daño ({categoria}), estado {estado}."),
          "forecast", MessageTemplate.parse("Pronóstico con {modelo} a {dias} días{respaldo}."),
          "proposal",
              MessageTemplate.parse(
                  "Propuesta de turnos del {dia}: {horas} horas disponibles, estado {estado}."),
          "anomaly", MessageTemplate.parse("Anomalía {tipo}, estado {estado}."),
          "execution", MessageTemplate.parse("Turno de {sector} del {dia}: {estado}."));

  private MinutesAssembler() {}

  /**
   * Builds the minutes.
   *
   * @param from first day
   * @param to last day
   * @param facts the records of the period
   * @return the minutes
   */
  public static Minutes assemble(LocalDate from, LocalDate to, List<MinutesFact> facts) {
    MinutesBuilder builder = new MinutesBuilder().period(from, to);
    facts.forEach(fact -> builder.add(classify(fact)));
    return builder.build();
  }

  /**
   * The epistemic kind of a record.
   *
   * @param fact the record
   * @return the record with its kind
   */
  static EpistemicRecord classify(MinutesFact fact) {
    MinutesEntry entry = new MinutesEntry(fact.source(), fact.id(), fact.at(), sentence(fact));
    String status = fact.status();
    return switch (fact.source()) {
      case "reading" ->
          "FLAGGED".equals(status) ? new InferredRecord(entry) : new ObservedRecord(entry);
      case "correction" -> new ObservedRecord(entry);
      case "incident" -> incident(status, entry);
      case "forecast", "proposal" -> new EstimatedRecord(entry);
      case "anomaly" -> anomaly(status, fact.values().get("incidente"), entry);
      case "execution" -> new ConfirmedRecord(entry);
      default -> throw new IllegalArgumentException("Unknown minutes source");
    };
  }

  /** Confirmed only when its incident was also confirmed in the field; otherwise a suspicion. */
  private static EpistemicRecord anomaly(String status, String incident, MinutesEntry entry) {
    boolean checked = incident != null && FIELD_CONFIRMED.contains(incident);
    return "CONFIRMED".equals(status) && checked
        ? new ConfirmedRecord(entry)
        : new InferredRecord(entry);
  }

  private static EpistemicRecord incident(String status, MinutesEntry entry) {
    if (FIELD_CONFIRMED.contains(status)) {
      return new ConfirmedRecord(entry);
    }
    return "DISMISSED".equals(status) ? new InferredRecord(entry) : new ObservedRecord(entry);
  }

  private static String sentence(MinutesFact fact) {
    Map<String, String> values = new java.util.HashMap<>(fact.values());
    values.putIfAbsent("estado", fact.status() == null ? "" : fact.status());
    return SENTENCES.get(fact.source()).render(values);
  }
}
