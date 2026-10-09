package co.caudal.api.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the API from a browser ({@code CORS_ALLOWED_ORIGINS}, comma separated).
 * The same list protects CORS and the {@code Origin} check of the refresh endpoint.
 *
 * @param allowedOrigins exact origins such as {@code https://app.example.test}; never {@code *}
 */
@ConfigurationProperties(prefix = "caudal.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

  private static final String WILDCARD = "*";

  /**
   * Cleans the list and refuses a wildcard, which would be unsafe together with credentials.
   *
   * @throws IllegalArgumentException if the list is empty or contains {@code *}
   */
  public CorsProperties {
    allowedOrigins =
        allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
    if (allowedOrigins.isEmpty()) {
      throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must list at least one origin");
    }
    if (allowedOrigins.stream().anyMatch(origin -> origin.contains(WILDCARD))) {
      throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must not contain wildcards");
    }
  }
}
