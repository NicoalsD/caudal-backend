package co.caudal.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DayStateTest {

  @Test
  void anOpenDayClosesOnceAndThenTakesNoExecutions() {
    DayState open = DayState.of(false);
    assertThat(open.takesExecutions()).isTrue();

    DayState closed = open.close();

    assertThat(closed.takesExecutions()).isFalse();
    assertThatThrownBy(closed::close)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
    assertThat(DayState.of(true)).isInstanceOf(DayState.Closed.class);
  }
}
