package co.caudal.application.publication;

import co.caudal.shared.text.MessageTemplate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** The published schedule of a day: only sectors and hours. */
public final class ScheduleNotice extends Notice {

  private static final MessageTemplate TITLE =
      MessageTemplate.parse("Horario de agua de {acueducto} para el {dia}:");
  private static final MessageTemplate LINE =
      MessageTemplate.parse("{sector}: de {inicio} a {fin}");
  private static final String CLOSING = "Cuide el agua. Si ve un daño, avísele a la Junta.";

  private final String aqueductName;
  private final LocalDate day;
  private final ZoneId zone;
  private final List<ScheduleLine> lines;

  /**
   * Creates the notice.
   *
   * @param channel where it is shown
   * @param aqueductName the aqueduct
   * @param day the day of service
   * @param zone time zone of the aqueduct
   * @param lines the shifts in their order
   */
  public ScheduleNotice(
      PublicationChannel channel,
      String aqueductName,
      LocalDate day,
      ZoneId zone,
      List<ScheduleLine> lines) {
    super(channel);
    this.aqueductName = aqueductName;
    this.day = day;
    this.zone = zone;
    this.lines = List.copyOf(lines);
  }

  @Override
  protected NoticeMessage toMessage() {
    return new NoticeMessage(
        TITLE.render(Map.of("acueducto", aqueductName, "dia", ScheduleTexts.day(day))),
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
