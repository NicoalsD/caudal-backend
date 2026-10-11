package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ProposalStateTest {

  @Test
  void theHappyPathGoesFromReviewToClosed() {
    ProposalState state = ProposalState.of("PENDING_REVIEW");

    assertThat(state.approve().publish().close().code()).isEqualTo("CLOSED");
    assertThat(state.modify().code()).isEqualTo("APPROVED_WITH_CHANGES");
    assertThat(state.modify().publish().code()).isEqualTo("PUBLISHED");
    assertThat(state.reject().code()).isEqualTo("REJECTED");
  }

  @Test
  void everyOtherMoveIsAnInvalidTransition() {
    List<String> statuses =
        List.of(
            "DRAFT",
            "PENDING_REVIEW",
            "APPROVED",
            "APPROVED_WITH_CHANGES",
            "REJECTED",
            "PUBLISHED",
            "CLOSED");
    List<UnaryOperator<ProposalState>> moves =
        List.of(
            ProposalState::approve,
            ProposalState::modify,
            ProposalState::reject,
            ProposalState::publish,
            ProposalState::close);
    int allowed = 0;
    for (String status : statuses) {
      for (UnaryOperator<ProposalState> move : moves) {
        try {
          move.apply(ProposalState.of(status));
          allowed++;
        } catch (DomainException refused) {
          assertThat(refused.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
          assertThat(refused.details()).containsEntry("from", status);
        }
      }
    }
    assertThat(allowed).as("approve, modify, reject, two publishes and close").isEqualTo(6);
    assertThatThrownBy(() -> ProposalState.of("LOST")).isInstanceOf(IllegalArgumentException.class);
  }
}
