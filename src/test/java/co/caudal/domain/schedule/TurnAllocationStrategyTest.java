package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TurnAllocationStrategyTest {

  private static final Instant FIVE = Instant.parse("2026-10-09T10:00:00Z");

  private static ScheduleProposal run(String code, String budget, boolean priorityOnly) {
    ScheduleProposalBuilder builder =
        new ScheduleProposalBuilder()
            .forDay(LocalDate.of(2026, 10, 9), UUID.randomUUID())
            .level(new BigDecimal("2.10"), "LOW")
            .availableHours(new BigDecimal(budget))
            .strategy(code);
    TurnAllocationStrategy.forCode(code)
        .allocate(
            new AllocationInput(
                SectorTurnIteratorTest.EXAMPLE,
                priorityOnly,
                new BigDecimal(budget),
                FIVE,
                new BigDecimal("1.00"),
                new BigDecimal("3.00"),
                s -> List.of()),
            builder);
    return builder.build();
  }

  private static long hours(ProposedTurn turn) {
    return java.time.Duration.between(turn.start(), turn.end()).toMinutes();
  }

  @Test
  void theExampleOfTheRulesGivesThreeThreeAndTwoHours() {
    ScheduleProposal proposal = run("PRIORITY_THEN_LONGEST_WAIT", "8.00", false);

    assertThat(proposal.turns())
        .extracting(TurnAllocationStrategyTest::hours)
        .containsExactly(180L, 180L, 120L);
    assertThat(proposal.turns().getFirst().start()).isEqualTo(FIVE);
    assertThat(proposal.turns().get(1).start()).isEqualTo(proposal.turns().getFirst().end());
    assertThat(proposal.unserved()).extracting(UnservedSector::reason).containsExactly("NO_BUDGET");
    assertThat(proposal.leftoverHours()).isEqualByComparingTo("0");
  }

  @Test
  void aLeftoverShorterThanAShiftIsKept() {
    ScheduleProposal proposal = run("PRIORITY_THEN_LONGEST_WAIT", "6.50", false);

    assertThat(proposal.turns()).hasSize(2);
    assertThat(proposal.leftoverHours()).isEqualByComparingTo("0.50");
    assertThat(proposal.unserved()).hasSize(2);
  }

  @Test
  void aPriorityOnlyDayServesOnlyTheSchool() {
    ScheduleProposal proposal = run("PRIORITY_THEN_LONGEST_WAIT", "3.00", true);

    assertThat(proposal.turns()).hasSize(1);
    assertThat(proposal.unserved()).isEmpty();
  }

  @Test
  void equalSplitGivesEverySectorTheSameShare() {
    ScheduleProposal proposal = run("EQUAL_SPLIT", "8.00", false);

    assertThat(proposal.turns())
        .extracting(TurnAllocationStrategyTest::hours)
        .containsExactly(120L, 120L, 120L, 120L);
    assertThat(proposal.leftoverHours()).isEqualByComparingTo("0");
  }

  @Test
  void equalSplitWithASmallShareServesNobody() {
    ScheduleProposal proposal = run("EQUAL_SPLIT", "3.00", false);

    assertThat(proposal.turns()).isEmpty();
    assertThat(proposal.unserved())
        .hasSize(4)
        .extracting(UnservedSector::reason)
        .containsOnly("CUOTA_INSUFICIENTE");
    assertThatThrownBy(() -> TurnAllocationStrategy.forCode("RANDOM"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
