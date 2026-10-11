package co.caudal.application.publication;

import java.util.Objects;

/**
 * What is announced, independent from where it is shown: the abstraction side of the bridge. A new
 * notice is a subclass; a new channel is a {@link PublicationChannel}; no combination needs its own
 * class. A notice never carries names or phones.
 *
 * @pattern P07 Bridge
 */
public abstract class Notice {

  private final PublicationChannel channel;

  /**
   * Creates the notice.
   *
   * @param channel where it is shown
   */
  protected Notice(PublicationChannel channel) {
    this.channel = Objects.requireNonNull(channel, "channel");
  }

  /**
   * Shows the notice in its channel.
   *
   * @return the text as the channel shows it
   */
  public final String publish() {
    return channel.deliver(toMessage());
  }

  /**
   * Builds what the notice says.
   *
   * @return the message
   */
  protected abstract NoticeMessage toMessage();
}
