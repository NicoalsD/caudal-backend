package co.caudal.domain.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every transition of the valve command lifecycle (docs/Protocolo-de-dispositivos.md, 5.2). */
class ValveCommandStateTest {

  private static final String INVALID = "INVALID";

  /** The move of a command, by name. */
  private static UnaryOperator<ValveCommandState> move(String name) {
    return switch (name) {
      case "deliver" -> ValveCommandState::deliver;
      case "ack" -> ValveCommandState::ack;
      case "fail" -> ValveCommandState::fail;
      case "expire" -> ValveCommandState::expire;
      case "cancel" -> ValveCommandState::cancel;
      default -> throw new IllegalArgumentException(name);
    };
  }

  /**
   * Each row is a state, a move and the state it leads to, or {@code INVALID} when the move is
   * refused. The rows cover the six states and the five moves.
   */
  @ParameterizedTest
  @CsvSource({
    "PENDING, deliver, DELIVERED",
    "PENDING, ack, INVALID",
    "PENDING, fail, INVALID",
    "PENDING, expire, EXPIRED",
    "PENDING, cancel, CANCELLED",
    "DELIVERED, deliver, DELIVERED",
    "DELIVERED, ack, ACKED",
    "DELIVERED, fail, FAILED",
    "DELIVERED, expire, EXPIRED",
    "DELIVERED, cancel, INVALID",
    "ACKED, deliver, INVALID",
    "ACKED, ack, ACKED",
    "ACKED, fail, INVALID",
    "ACKED, expire, INVALID",
    "ACKED, cancel, INVALID",
    "FAILED, deliver, INVALID",
    "FAILED, ack, INVALID",
    "FAILED, fail, FAILED",
    "FAILED, expire, INVALID",
    "FAILED, cancel, INVALID",
    "EXPIRED, deliver, INVALID",
    "EXPIRED, ack, INVALID",
    "EXPIRED, fail, INVALID",
    "EXPIRED, expire, INVALID",
    "EXPIRED, cancel, INVALID",
    "CANCELLED, deliver, INVALID",
    "CANCELLED, ack, INVALID",
    "CANCELLED, fail, INVALID",
    "CANCELLED, expire, INVALID",
    "CANCELLED, cancel, INVALID"
  })
  void everyMoveLeadsWhereTheLifecycleSays(String from, String name, String expected) {
    ValveCommandState state = ValveCommandState.of(from);
    UnaryOperator<ValveCommandState> apply = move(name);

    if (INVALID.equals(expected)) {
      assertThatThrownBy(() -> apply.apply(state))
          .isInstanceOf(DomainException.class)
          .extracting(error -> ((DomainException) error).code())
          .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    } else {
      assertThat(apply.apply(state).status()).isEqualTo(expected);
    }
  }

  @Test
  void theStatusOfEachStateIsItsStoredCode() {
    for (String code :
        new String[] {"PENDING", "DELIVERED", "ACKED", "FAILED", "EXPIRED", "CANCELLED"}) {
      assertThat(ValveCommandState.of(code).status()).isEqualTo(code);
    }
  }

  @Test
  void anUnknownStoredStatusIsRefused() {
    assertThatThrownBy(() -> ValveCommandState.of("LOST"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
