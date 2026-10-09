package co.caudal.application.auth;

/**
 * What the server knows about the caller of a request, already reduced to what may be stored: the
 * HMAC of the IP (never the IP) and a trimmed user agent.
 *
 * @param ipHmac keyed hash of the IP address
 * @param userAgent user agent, at most {@value #USER_AGENT_MAX} characters
 */
public record ClientContext(String ipHmac, String userAgent) {

  /** Longest stored user agent ({@code varchar(200)}). */
  public static final int USER_AGENT_MAX = 200;

  /** Cuts the user agent to the column size. */
  public ClientContext {
    if (userAgent != null && userAgent.length() > USER_AGENT_MAX) {
      userAgent = userAgent.substring(0, USER_AGENT_MAX);
    }
  }
}
