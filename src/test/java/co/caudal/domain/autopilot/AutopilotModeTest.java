package co.caudal.domain.autopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AutopilotModeTest {

  @Test
  void eachModeSaysWhatItAllows() {
    assertThat(AutopilotMode.MANUAL.acceptsDecisions()).isFalse();
    assertThat(AutopilotMode.MANUAL.executesByItself()).isFalse();
    assertThat(AutopilotMode.ASSISTED.acceptsDecisions()).isTrue();
    assertThat(AutopilotMode.ASSISTED.executesByItself()).isFalse();
    assertThat(AutopilotMode.AUTOMATIC.acceptsDecisions()).isTrue();
    assertThat(AutopilotMode.AUTOMATIC.executesByItself()).isTrue();
  }

  @Test
  void aModeIsRestoredFromItsCode() {
    assertThat(AutopilotMode.of("ASSISTED")).isSameAs(AutopilotMode.ASSISTED);
    assertThat(AutopilotMode.of("AUTOMATIC")).isSameAs(AutopilotMode.AUTOMATIC);
  }

  @Test
  void anUnknownModeIsAValidationError() {
    for (String code : new String[] {"FREE", "manual", "", null}) {
      assertThatThrownBy(() -> AutopilotMode.of(code))
          .isInstanceOfSatisfying(
              DomainException.class,
              e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
  }

  @Test
  void onlyOpenAndCloseMoveAValve() {
    assertThat(AutopilotAction.OPEN.movesValve()).isTrue();
    assertThat(AutopilotAction.CLOSE.movesValve()).isTrue();
    assertThat(AutopilotAction.WAIT.movesValve()).isFalse();
    assertThat(AutopilotAction.INSPECT.movesValve()).isFalse();
    assertThat(AutopilotAction.of("INSPECT")).isSameAs(AutopilotAction.INSPECT);
    assertThatThrownBy(() -> AutopilotAction.of("EXPLODE")).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> AutopilotAction.of(null)).isInstanceOf(DomainException.class);
  }
}
