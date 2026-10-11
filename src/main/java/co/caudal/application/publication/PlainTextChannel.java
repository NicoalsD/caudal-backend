package co.caudal.application.publication;

/** Plain text, for screens and printed paper: one line each, without any marks. */
public final class PlainTextChannel implements PublicationChannel {

  @Override
  public String deliver(NoticeMessage message) {
    StringBuilder text = new StringBuilder();
    if (message.banner() != null) {
      text.append(message.banner()).append('\n');
    }
    text.append(message.title());
    message.lines().forEach(line -> text.append('\n').append(line));
    if (message.closing() != null) {
      text.append('\n').append(message.closing());
    }
    return text.toString();
  }
}
