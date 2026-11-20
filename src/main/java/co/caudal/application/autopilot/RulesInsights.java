package co.caudal.application.autopilot;

import co.caudal.shared.FieldLimits;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The deterministic assistant that answers when the language model cannot: it follows the schedule
 * (opens the valves of the sectors in shift, closes the others), closes everything at the reserve
 * floor, and asks for an inspection when there is no level or a serious anomaly. The text is a
 * template in Spanish. Its source is {@code RULES} and its model {@link #MODEL}.
 */
public final class RulesInsights {

  /** Name of the rules in the {@code model} field. */
  public static final String MODEL = "rules-v1";

  private static final BigDecimal CONFIDENCE_RULE = new BigDecimal("0.70");
  private static final BigDecimal CONFIDENCE_SAFETY = new BigDecimal("0.90");
  private static final BigDecimal CONFIDENCE_WAIT = new BigDecimal("0.50");
  private static final String OPEN = "OPEN";
  private static final String CLOSED = "CLOSED";
  private static final String PLANNED = "PLANNED";
  private static final String HIGH = "HIGH";
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private RulesInsights() {}

  /**
   * Recommends what to do with the valves of the snapshot.
   *
   * @param snapshot the state of the aqueduct
   * @param now the moment of the answer
   * @return the recommendations, at most five
   */
  public static InsightsRecommendations recommend(InsightsSnapshot snapshot, Instant now) {
    List<InsightRecommendation> result = new ArrayList<>();
    BigDecimal level = snapshot.tank().levelPct();
    if (level == null) {
      result.add(
          new InsightRecommendation(
              "INSPECT",
              null,
              "No hay una lectura reciente del tanque; revisar el sensor antes de mover llaves.",
              CONFIDENCE_SAFETY));
      return answer(result, "Sin lectura del tanque.", now);
    }
    boolean atFloor = level.compareTo(snapshot.reserveFloorPct()) <= 0;
    if (atFloor) {
      closeOpenValves(snapshot, result);
    } else {
      followSchedule(snapshot, now, result);
    }
    anomalies(snapshot, result);
    if (result.isEmpty()) {
      result.add(
          new InsightRecommendation(
              "WAIT",
              null,
              "Las llaves ya siguen el horario y el nivel está sobre la reserva.",
              CONFIDENCE_WAIT));
    }
    String summary =
        atFloor
            ? "El tanque está en o bajo la reserva: se cierra para protegerla."
            : "Se sigue el horario publicado con el nivel sobre la reserva.";
    return answer(result, summary, now);
  }

  private static void closeOpenValves(InsightsSnapshot snapshot, List<InsightRecommendation> out) {
    for (InsightsSnapshot.Valve valve : snapshot.valves()) {
      if (valve.motorized() && OPEN.equals(valve.state())) {
        out.add(
            new InsightRecommendation(
                "CLOSE",
                valve.id(),
                "El nivel del tanque está en o bajo la reserva; cerrar la llave del sector "
                    + valve.sectorName()
                    + ".",
                CONFIDENCE_SAFETY));
      }
    }
    if (out.isEmpty()) {
      out.add(
          new InsightRecommendation(
              "WAIT",
              null,
              "El nivel está en o bajo la reserva y no hay llaves abiertas; esperar a que suba.",
              CONFIDENCE_SAFETY));
    }
  }

  private static void followSchedule(
      InsightsSnapshot snapshot, Instant now, List<InsightRecommendation> out) {
    Set<UUID> inShift =
        snapshot.schedule().stream()
            .filter(s -> PLANNED.equals(s.status()))
            .filter(s -> !s.startsAt().isAfter(now) && s.endsAt().isAfter(now))
            .map(InsightsSnapshot.Shift::sectorId)
            .collect(Collectors.toSet());
    for (InsightsSnapshot.Valve valve : snapshot.valves()) {
      if (!valve.motorized()) {
        continue;
      }
      boolean due = inShift.contains(valve.sectorId());
      if (due && CLOSED.equals(valve.state())) {
        out.add(
            new InsightRecommendation(
                "OPEN",
                valve.id(),
                "El sector " + valve.sectorName() + " tiene turno ahora y su llave está cerrada.",
                CONFIDENCE_RULE));
      } else if (!due && OPEN.equals(valve.state())) {
        out.add(
            new InsightRecommendation(
                "CLOSE",
                valve.id(),
                "El sector "
                    + valve.sectorName()
                    + " no tiene turno ahora y su llave está abierta.",
                CONFIDENCE_RULE));
      }
    }
  }

  private static void anomalies(InsightsSnapshot snapshot, List<InsightRecommendation> out) {
    boolean serious = snapshot.anomalies().stream().anyMatch(a -> HIGH.equals(a.severity()));
    if (serious) {
      out.add(
          new InsightRecommendation(
              "INSPECT",
              null,
              "Hay una anomalía grave abierta; enviar a alguien a revisar la red.",
              CONFIDENCE_RULE));
    }
  }

  private static InsightsRecommendations answer(
      List<InsightRecommendation> list, String summary, Instant now) {
    List<InsightRecommendation> cut =
        list.size() <= FieldLimits.INSIGHTS_RECOMMENDATIONS_MAX
            ? list
            : list.subList(0, FieldLimits.INSIGHTS_RECOMMENDATIONS_MAX);
    return new InsightsRecommendations(
        InsightsRecommendations.SOURCE_RULES, MODEL, now, summary, cut);
  }

  /**
   * Answers a question with a template about the state of the aqueduct. The question is not
   * interpreted: the answer is the same summary of the state.
   *
   * @param snapshot the state of the aqueduct
   * @param now the moment of the answer
   * @return the answer
   */
  public static InsightsAnswer ask(InsightsSnapshot snapshot, Instant now) {
    InsightsSnapshot.Tank tank = snapshot.tank();
    StringBuilder text =
        new StringBuilder("Respuesta automática por reglas (sin modelo de lenguaje). ");
    if (tank.levelPct() == null) {
      text.append("No hay una lectura reciente del tanque.");
    } else {
      text.append("El tanque está al ").append(tank.levelPct().toPlainString()).append(" %");
      if (tank.trend() != null) {
        text.append(" y su tendencia es ").append(trendEs(tank.trend()));
      }
      text.append(". La reserva es ")
          .append(snapshot.reserveFloorPct().toPlainString())
          .append(" %.");
    }
    ZoneId zone = snapshot.zone();
    snapshot.forecast().stream()
        .findFirst()
        .ifPresent(
            first ->
                text.append(" Pronóstico para el ")
                    .append(DAY.format(first.timestamp().atZone(zone)))
                    .append(": entre ")
                    .append(first.p10().toPlainString())
                    .append(" y ")
                    .append(first.p90().toPlainString())
                    .append(" m."));
    return new InsightsAnswer(InsightsRecommendations.SOURCE_RULES, MODEL, now, text.toString());
  }

  private static String trendEs(String trend) {
    return switch (trend) {
      case "RISING" -> "a subir";
      case "FALLING" -> "a bajar";
      default -> "estable";
    };
  }

  /**
   * Writes the report of a period from its aggregates with a template.
   *
   * @param request the aggregates of the period
   * @param now the moment of the answer
   * @return the report
   */
  public static InsightsReport report(ReportRequest request, Instant now) {
    List<String> findings = new ArrayList<>();
    request
        .aggregates()
        .forEach(
            (group, value) -> {
              if (value instanceof Map<?, ?> numbers) {
                findings.add(
                    group.replace('_', ' ')
                        + ": "
                        + numbers.entrySet().stream()
                            .map(e -> e.getKey() + " = " + e.getValue())
                            .collect(Collectors.joining(", ")));
              }
            });
    List<String> advice =
        List.of("Revisar con la Junta los hallazgos del periodo antes de la próxima reunión.");
    String title =
        "Reporte de "
            + request.aqueductName()
            + " del "
            + day(request.from())
            + " al "
            + day(request.to());
    String summary = "Resumen automático por reglas con " + findings.size() + " grupos de datos.";
    StringBuilder markdown = new StringBuilder("# ").append(title).append("\n\n");
    markdown.append(summary).append("\n\n## Hallazgos\n\n");
    findings.forEach(f -> markdown.append("- ").append(f).append('\n'));
    markdown.append("\n## Recomendaciones\n\n");
    advice.forEach(a -> markdown.append("- ").append(a).append('\n'));
    return new InsightsReport(
        InsightsRecommendations.SOURCE_RULES,
        MODEL,
        now,
        title,
        summary,
        findings,
        advice,
        markdown.toString());
  }

  private static String day(Instant instant) {
    return DAY.format(instant.atZone(ZoneId.of("UTC")));
  }
}
