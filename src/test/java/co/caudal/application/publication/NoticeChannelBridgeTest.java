package co.caudal.application.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeChannelBridgeTest {

  static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  static final LocalDate DAY = LocalDate.of(2026, 10, 12);
  static final List<ScheduleLine> LINES =
      List.of(
          new ScheduleLine(
              "Sector Alto",
              Instant.parse("2026-10-12T10:00:00Z"),
              Instant.parse("2026-10-12T14:00:00Z")));

  @Test
  void theScheduleInWhatsAppHasABoldTitleAndDashes() {
    String text =
        new ScheduleNotice(new WhatsAppChannel(), "Acueducto Demo", DAY, BOGOTA, LINES).publish();

    assertThat(text)
        .startsWith("*Horario de agua de Acueducto Demo para el lunes 12 de octubre:*")
        .contains("\n- Sector Alto: de 5:00")
        .contains(" a 9:00")
        .endsWith("\n\nCuide el agua. Si ve un daño, avísele a la Junta.");
  }

  @Test
  void theSameScheduleInPlainTextHasNoMarks() {
    String text =
        new ScheduleNotice(new PlainTextChannel(), "Acueducto Demo", DAY, BOGOTA, LINES).publish();

    assertThat(text)
        .startsWith("Horario de agua de Acueducto Demo para el lunes 12 de octubre:\nSector Alto")
        .doesNotContain("*")
        .doesNotContain("- ");
  }

  @Test
  void aReceivedReportGoesThroughBothChannels() {
    assertThat(new ReportReceivedNotice(new WhatsAppChannel(), "AB12CD34").publish())
        .startsWith("*Recibimos su reporte.")
        .contains("- Código de seguimiento: AB12CD34");
    assertThat(new ReportReceivedNotice(new PlainTextChannel(), "AB12CD34").publish())
        .contains("\nCódigo de seguimiento: AB12CD34\nGuarde el código");
  }
}
