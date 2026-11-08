package co.caudal.infrastructure.forecast;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * When the autopilot runs by itself.
 *
 * @param enabled whether the scheduler runs ({@code AUTOPILOT_ENABLED}); the forced cycle of {@code
 *     POST /api/v1/autopilot/run} always works
 * @param intervalSeconds seconds between two cycles ({@code AUTOPILOT_INTERVAL_SECONDS}, 60 by
 *     default; 10 is fine for a demo)
 */
@ConfigurationProperties(prefix = "caudal.autopilot")
public record AutopilotProperties(boolean enabled, long intervalSeconds) {

  /** Refuses an interval that is not positive. */
  public AutopilotProperties {
    if (intervalSeconds <= 0) {
      throw new IllegalArgumentException("Autopilot interval must be positive");
    }
  }
}
