package co.caudal.application.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import org.junit.jupiter.api.Test;

class PageLimitTest {

  @Test
  void acceptsTheRangeAndRejectsTheRest() {
    assertThat(PageLimit.checked(1)).isEqualTo(1);
    assertThat(PageLimit.checked(100)).isEqualTo(100);
    assertThatThrownBy(() -> PageLimit.checked(0)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> PageLimit.checked(101)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> PageLimit.checked(Integer.MAX_VALUE))
        .isInstanceOf(DomainException.class);
  }
}
