package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExclusionConstraintsIT extends DatabaseIT {

  private UUID draftRuleSet(UUID aqueduct) {
    return owner.queryForObject(
        "INSERT INTO org.rule_sets (aqueduct_id, version, reserve_level, max_daily_service_hours,"
            + " min_shift_hours, max_shift_hours, duplicate_window_minutes,"
            + " max_level_change_per_hour, stale_reading_hours, max_backdate_days,"
            + " forecast_horizon_days, forecast_context_days, trend_threshold_per_day, created_by)"
            + " VALUES (?, 1, 1, 16, 1, 4, 30, 0.5, 12, 7, 3, 30, 0.2, ?) RETURNING id",
        UUID.class,
        aqueduct,
        systemUser());
  }

  private void band(UUID ruleSet, String band, String min, String max) {
    owner.update(
        "INSERT INTO org.rule_level_bands (rule_set_id, band, min_level, max_level,"
            + " daily_service_hours) VALUES (?, ?, ?::numeric, ?::numeric, 8)",
        ruleSet,
        band,
        min,
        max);
  }

  @Test
  void contiguousBandsAreAccepted() {
    UUID ruleSet = draftRuleSet(newAqueduct(false));

    assertThatCode(
            () -> {
              band(ruleSet, "CRITICAL", "0", "1.5");
              band(ruleSet, "LOW", "1.5", "3.5");
              band(ruleSet, "HIGH", "3.5", "5");
            })
        .doesNotThrowAnyException();
  }

  @Test
  void overlappingBandsAreRejected() {
    UUID ruleSet = draftRuleSet(newAqueduct(false));
    band(ruleSet, "LOW", "1.5", "3.5");

    assertThatThrownBy(() -> band(ruleSet, "HIGH", "3.0", "5"))
        .hasStackTraceContaining("rule_level_bands_no_overlap");
  }

  @Test
  void serviceHoursCannotExceedADay() {
    UUID ruleSet = draftRuleSet(newAqueduct(false));

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO org.rule_level_bands (rule_set_id, band, min_level, max_level,"
                        + " daily_service_hours) VALUES (?, 'HIGH', 3.5, 5, 25)",
                    ruleSet))
        .hasStackTraceContaining("rule_level_bands_daily_service_hours_range");
  }

  @Test
  void overlappingShiftsOfOneProposalAreRejected() {
    UUID aqueduct = newAqueduct(false);
    UUID ruleSet = draftRuleSet(aqueduct);
    UUID sector =
        owner.queryForObject(
            "INSERT INTO org.sectors (aqueduct_id, code, name) VALUES (?, 'S1', 'Sector uno')"
                + " RETURNING id",
            UUID.class,
            aqueduct);
    UUID proposal =
        owner.queryForObject(
            "INSERT INTO ops.schedule_proposals (aqueduct_id, service_date, rule_set_id,"
                + " tank_level_snapshot, tank_band, available_hours, strategy_code)"
                + " VALUES (?, current_date, ?, 3, 'LOW', 8, 'PRIORITY_THEN_LONGEST_WAIT')"
                + " RETURNING id",
            UUID.class,
            aqueduct,
            ruleSet);
    String insert =
        "INSERT INTO ops.schedule_items (aqueduct_id, proposal_id, sector_id, sequence, start_at,"
            + " end_at) VALUES (?, ?, ?, ?, current_date + ?::interval, current_date + ?::interval)";
    owner.update(insert, aqueduct, proposal, sector, 1, "6 hours", "8 hours");

    assertThatCode(() -> owner.update(insert, aqueduct, proposal, sector, 2, "8 hours", "10 hours"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> owner.update(insert, aqueduct, proposal, sector, 3, "9 hours", "11 hours"))
        .hasStackTraceContaining("schedule_items_no_overlap");
  }
}
