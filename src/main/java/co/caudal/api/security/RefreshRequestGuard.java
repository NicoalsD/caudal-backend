package co.caudal.api.security;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Protects the refresh endpoint against cross-site requests (docs/Seguridad.md, section 2.3).
 *
 * <p>The refresh cookie travels with {@code SameSite=None}, so the browser would attach it to a
 * request started by any page. Two checks stop that: the request must carry {@code
 * X-Requested-With: caudal-web}, a header a plain form or image cannot add, and its {@code Origin}
 * must be one of {@code CORS_ALLOWED_ORIGINS}. A request without {@code Origin} is refused too,
 * because browsers always send it on cross-site POST requests.
 */
@Component
public class RefreshRequestGuard {

  /** Header that marks a request made by the CAUDAL web app. */
  public static final String CLIENT_HEADER = "X-Requested-With";

  /** Value of {@link #CLIENT_HEADER} sent by the web app. */
  public static final String CLIENT_VALUE = "caudal-web";

  private final List<String> allowedOrigins;

  RefreshRequestGuard(CorsProperties cors) {
    this.allowedOrigins = cors.allowedOrigins();
  }

  /**
   * Checks the request.
   *
   * @param request the incoming request
   * @throws DomainException with {@code FORBIDDEN} if the header or the origin is not acceptable
   */
  public void verify(HttpServletRequest request) {
    if (!isValid(request)) {
      throw new DomainException(ErrorCode.FORBIDDEN);
    }
  }

  /**
   * Tells whether the request carries the client header and an allowed origin.
   *
   * @param request the incoming request
   * @return true if both checks pass
   */
  public boolean isValid(HttpServletRequest request) {
    String marker = request.getHeader(CLIENT_HEADER);
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    return CLIENT_VALUE.equals(marker) && origin != null && allowedOrigins.contains(origin);
  }

  /**
   * Tells whether an origin is one of the allowed ones.
   *
   * @param origin value of the {@code Origin} header, possibly null
   * @return true if it is listed
   */
  public boolean isAllowedOrigin(String origin) {
    return origin != null && allowedOrigins.contains(origin);
  }
}
