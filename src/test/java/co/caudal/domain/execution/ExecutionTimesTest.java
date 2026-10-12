package co.caudal.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.FieldValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExecutionTimesTest {

  static final Instant START = Instant.parse("2026-10-12T10:00:00Z");
  static final Instant END = Instant.parse("2026-10-12T14:00:00Z");

  @Test
  void aCompletedShiftTakesThePlannedHoursWhenNoneAreGiven() {
    assertThat(ExecutionTimes.resolve("COMPLETED", null, null, START, END))
        .isEqualTo(new ExecutionTimes(START, END));
    Instant later = START.plusSeconds(600);
    assertThat(ExecutionTimes.resolve("COMPLETED", later, null, START, END).start())
        .isEqualTo(later);
  }

  @Test
  void aPartialShiftNeedsItsRealStart() {
    assertThatThrownBy(() -> ExecutionTimes.resolve("PARTIAL", null, END, START, END))
        .isInstanceOf(FieldValidationException.class);
    assertThat(ExecutionTimes.resolve("PARTIAL", START, null, START, END).end()).isEqualTo(END);
  }

  @Test
  void aShiftNotExecutedHasNoHours() {
    assertThat(ExecutionTimes.resolve("NOT_EXECUTED", null, null, START, END))
        .isEqualTo(new ExecutionTimes(null, null));
    assertThatThrownBy(() -> ExecutionTimes.resolve("NOT_EXECUTED", START, null, START, END))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> ExecutionTimes.resolve("NOT_EXECUTED", null, END, START, END))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  void theEndComesAfterTheStart() {
    assertThatThrownBy(() -> ExecutionTimes.resolve("COMPLETED", END, START, START, END))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> ExecutionTimes.resolve("PARTIAL", START, START, START, END))
        .isInstanceOf(FieldValidationException.class);
  }
}
