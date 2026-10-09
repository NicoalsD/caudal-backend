package co.caudal.api.security;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Attributes of the refresh cookie that depend on the deployment.
 *
 * @param sameSite {@code None} when the PWA and the API are different sites (the default), {@code
 *     Strict} or {@code Lax} when they share a domain ({@code COOKIE_SAME_SITE})
 * @param secure {@code Secure} attribute; true everywhere except local HTTP ({@code COOKIE_SECURE})
 */
@ConfigurationProperties(prefix = "caudal.security.cookie")
public record RefreshCookieProperties(String sameSite, boolean secure) {

  private static final Set<String> ALLOWED = Set.of("None", "Lax", "Strict");

  /**
   * Normalizes the SameSite value and refuses combinations that browsers reject.
   *
   * @throws IllegalArgumentException if SameSite is unknown, or {@code None} without {@code Secure}
   */
  public RefreshCookieProperties {
    String normalized = capitalize(sameSite);
    if (!ALLOWED.contains(normalized)) {
      throw new IllegalArgumentException("COOKIE_SAME_SITE must be None, Lax or Strict");
    }
    if ("None".equals(normalized) && !secure) {
      throw new IllegalArgumentException("SameSite=None requires COOKIE_SECURE=true");
    }
    sameSite = normalized;
  }

  private static String capitalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String lower = value.strip().toLowerCase(Locale.ROOT);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }
}
