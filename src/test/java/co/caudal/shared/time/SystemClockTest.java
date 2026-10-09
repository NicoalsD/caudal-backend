package co.caudal.shared.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SystemClockTest {

  @Test
  void alwaysReturnsTheSameInstance() {
    assertThat(SystemClock.getInstance()).isSameAs(SystemClock.getInstance());
  }

  @Test
  void usesUtc() {
    assertThat(SystemClock.getInstance().getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void refusesToChangeZone() {
    ZoneId bogota = ZoneId.of("America/Bogota");

    assertThatThrownBy(() -> SystemClock.getInstance().withZone(bogota))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void readsTheCurrentInstant() {
    Instant before = Instant.now();
    Instant read = SystemClock.getInstance().instant();
    Instant after = Instant.now();

    assertThat(read).isBetween(before, after);
    assertThat(SystemClock.getInstance().millis()).isGreaterThanOrEqualTo(read.toEpochMilli());
  }
}
