package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.rules.RuleSetDetail;
import co.caudal.application.rules.RuleSetSummary;
import co.caudal.application.rules.RuleSetView;
import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.application.rules.RuleSetView.SectorSettingView;
import co.caudal.application.rules.RuleSetView.ValveOrderView;
import co.caudal.application.rules.RuleSetView.WindowView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads {@code org.rule_sets} and its child tables. Row level security limits the aqueduct. */
@Component
public class RuleSetPersistenceAdapter implements RuleSetPort {

  private static final String HEAD =
      "SELECT id, version, valid_from, reserve_level, reserve_policy, max_daily_service_hours,"
          + " min_shift_hours, max_shift_hours, allocation_strategy, duplicate_window_minutes,"
          + " max_level_change_per_hour, stale_reading_hours, max_backdate_days,"
          + " forecast_horizon_days, forecast_context_days, trend_threshold_per_day"
          + " FROM org.rule_sets WHERE ";

  private final JdbcClient jdbc;

  RuleSetPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RuleSetView> findActive() {
    return jdbc.sql(HEAD + "status = 'ACTIVE'")
        .query(this::head)
        .optional()
        .map(this::withChildren);
  }

  @Override
  public Optional<RuleSetView> findById(UUID id) {
    return jdbc.sql(HEAD + "id = :id")
        .param("id", id)
        .query(this::head)
        .optional()
        .map(this::withChildren);
  }

  @Override
  public Optional<RuleSetDetail> findDetail(UUID id) {
    return findById(id)
        .flatMap(
            rules ->
                jdbc.sql(
                        "SELECT status, based_on_rule_set_id, valid_to, change_reason, created_by,"
                            + " created_at, activated_by, activated_at FROM org.rule_sets"
                            + " WHERE id = :id")
                    .param("id", id)
                    .query(
                        (rs, row) ->
                            new RuleSetDetail(
                                rules,
                                rs.getString("status"),
                                rs.getObject("based_on_rule_set_id", UUID.class),
                                SqlTime.instant(rs, "valid_to"),
                                rs.getString("change_reason"),
                                rs.getObject("created_by", UUID.class),
                                SqlTime.instant(rs, "created_at"),
                                rs.getObject("activated_by", UUID.class),
                                SqlTime.instant(rs, "activated_at")))
                    .optional());
  }

  @Override
  public List<RuleSetSummary> list(String status, Integer beforeVersion, int limit) {
    return jdbc.sql(
            "SELECT id, version, status, valid_from, valid_to, activated_at, based_on_rule_set_id"
                + " FROM org.rule_sets WHERE (CAST(:status AS varchar) IS NULL OR status = :status)"
                + " AND (CAST(:before AS integer) IS NULL OR version < :before)"
                + " ORDER BY version DESC LIMIT :limit")
        .param("status", status, Types.VARCHAR)
        .param("before", beforeVersion, Types.INTEGER)
        .param("limit", limit)
        .query(
            (rs, row) ->
                new RuleSetSummary(
                    rs.getObject("id", UUID.class),
                    rs.getInt("version"),
                    rs.getString("status"),
                    SqlTime.instant(rs, "valid_from"),
                    SqlTime.instant(rs, "valid_to"),
                    SqlTime.instant(rs, "activated_at"),
                    rs.getObject("based_on_rule_set_id", UUID.class)))
        .list();
  }

  private RuleSetView head(ResultSet rs, int row) throws SQLException {
    return new RuleSetView(
        rs.getObject("id", UUID.class),
        rs.getInt("version"),
        SqlTime.instant(rs, "valid_from"),
        rs.getBigDecimal("reserve_level"),
        rs.getString("reserve_policy"),
        rs.getBigDecimal("max_daily_service_hours"),
        rs.getBigDecimal("min_shift_hours"),
        rs.getBigDecimal("max_shift_hours"),
        rs.getString("allocation_strategy"),
        rs.getInt("duplicate_window_minutes"),
        rs.getBigDecimal("max_level_change_per_hour"),
        rs.getInt("stale_reading_hours"),
        rs.getInt("max_backdate_days"),
        rs.getInt("forecast_horizon_days"),
        rs.getInt("forecast_context_days"),
        rs.getBigDecimal("trend_threshold_per_day"),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private RuleSetView withChildren(RuleSetView head) {
    return new RuleSetView(
        head.id(),
        head.version(),
        head.validFrom(),
        head.reserveLevel(),
        head.reservePolicy(),
        head.maxDailyServiceHours(),
        head.minShiftHours(),
        head.maxShiftHours(),
        head.allocationStrategy(),
        head.duplicateWindowMinutes(),
        head.maxLevelChangePerHour(),
        head.staleReadingHours(),
        head.maxBackdateDays(),
        head.forecastHorizonDays(),
        head.forecastContextDays(),
        head.trendThresholdPerDay(),
        bands(head.id()),
        sectors(head.id()),
        valves(head.id()),
        windows(head.id()));
  }

  private List<BandView> bands(UUID ruleSetId) {
    return jdbc.sql(
            "SELECT band, min_level, max_level, daily_service_hours, priority_only"
                + " FROM org.rule_level_bands WHERE rule_set_id = :id ORDER BY min_level")
        .param("id", ruleSetId)
        .query(
            (rs, row) ->
                new BandView(
                    rs.getString("band"),
                    rs.getBigDecimal("min_level"),
                    rs.getBigDecimal("max_level"),
                    rs.getBigDecimal("daily_service_hours"),
                    rs.getBoolean("priority_only")))
        .list();
  }

  private List<SectorSettingView> sectors(UUID ruleSetId) {
    return jdbc.sql(
            "SELECT sector_id, is_included, is_priority, priority_rank"
                + " FROM org.rule_sector_settings WHERE rule_set_id = :id"
                + " ORDER BY priority_rank NULLS LAST, sector_id")
        .param("id", ruleSetId)
        .query(
            (rs, row) -> {
              int rank = rs.getInt("priority_rank");
              Integer priorityRank = rs.wasNull() ? null : rank;
              return new SectorSettingView(
                  rs.getObject("sector_id", UUID.class),
                  rs.getBoolean("is_included"),
                  rs.getBoolean("is_priority"),
                  priorityRank);
            })
        .list();
  }

  private List<ValveOrderView> valves(UUID ruleSetId) {
    return jdbc.sql(
            "SELECT valve_id, sequence_order FROM org.rule_valve_orders"
                + " WHERE rule_set_id = :id ORDER BY sequence_order")
        .param("id", ruleSetId)
        .query(
            (rs, row) ->
                new ValveOrderView(
                    rs.getObject("valve_id", UUID.class), rs.getInt("sequence_order")))
        .list();
  }

  private List<WindowView> windows(UUID ruleSetId) {
    return jdbc.sql(
            "SELECT day_of_week, start_time, end_time FROM org.rule_operating_windows"
                + " WHERE rule_set_id = :id ORDER BY day_of_week NULLS FIRST, start_time")
        .param("id", ruleSetId)
        .query(
            (rs, row) -> {
              int day = rs.getInt("day_of_week");
              return new WindowView(
                  rs.wasNull() ? null : day,
                  rs.getObject("start_time", LocalTime.class),
                  rs.getObject("end_time", LocalTime.class));
            })
        .list();
  }
}
