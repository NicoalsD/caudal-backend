package co.caudal.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.domain.rules.RuleSet.Parameters;
import co.caudal.domain.rules.RuleSet.SectorSetting;
import co.caudal.domain.rules.RuleSet.ValveOrder;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleSetTest {

  static final Parameters PARAMETERS =
      new Parameters(
          new BigDecimal("1.00"),
          "REDUCE_TO_CRITICAL_BAND",
          new BigDecimal("16.00"),
          new BigDecimal("1.00"),
          new BigDecimal("4.00"),
          "PRIORITY_THEN_LONGEST_WAIT",
          30,
          new BigDecimal("0.50"),
          12,
          7,
          3,
          30,
          new BigDecimal("0.20"));

  static RuleSet active() {
    return new RuleSet(
        UUID.randomUUID(),
        3,
        "ACTIVE",
        null,
        PARAMETERS,
        List.of(
            new LevelBand(
                "CRITICAL",
                new BigDecimal("0.00"),
                new BigDecimal("1.50"),
                new BigDecimal("3.00"),
                true),
            new LevelBand(
                "LOW",
                new BigDecimal("1.50"),
                new BigDecimal("3.50"),
                new BigDecimal("8.00"),
                false),
            new LevelBand(
                "HIGH",
                new BigDecimal("3.50"),
                new BigDecimal("5.00"),
                new BigDecimal("16.00"),
                false)),
        List.of(new SectorSetting(UUID.randomUUID(), true, true, 1)),
        List.of(new ValveOrder(UUID.randomUUID(), 1)),
        List.of(new OperatingWindow(null, LocalTime.of(5, 0), LocalTime.of(21, 0))));
  }

  @Test
  void aCopyIsADraftOfTheNextVersionThatPointsToItsOrigin() {
    RuleSet original = active();

    RuleSet copy = original.copyForNewVersion(4);

    assertThat(copy.id()).isNull();
    assertThat(copy.version()).isEqualTo(4);
    assertThat(copy.status()).isEqualTo("DRAFT");
    assertThat(copy.isDraft()).isTrue();
    assertThat(original.isDraft()).isFalse();
    assertThat(copy.basedOnRuleSetId()).isEqualTo(original.id());
    assertThat(copy.parameters()).isEqualTo(original.parameters());
    assertThat(copy.bands()).isEqualTo(original.bands());
    assertThat(copy.sectors()).isEqualTo(original.sectors());
    assertThat(copy.valveOrder()).isEqualTo(original.valveOrder());
    assertThat(copy.windows()).isEqualTo(original.windows());
  }

  @Test
  void theOriginalNeverChangesAndTheListsAreFrozen() {
    List<LevelBand> bands = new ArrayList<>(active().bands());
    RuleSet built =
        new RuleSet(null, 1, "DRAFT", null, PARAMETERS, bands, List.of(), List.of(), List.of());
    bands.clear();

    assertThat(built.bands()).hasSize(3);
    assertThatThrownBy(() -> built.bands().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aCopyMustMoveForward() {
    assertThatThrownBy(() -> active().copyForNewVersion(3))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
