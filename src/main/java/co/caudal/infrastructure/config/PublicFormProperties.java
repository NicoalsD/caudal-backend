package co.caudal.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the public damage form.
 *
 * @param minSeconds shortest time between opening the form and sending it ({@code
 *     PUBLIC_FORM_MIN_SECONDS})
 */
@ConfigurationProperties(prefix = "caudal.security.public-form")
public record PublicFormProperties(int minSeconds) {

  /**
   * Refuses a negative time.
   *
   * @throws IllegalArgumentException if it is negative
   */
  public PublicFormProperties {
    if (minSeconds < 0) {
      throw new IllegalArgumentException("Minimum form time must not be negative");
    }
  }
}
