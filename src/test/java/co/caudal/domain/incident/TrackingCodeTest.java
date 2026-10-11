package co.caudal.domain.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrackingCodeTest {

  @Test
  void aNewCodeHasEightCrockfordCharactersAndHidesItself() {
    TrackingCode code = TrackingCode.generate();

    assertThat(code.value()).hasSize(8).matches("[0-9A-HJKMNP-TV-Z]{8}");
    assertThat(code.hash()).hasSize(64).isNotEqualTo(code.value());
    assertThat(code.toString()).doesNotContain(code.value());
  }

  @Test
  void aTypedCodeIsReadLeniently() {
    assertThat(TrackingCode.parse(" abcd-2345 ")).contains(new TrackingCode("ABCD2345"));
    assertThat(TrackingCode.parse("ABCD234")).isEmpty();
    assertThat(TrackingCode.parse("ABCD234I")).isEmpty();
    assertThat(TrackingCode.parse(null)).isEmpty();
    assertThatThrownBy(() -> new TrackingCode("short"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
