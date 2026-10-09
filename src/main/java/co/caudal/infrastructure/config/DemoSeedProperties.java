package co.caudal.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seeding of the demo accounts ({@code SEED_DEMO_DATA}, {@code DEMO_USER_PASSWORD}). The password
 * has no default value and is never stored in git.
 *
 * @param enabled true to create the accounts at startup
 * @param userPassword password of the demo accounts; blank disables the seeding
 * @param aqueductSlug slug of the demo aqueduct
 */
@ConfigurationProperties(prefix = "caudal.demo")
public record DemoSeedProperties(boolean enabled, String userPassword, String aqueductSlug) {

  /** Applies the default slug. */
  public DemoSeedProperties {
    aqueductSlug = aqueductSlug == null || aqueductSlug.isBlank() ? "vereda-demo" : aqueductSlug;
  }

  @Override
  public String toString() {
    return "DemoSeedProperties[enabled=" + enabled + ", aqueductSlug=" + aqueductSlug + "]";
  }
}
