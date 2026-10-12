package co.caudal.application.publication;

/**
 * Where a notice is shown: the implementor side of the bridge. A new channel never needs a new
 * notice.
 *
 * @pattern P07 Bridge
 */
public interface PublicationChannel {

  /**
   * Gives the message the form of the channel.
   *
   * @param message what the notice says
   * @return the text as the channel shows it
   */
  String deliver(NoticeMessage message);
}
