package co.caudal.domain.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AnomalyStateTest {

  private static ErrorCode codeOf(Throwable failure) {
    return ((DomainException) failure).code();
  }

  @Test
  void anOpenAnomalyIsConfirmedOrDismissedWithItsReason() {
    assertThat(AnomalyState.of("OPEN").resolve("CONFIRMED", null).code()).isEqualTo("CONFIRMED");
    assertThat(AnomalyState.of("OPEN").resolve("DISMISSED", "La caída fue por la lluvia.").code())
        .isEqualTo("DISMISSED");
  }

  @Test
  void aDismissalNeedsAReasonAndResolvedAnomaliesDoNotChange() {
    assertThatThrownBy(() -> AnomalyState.of("OPEN").resolve("DISMISSED", "corto"))
        .extracting(AnomalyStateTest::codeOf)
        .isEqualTo(ErrorCode.REASON_REQUIRED);
    assertThatThrownBy(() -> AnomalyState.of("OPEN").resolve("REOPENED", null))
        .extracting(AnomalyStateTest::codeOf)
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThatThrownBy(() -> AnomalyState.of("CONFIRMED").resolve("DISMISSED", "Otra vez más."))
        .extracting(AnomalyStateTest::codeOf)
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThatThrownBy(() -> AnomalyState.of("DISMISSED").resolve("CONFIRMED", null))
        .extracting(AnomalyStateTest::codeOf)
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThatThrownBy(() -> AnomalyState.of("LOST")).isInstanceOf(IllegalArgumentException.class);
  }
}
