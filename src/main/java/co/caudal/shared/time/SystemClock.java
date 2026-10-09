package co.caudal.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The only place in the backend that reads the system clock. Always UTC.
 *
 * <p>Use cases and domain rules receive a {@link Clock}; production wires this instance and tests
 * use a fixed clock, so date rules (future, too old, duplicate window) are reproducible.
 *
 * @pattern P01 Singleton
 */
public final class SystemClock extends Clock {

  private static final SystemClock INSTANCE = new SystemClock();

  private SystemClock() {}

  /**
   * Returns the single instance.
   *
   * @return the system clock in UTC
   */
  public static SystemClock getInstance() {
    return INSTANCE;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("SystemClock is always UTC");
  }

  @Override
  public Instant instant() {
    return Instant.now();
  }

  @Override
  public long millis() {
    return instant().toEpochMilli();
  }
}
