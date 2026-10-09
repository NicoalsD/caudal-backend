package co.caudal.application.port.out;

import co.caudal.application.auth.AccessTokenSubject;
import co.caudal.application.auth.IssuedAccessToken;

/** Issues the short lived access token that proves a session. */
public interface AccessTokenIssuerPort {

  /**
   * Signs an access token for the session.
   *
   * @param subject who the token is for
   * @return the token and its lifetime
   */
  IssuedAccessToken issue(AccessTokenSubject subject);
}
