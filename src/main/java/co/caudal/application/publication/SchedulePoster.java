package co.caudal.application.publication;

import co.caudal.shared.text.MessageTemplate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** What the printed poster of a schedule says: the same sectors and hours as the message. */
public final class SchedulePoster {

  private static final String TITLE = "Horario de agua";
  private static final MessageTemplate SUBTITLE = MessageTemplate.parse("{acueducto}, {dia}");
  private static final MessageTemplate LINE =
      MessageTemplate.parse("{sector}: de {inicio} a {fin}");
  private static final String CLOSING = "Cuide el agua. Si ve un daño, avísele a la Junta.";

  private SchedulePoster() {}

  /**
   * The content of the poster.
   *
   * @param aqueductName the aqueduct
   * @param demo true if its data are simulated
   * @param day the day of service
   * @param zone time zone of the aqueduct
   * @param lines the shifts in their order
   * @return what the poster says
   */
  public static PosterData of(
      String aqueductName, boolean demo, LocalDate day, ZoneId zone, List<ScheduleLine> lines) {
    return new PosterData(
        DemoMark.note(demo),
        TITLE,
        SUBTITLE.render(Map.of("acueducto", aqueductName, "dia", ScheduleTexts.day(day))),
        lines.stream()
            .map(
                line -> {
                  String[] hours = ScheduleTexts.hours(line, zone);
                  return LINE.render(
                      Map.of("sector", line.sectorName(), "inicio", hours[0], "fin", hours[1]));
                })
            .toList(),
        CLOSING);
  }
}
