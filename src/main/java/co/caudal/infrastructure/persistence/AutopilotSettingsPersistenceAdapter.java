package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.domain.autopilot.AutopilotMode;
import co.caudal.domain.autopilot.AutopilotSettings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes {@code org.autopilot_settings}. Row level security limits the aqueduct. */
@Component
public class AutopilotSettingsPersistenceAdapter implements AutopilotSettingsPort {

  private static final String COLUMNS =
      "aqueduct_id, mode, operator_absence_minutes, reserve_floor_pct, updated_at";

  private final JdbcClient jdbc;

  AutopilotSettingsPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AutopilotSettings getOrCreate(UUID aqueductId) {
    // The defaults (ASSISTED, 30 minutes, 25 %) are the ones of the columns of the table.
    jdbc.sql(
            "INSERT INTO org.autopilot_settings (aqueduct_id) VALUES (:aqueduct)"
                + " ON CONFLICT (aqueduct_id) DO NOTHING")
        .param("aqueduct", aqueductId)
        .update();
    return jdbc.sql(
            "SELECT " + COLUMNS + " FROM org.autopilot_settings WHERE aqueduct_id = :aqueduct")
        .param("aqueduct", aqueductId)
        .query(AutopilotSettingsPersistenceAdapter::settings)
        .single();
  }

  @Override
  public AutopilotSettings save(
      UUID aqueductId,
      AutopilotMode mode,
      int operatorAbsenceMinutes,
      BigDecimal reserveFloorPct,
      UUID updatedBy) {
    return jdbc.sql(
            "UPDATE org.autopilot_settings SET mode = :mode, operator_absence_minutes = :minutes,"
                + " reserve_floor_pct = :floor, updated_by = :by, updated_at = now()"
                + " WHERE aqueduct_id = :aqueduct RETURNING "
                + COLUMNS)
        .param("mode", mode.name())
        .param("minutes", operatorAbsenceMinutes)
        .param("floor", reserveFloorPct)
        .param("by", updatedBy, Types.OTHER)
        .param("aqueduct", aqueductId)
        .query(AutopilotSettingsPersistenceAdapter::settings)
        .single();
  }

  private static AutopilotSettings settings(ResultSet rs, int row) throws SQLException {
    return new AutopilotSettings(
        rs.getObject("aqueduct_id", UUID.class),
        AutopilotMode.of(rs.getString("mode")),
        rs.getInt("operator_absence_minutes"),
        rs.getBigDecimal("reserve_floor_pct"),
        SqlTime.instant(rs, "updated_at"));
  }
}
