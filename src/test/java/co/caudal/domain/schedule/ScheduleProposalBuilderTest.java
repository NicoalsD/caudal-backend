package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleProposalBuilderTest {

  private static final Instant FIVE = Instant.parse("2026-10-09T10:00:00Z");

  @Test
  void buildsAProposalWithNumberedShifts() {
    UUID school = UUID.randomUUID();
    UUID centre = UUID.randomUUID();
    UUID high = UUID.randomUUID();

    ScheduleProposal proposal =
        new ScheduleProposalBuilder()
            .forDay(LocalDate.of(2026, 10, 9), UUID.randomUUID())
            .level(new BigDecimal("2.10"), "LOW")
            .availableHours(new BigDecimal("8.00"))
            .strategy("PRIORITY_THEN_LONGEST_WAIT")
            .turn(school, FIVE, FIVE.plusSeconds(3 * 3600), List.of())
            .turn(centre, FIVE.plusSeconds(3 * 3600), FIVE.plusSeconds(6 * 3600), List.of())
            .unserved(high, "NO_BUDGET")
            .build();

    assertThat(proposal.turns()).extracting(ProposedTurn::sequence).containsExactly(1, 2);
    assertThat(proposal.turns()).extracting(ProposedTurn::origin).containsOnly("PROPOSED");
    assertThat(proposal.unserved()).containsExactly(new UnservedSector(high, "NO_BUDGET"));
    assertThat(proposal.forecastRunId()).isNull();
    assertThat(proposal.leftoverHours()).isEqualByComparingTo("0");
  }

  @Test
  void refusesAnEmptyShiftAndAProposalWithoutItsFacts() {
    ScheduleProposalBuilder builder = new ScheduleProposalBuilder();

    assertThatThrownBy(() -> builder.turn(UUID.randomUUID(), FIVE, FIVE, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }
}
