package co.caudal.application.publication;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The Spanish texts of a published schedule (docs/Reglas-de-negocio.md, section 13). They carry
 * only sectors and hours: never names, phones, levels or reasons.
 */
public final class ScheduleTexts {

  private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH);
  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h:mm a", SPANISH);

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
}
