package co.caudal.application.publication;

import co.caudal.shared.text.MessageTemplate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Spanish texts of a published schedule (docs/Reglas-de-negocio.md, section 13). They carry
 * only sectors and hours: never names, phones, levels or reasons.
 */
public final class ScheduleTexts {

  private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH);
  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h:mm a", SPANISH);
  private static final MessageTemplate TITLE =
      MessageTemplate.parse("Horario de agua de {acueducto} para el {dia}:");
  private static final MessageTemplate LINE =
      MessageTemplate.parse("- {sector}: de {inicio} a {fin}");
  private static final String CLOSING = "Cuide el agua. Si ve un daño, avísele a la Junta.";

  private ScheduleTexts() {}

  /**
   * The day as people say it, for example "lunes 12 de octubre".
   *
   * @param day the day
   * @return the text
   */
  public static String day(LocalDate day) {
    return DAY.format(day);
  }

  /**
   * An hour as people say it, for example "5:00 a. m.".
   *
   * @param line the shift
   * @param zone time zone of the aqueduct
   * @return start and end
   */
  static String[] hours(ScheduleLine line, ZoneId zone) {
    return new String[] {
      HOUR.format(line.startAt().atZone(zone)), HOUR.format(line.endAt().atZone(zone))
    };
  }

  /**
   * The message to share by WhatsApp.
   *
   * @param aqueductName the aqueduct
   * @param day the day of service
   * @param zone time zone of the aqueduct
   * @param lines the shifts in their order
   * @return the message
   */
  public static String whatsapp(
      String aqueductName, LocalDate day, ZoneId zone, List<ScheduleLine> lines) {
    StringBuilder text =
        new StringBuilder(TITLE.render(Map.of("acueducto", aqueductName, "dia", day(day))));
    for (ScheduleLine line : lines) {
      String[] hours = hours(line, zone);
      text.append('\n')
          .append(
              LINE.render(
                  Map.of("sector", line.sectorName(), "inicio", hours[0], "fin", hours[1])));
    }
    return text.append("\n\n").append(CLOSING).toString();
  }
}
