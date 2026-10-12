package co.caudal.api.security;

import co.caudal.application.auth.Caller;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Builds the {@link Caller} of a command from the validated access token and the connection. The
 * account and the aqueduct always come from the signed token, never from the request body.
 */
public final class CallerFactory {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_AQUEDUCT_ID = "aqueduct_id";

  private CallerFactory() {}

  /**
   * Reads the caller.
   *
   * @param jwt the validated access token
   * @param request the current request, for the address of the connection
   * @return the caller
   * @throws DomainException with {@code UNAUTHORIZED} if the token lacks a valid account or
   *     aqueduct
   */
  public static Caller from(Jwt jwt, HttpServletRequest request) {
    return new Caller(
        parse(jwt.getSubject()),
        parse(jwt.getClaimAsString(CLAIM_AQUEDUCT_ID)),
        jwt.getClaimAsString(CLAIM_ROLE),
        request.getRemoteAddr());
  }

  private static UUID parse(String value) {
    try {
      if (value == null) {
        throw new DomainException(ErrorCode.UNAUTHORIZED);
      }
      return UUID.fromString(value);
    } catch (IllegalArgumentException notAUuid) {
      throw new DomainException(ErrorCode.UNAUTHORIZED);
    }
  }
}
