package co.caudal.infrastructure.persistence;

import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.network.ValveView;
import co.caudal.application.port.out.NetworkPort;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes tanks, sectors and valves. Row level security limits the aqueduct. */
@Component
public class NetworkPersistenceAdapter implements NetworkPort {

  private static final String TANK_COLUMNS =
      "SELECT id, name, gauge_min, gauge_max, gauge_step, capacity_liters, is_active"
          + " FROM org.tanks";
  private static final String SECTOR_COLUMNS =
      "SELECT id, code, name, parent_sector_id, households_count, is_active FROM org.sectors";

  private static final String VALVE_COLUMNS =
      "SELECT v.id, v.code, v.name, v.location_hint, v.is_motorized, v.fail_safe_position,"
          + " v.is_active FROM org.valves v";

  private final JdbcClient jdbc;

  NetworkPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<TankView> tanks() {
    return jdbc.sql(TANK_COLUMNS + " ORDER BY name, id")
        .query(NetworkPersistenceAdapter::tank)
        .list();
  }

  @Override
  public Optional<TankView> tank(UUID id) {
    return jdbc.sql(TANK_COLUMNS + " WHERE id = :id")
        .param("id", id)
        .query(NetworkPersistenceAdapter::tank)
        .optional();
  }

  @Override
  public TankView insertTank(
      UUID aqueductId,
      String name,
      BigDecimal gaugeMin,
      BigDecimal gaugeMax,
      BigDecimal gaugeStep,
      Integer capacityLiters) {
    return jdbc.sql(
            "INSERT INTO org.tanks (aqueduct_id, name, gauge_min, gauge_max, gauge_step,"
                + " capacity_liters) VALUES (:aqueductId, :name, :min, :max, :step, :capacity)"
                + " RETURNING id, name, gauge_min, gauge_max, gauge_step, capacity_liters,"
                + " is_active")
        .param("aqueductId", aqueductId)
        .param("name", name)
        .param("min", gaugeMin)
        .param("max", gaugeMax)
        .param("step", gaugeStep)
        .param("capacity", capacityLiters, java.sql.Types.INTEGER)
        .query(NetworkPersistenceAdapter::tank)
        .single();
  }

  @Override
  public List<SectorView> sectors() {
    return jdbc.sql(SECTOR_COLUMNS + " ORDER BY code")
        .query(NetworkPersistenceAdapter::sector)
        .list();
  }

  @Override
  public Optional<SectorView> sector(UUID id) {
    return jdbc.sql(SECTOR_COLUMNS + " WHERE id = :id")
        .param("id", id)
        .query(NetworkPersistenceAdapter::sector)
        .optional();
  }

  @Override
  public List<ValveView> valves(UUID sectorId) {
    return jdbc.sql(VALVE_COLUMNS + " WHERE v.sector_id = :sectorId ORDER BY v.code")
        .param("sectorId", sectorId)
        .query(NetworkPersistenceAdapter::valve)
        .list();
  }

  @Override
  public Optional<ValveView> valve(UUID valveId) {
    // The join with org.sectors, which has row level security, hides valves of other aqueducts.
    return jdbc.sql(VALVE_COLUMNS + " JOIN org.sectors s ON s.id = v.sector_id WHERE v.id = :id")
        .param("id", valveId)
        .query(NetworkPersistenceAdapter::valve)
        .optional();
  }

  @Override
  public Optional<ValveView> insertValve(
      UUID sectorId,
      String code,
      String name,
      String locationHint,
      boolean motorized,
      String failSafePosition) {
    return jdbc.sql(
            "INSERT INTO org.valves AS v (sector_id, code, name, location_hint, is_motorized,"
                + " fail_safe_position) VALUES (:sector, :code, :name, :hint, :motorized, :failSafe)"
                + " ON CONFLICT (sector_id, code) DO NOTHING RETURNING v.id, v.code, v.name,"
                + " v.location_hint, v.is_motorized, v.fail_safe_position, v.is_active")
        .param("sector", sectorId)
        .param("code", code)
        .param("name", name)
        .param("hint", locationHint, java.sql.Types.VARCHAR)
        .param("motorized", motorized)
        .param("failSafe", failSafePosition)
        .query(NetworkPersistenceAdapter::valve)
        .optional();
  }

  @Override
  public ValveView updateValve(ValveView valve) {
    return jdbc.sql(
            "UPDATE org.valves AS v SET name = :name, location_hint = :hint,"
                + " is_motorized = :motorized, fail_safe_position = :failSafe, is_active = :active"
                + " WHERE v.id = :id RETURNING v.id, v.code, v.name, v.location_hint,"
                + " v.is_motorized, v.fail_safe_position, v.is_active")
        .param("name", valve.name())
        .param("hint", valve.locationHint(), java.sql.Types.VARCHAR)
        .param("motorized", valve.motorized())
        .param("failSafe", valve.failSafePosition())
        .param("active", valve.active())
        .param("id", valve.id())
        .query(NetworkPersistenceAdapter::valve)
        .single();
  }

  private static ValveView valve(ResultSet rs, int row) throws SQLException {
    return new ValveView(
        rs.getObject("id", UUID.class),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("location_hint"),
        rs.getBoolean("is_motorized"),
        rs.getString("fail_safe_position"),
        rs.getBoolean("is_active"));
  }

  @Override
  public Optional<SectorView> insertSector(
      UUID aqueductId, String code, String name, UUID parentSectorId, int householdsCount) {
    return jdbc.sql(
            "INSERT INTO org.sectors (aqueduct_id, code, name, parent_sector_id, households_count)"
                + " VALUES (:aqueduct, :code, :name, :parent, :households)"
                + " ON CONFLICT (aqueduct_id, code) DO NOTHING"
                + " RETURNING id, code, name, parent_sector_id, households_count, is_active")
        .param("aqueduct", aqueductId)
        .param("code", code)
        .param("name", name)
        .param("parent", parentSectorId, java.sql.Types.OTHER)
        .param("households", householdsCount)
        .query(NetworkPersistenceAdapter::sector)
        .optional();
  }

  @Override
  public SectorView updateSector(UUID sectorId, String name, int householdsCount, boolean active) {
    return jdbc.sql(
            "UPDATE org.sectors SET name = :name, households_count = :households,"
                + " is_active = :active, updated_at = now() WHERE id = :id"
                + " RETURNING id, code, name, parent_sector_id, households_count, is_active")
        .param("name", name)
        .param("households", householdsCount)
        .param("active", active)
        .param("id", sectorId)
        .query(NetworkPersistenceAdapter::sector)
        .single();
  }

  private static TankView tank(ResultSet rs, int row) throws SQLException {
    int capacity = rs.getInt("capacity_liters");
    return new TankView(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getBigDecimal("gauge_min"),
        rs.getBigDecimal("gauge_max"),
        rs.getBigDecimal("gauge_step"),
        rs.wasNull() ? null : capacity,
        rs.getBoolean("is_active"));
  }

  private static SectorView sector(ResultSet rs, int row) throws SQLException {
    return new SectorView(
        rs.getObject("id", UUID.class),
        rs.getString("code"),
        rs.getString("name"),
        rs.getObject("parent_sector_id", UUID.class),
        rs.getInt("households_count"),
        rs.getBoolean("is_active"));
  }
}
