package co.caudal.domain.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import org.junit.jupiter.api.Test;

class IncidentStateTest {

  @Test
  void aReportGoesThroughVerificationToResolution() {
    IncidentState state = IncidentState.of("REPORTED").verify().confirm().resolve();

    assertThat(state.code()).isEqualTo("RESOLVED");
    assertThat(state.publicLabel()).isEqualTo("Resuelto");
  }

  @Test
  void onlyReportedOrVerifyingAreDismissed() {
    assertThat(IncidentState.of("REPORTED").dismiss().code()).isEqualTo("DISMISSED");
    assertThat(IncidentState.of("VERIFYING").dismiss().code()).isEqualTo("DISMISSED");
    assertThatThrownBy(() -> IncidentState.of("CONFIRMED").dismiss())
        .isInstanceOf(DomainException.class);
  }

  @Test
  void finalStatesDoNotMove() {
    for (String status : new String[] {"RESOLVED", "DISMISSED"}) {
      IncidentState state = IncidentState.of(status);
      assertThatThrownBy(state::verify).isInstanceOf(DomainException.class);
      assertThatThrownBy(state::confirm).isInstanceOf(DomainException.class);
      assertThatThrownBy(state::resolve).isInstanceOf(DomainException.class);
      assertThatThrownBy(state::dismiss).isInstanceOf(DomainException.class);
    }
    assertThatThrownBy(() -> IncidentState.of("REPORTED").resolve())
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> IncidentState.of("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyStateHasAPublicLabel() {
    for (String status :
        new String[] {"REPORTED", "VERIFYING", "CONFIRMED", "RESOLVED", "DISMISSED"}) {
      assertThat(IncidentState.of(status).publicLabel()).isNotBlank();
      assertThat(IncidentState.of(status).code()).isEqualTo(status);
    }
  }
}
