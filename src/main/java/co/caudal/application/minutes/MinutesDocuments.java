package co.caudal.application.minutes;

import co.caudal.application.publication.DemoMark;
import co.caudal.domain.minutes.MinutesSection;
import co.caudal.shared.text.MessageTemplate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the PDF of the minutes says. It uses only what is stored, so the final PDF rendered again
 * later gives the same bytes and the same hash. A draft carries the mark "BORRADOR"; a demo
 * aqueduct carries "Datos simulados". No person is named: the signature lines carry only the roles.
 */
public final class MinutesDocuments {

  /** The mark of a draft. */
  public static final String DRAFT_MARK = "BORRADOR";

  private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH);
  private static final DateTimeFormatter MOMENT =
      DateTimeFormatter.ofPattern("dd/MM HH:mm", SPANISH);
  private static final MessageTemplate TITLE = MessageTemplate.parse("Acta de {acueducto}");
  private static final MessageTemplate PERIOD =
      MessageTemplate.parse("Periodo del {desde} al {hasta}");
  private static final MessageTemplate CHAIN =
      MessageTemplate.parse("Cabeza de la cadena de auditoría: {hash}");
  private static final String VERIFICATION = "Verificación";
  private static final List<String> SIGNATORIES =
      List.of("Presidencia de la Junta", "Secretaría de la Junta");

  private MinutesDocuments() {}

  /**
   * The document of some minutes.
   *
   * @param minutes the minutes
   * @param aqueductName the aqueduct
   * @param demo true if its data are simulated
   * @param zone time zone of the aqueduct
   * @param draft true to carry the draft mark
   * @param auditHeadHash head of the audit chain to print, or null
   * @return what the PDF says
   */
  @SuppressWarnings("checkstyle:ParameterNumber")
  public static MinutesDocument of(
      MinutesView minutes,
      String aqueductName,
      boolean demo,
      ZoneId zone,
      boolean draft,
      String auditHeadHash) {
    List<String> marks = new ArrayList<>();
    if (draft) {
      marks.add(DRAFT_MARK);
    }
    if (demo) {
      marks.add(DemoMark.BANNER);
    }
    List<MinutesDocument.DocumentSection> sections = new ArrayList<>();
    for (MinutesSection section : MinutesSection.values()) {
      sections.add(
          new MinutesDocument.DocumentSection(
              section.title(),
              minutes.content().getOrDefault(section.key(), List.of()).stream()
                  .map(e -> MOMENT.format(e.at().atZone(zone)) + "  " + e.summary())
                  .toList()));
    }
    if (auditHeadHash != null) {
      sections.add(
          new MinutesDocument.DocumentSection(
              VERIFICATION, List.of(CHAIN.render(Map.of("hash", auditHeadHash)))));
    }
    return new MinutesDocument(
        marks.isEmpty() ? null : String.join(" · ", marks),
        TITLE.render(Map.of("acueducto", aqueductName)),
        PERIOD.render(
            Map.of(
                "desde",
                DAY.format(minutes.periodFrom()),
                "hasta",
                DAY.format(minutes.periodTo()))),
        sections,
        SIGNATORIES);
  }
}
