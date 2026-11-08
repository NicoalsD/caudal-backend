package co.caudal.domain.autopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DecisionStatusTest {

  @Test
  void aPendingDecisionCanBeResolvedInEveryWay() {
    for (DecisionStatus next :
        new DecisionStatus[] {
          DecisionStatus.ACCEPTED,
          DecisionStatus.REJECTED,
          DecisionStatus.EXECUTED,
          DecisionStatus.EXPIRED,
          DecisionStatus.SKIPPED
        }) {
      assertThat(DecisionStatus.PENDING.moveTo(next)).isSameAs(next);
    }
    assertThat(DecisionStatus.PENDING.isOpen()).isTrue();
  }

  @Test
  void anAcceptedDecisionOnlyBecomesExecuted() {
    assertThat(DecisionStatus.ACCEPTED.moveTo(DecisionStatus.EXECUTED))
        .isSameAs(DecisionStatus.EXECUTED);
    assertThatThrownBy(() -> DecisionStatus.ACCEPTED.moveTo(DecisionStatus.REJECTED))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
  }

  @Test
  void aClosedDecisionDoesNotMove() {
    for (DecisionStatus closed :
        new DecisionStatus[] {
          DecisionStatus.REJECTED,
          DecisionStatus.EXECUTED,
          DecisionStatus.EXPIRED,
          DecisionStatus.SKIPPED
        }) {
      assertThat(closed.isOpen()).isFalse();
      assertThatThrownBy(() -> closed.moveTo(DecisionStatus.ACCEPTED))
          .isInstanceOf(DomainException.class);
    }
  }

  @Test
  void aStatusIsRestoredFromItsCode() {
    assertThat(DecisionStatus.of("EXPIRED")).isSameAs(DecisionStatus.EXPIRED);
    assertThatThrownBy(() -> DecisionStatus.of("DONE"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
