package co.caudal.application.publication;

import co.caudal.shared.text.MessageTemplate;
import java.util.List;
import java.util.Map;

/** The answer to a person who reported damage: the code to follow the report. */
public final class ReportReceivedNotice extends Notice {

  private static final String TITLE = "Recibimos su reporte. Gracias por avisar.";
  private static final MessageTemplate CODE =
      MessageTemplate.parse("Código de seguimiento: {codigo}");
  private static final String CLOSING = "Guarde el código: solo se muestra esta vez.";

  private final String trackingCode;

  /**
   * Creates the notice.
   *
   * @param channel where it is shown
   * @param trackingCode the code of the report
   */
  public ReportReceivedNotice(PublicationChannel channel, String trackingCode) {
    super(channel);
    this.trackingCode = trackingCode;
  }

  @Override
  protected NoticeMessage toMessage() {
    return new NoticeMessage(
        null, TITLE, List.of(CODE.render(Map.of("codigo", trackingCode))), CLOSING);
  }
}
