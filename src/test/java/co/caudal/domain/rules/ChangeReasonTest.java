package co.caudal.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ChangeReasonTest {

  @Test
  void keepsATrimmedReasonOfTheRightLength() {
    assertThat(ChangeReason.require("  Sube la reserva.  ").value()).isEqualTo("Sube la reserva.");
  }

  @Test
  void aMissingShortOrLongReasonIsRequired() {
    for (String bad : new String[] {null, "", "  corto  ", "x".repeat(501)}) {
      assertThatThrownBy(() -> ChangeReason.require(bad))
          .isInstanceOf(DomainException.class)
          .extracting(failure -> ((DomainException) failure).code())
          .isEqualTo(ErrorCode.REASON_REQUIRED);
    }
  }
}
