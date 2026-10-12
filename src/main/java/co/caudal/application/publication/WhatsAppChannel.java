package co.caudal.application.publication;

/**
 * WhatsApp: the banner in italics, the title in bold, one shift per line with a dash and a blank
 * line before the end.
 */
public final class WhatsAppChannel implements PublicationChannel {

  @Override
  public String deliver(NoticeMessage message) {
    StringBuilder text = new StringBuilder();
    if (message.banner() != null) {
      text.append('_').append(message.banner()).append("_\n\n");
    }
    text.append('*').append(message.title()).append('*');
    for (String line : message.lines()) {
      text.append("\n- ").append(line);
    }
    if (message.closing() != null) {
      text.append("\n\n").append(message.closing());
    }
    return text.toString();
  }
}
