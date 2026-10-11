package co.caudal.application.schedule;

import co.caudal.shared.text.MessageTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns the structured reasons of a shift into the Spanish text people read
 * (docs/Reglas-de-negocio.md, section 6.1): hours without decimals when whole and with a decimal
 * comma otherwise, levels always with two decimals, "hora" or "horas", and the day as "hoy",
 * "mañana" or the date.
 */
public final class ReasonTexts {

  private static final Map<String, MessageTemplate> TEMPLATES =
      Map.of(
          "PRIORITY_SECTOR",
          MessageTemplate.parse("{sector} es prioritario y recibe turno antes que los demás."),
          "HOURS_WITHOUT_SERVICE",
          MessageTemplate.parse(
              "{sector} lleva {horas} {unidad_horas} sin servicio{origen_texto}."),
          "TANK_BAND",
          MessageTemplate.parse(
              "Nivel del tanque {nivel} (banda {banda}): hay {horas} {unidad_horas} de servicio"
                  + " hoy."),
          "FORECAST_RANGE",
          MessageTemplate.parse("Para {dia} se espera un nivel de {p50} (entre {p10} y {p90})."),
          "RESERVE_GUARD_OK",
          MessageTemplate.parse(
              "El nivel posible más bajo ({p10}) está por encima de la reserva ({reserve_level});"
                  + " no se recortan horas."),
          "RESERVE_GUARD_CUT",
          MessageTemplate.parse(
              "El nivel posible más bajo ({p10}) queda por debajo de la reserva ({reserve_level});"
                  + " se recortan las horas para cuidar el agua."),
          "BOARD_CHANGE",
          MessageTemplate.parse("La Junta cambió este turno: {motivo}"));

  private static final String HOURS = "horas";
  private static final int LEVEL_SCALE = 2;

  private ReasonTexts() {}

  /**
   * The text of a reason.
   *
   * @param code the reason code
   * @param params its values
   * @param today the day the text is read
   * @return the text in Spanish
   */
  public static String text(String code, Map<String, Object> params, LocalDate today) {
    Map<String, String> values = new HashMap<>();
    params.forEach((key, value) -> values.put(key, String.valueOf(value)));
    for (String level : new String[] {"nivel", "p10", "p50", "p90", "reserve_level"}) {
      if (params.containsKey(level)) {
        values.put(level, level(params.get(level)));
      }
    }
    if (params.containsKey(HOURS)) {
      BigDecimal hours = new BigDecimal(String.valueOf(params.get(HOURS)));
      values.put(HOURS, hours(hours));
      values.put("unidad_horas", hours.compareTo(BigDecimal.ONE) == 0 ? "hora" : HOURS);
    }
    values.put(
        "origen_texto",
        "ESTIMATED".equals(params.get("origen"))
            ? " (dato estimado, según el último turno publicado)"
            : "");
    if (params.containsKey("dia")) {
      values.put("dia", day(LocalDate.parse(String.valueOf(params.get("dia"))), today));
    }
    String key = code;
    if ("RESERVE_GUARD".equals(code)) {
      key =
          Boolean.parseBoolean(String.valueOf(params.get("recortado")))
              ? "RESERVE_GUARD_CUT"
              : "RESERVE_GUARD_OK";
    }
    MessageTemplate template = TEMPLATES.get(key);
    return template == null ? code : template.render(values);
  }

  private static String level(Object value) {
    return new BigDecimal(String.valueOf(value))
        .setScale(LEVEL_SCALE, RoundingMode.HALF_UP)
        .toPlainString()
        .replace('.', ',');
  }

  private static String hours(BigDecimal value) {
    BigDecimal clean = value.stripTrailingZeros();
    return (clean.scale() <= 0 ? clean.setScale(0, RoundingMode.UNNECESSARY) : clean)
        .toPlainString()
        .replace('.', ',');
  }

  private static String day(LocalDate day, LocalDate today) {
    if (day.equals(today)) {
      return "hoy";
    }
    if (day.equals(today.plusDays(1))) {
      return "mañana";
    }
    return day.toString();
  }
}
