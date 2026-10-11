package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.reading.CorrectionView;
import co.caudal.application.reading.IssueView;
import co.caudal.application.reading.LevelPoint;
import co.caudal.application.reading.ListedReading;
import co.caudal.application.reading.ReadingFilter;
import co.caudal.application.reading.ReadingView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes {@code ops.readings} and {@code ops.reading_issues}. */
@Component
public class ReadingPersistenceAdapter implements ReadingPort {

  private static final String READING_COLUMNS =
      "SELECT r.id, r.tank_id, r.gauge_value, r.water_appearance_code, r.damage_noticed, r.note,"
          + " r.observed_at, r.received_at, r.source, r.validation_status, r.rule_set_id"
          + " FROM ops.readings r";

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  ReadingPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public Optional<ReadingView> findById(UUID id) {
    return jdbc.sql(READING_COLUMNS + " WHERE r.id = :id")
        .param("id", id)
        .query(ReadingPersistenceAdapter::reading)
        .optional();
  }

  @Override
  public List<IssueView> issues(UUID readingId) {
    return jdbc.sql(
            "SELECT issue_code, severity, details::text AS details FROM ops.reading_issues"
                + " WHERE reading_id = :id ORDER BY issue_code")
        .param("id", readingId)
        .query(
            (rs, row) ->
                new IssueView(
                    rs.getString("issue_code"),
                    rs.getString("severity"),
                    json.readValue(rs.getString("details"), MAP_TYPE)))
        .list();
  }

  @Override
  public Optional<UUID> findDuplicate(
      UUID tankId, BigDecimal value, Instant observedAt, int windowMinutes, UUID excludeId) {
    return jdbc.sql(
            "SELECT id FROM ops.effective_readings WHERE tank_id = :tank AND id <> :exclude"
                + " AND gauge_value = :value AND observed_at BETWEEN"
                + " CAST(:at AS timestamptz) - make_interval(mins => CAST(:window AS integer))"
                + " AND CAST(:at AS timestamptz) + make_interval(mins => CAST(:window AS integer))"
                + " ORDER BY observed_at DESC LIMIT 1")
        .param("tank", tankId)
        .param("exclude", excludeId)
        .param("value", value)
        .param("at", SqlTime.timestamp(observedAt))
        .param("window", windowMinutes)
        .query(UUID.class)
        .optional();
  }

  @Override
  public Optional<LevelPoint> previousEffective(UUID tankId, Instant before) {
    return jdbc.sql(
            "SELECT gauge_value, observed_at FROM ops.effective_readings WHERE tank_id = :tank"
                + " AND observed_at < :before ORDER BY observed_at DESC LIMIT 1")
        .param("tank", tankId)
        .param("before", SqlTime.timestamp(before))
        .query(ReadingPersistenceAdapter::point)
        .optional();
  }

  @Override
  public boolean insert(UUID aqueductId, ReadingView r, UUID recordedBy, UUID syncBatchId) {
    return jdbc.sql(
                "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value,"
                    + " water_appearance_code, damage_noticed, note, observed_at, received_at,"
                    + " source, recorded_by, rule_set_id, validation_status, sync_batch_id)"
                    + " VALUES (:id, :aqueduct, :tank, :value, :appearance, :damage, :note,"
                    + " :observed, :received, :source, :user, :ruleSet, :status, :batch)"
                    + " ON CONFLICT (id) DO NOTHING")
            .param("id", r.id())
            .param("aqueduct", aqueductId)
            .param("tank", r.tankId())
            .param("value", r.gaugeValue())
            .param("appearance", r.waterAppearanceCode())
            .param("damage", r.damageNoticed())
            .param("note", r.note(), java.sql.Types.VARCHAR)
            .param("observed", SqlTime.timestamp(r.observedAt()))
            .param("received", SqlTime.timestamp(r.receivedAt()))
            .param("source", r.source())
            .param("user", recordedBy)
            .param("ruleSet", r.ruleSetId())
            .param("status", r.validationStatus())
            .param("batch", syncBatchId, java.sql.Types.OTHER)
            .update()
        > 0;
  }

  @Override
  public void insertIssue(UUID aqueductId, UUID readingId, IssueView issue) {
    jdbc.sql(
            "INSERT INTO ops.reading_issues (aqueduct_id, reading_id, issue_code, severity,"
                + " details) VALUES (:aqueduct, :reading, :code, :severity, CAST(:details AS"
                + " jsonb))")
        .param("aqueduct", aqueductId)
        .param("reading", readingId)
        .param("code", issue.code())
        .param("severity", issue.severity())
        .param("details", json.writeValueAsString(issue.details()))
        .update();
  }

  @Override
  public List<ListedReading> list(ReadingFilter filter, Instant before, UUID beforeId, int limit) {
    return jdbc.sql(
            "SELECT r.id, r.tank_id, r.gauge_value, r.water_appearance_code, r.damage_noticed,"
                + " r.note, r.observed_at, r.received_at, r.source, r.validation_status,"
                + " r.rule_set_id, e.gauge_value AS effective_value,"
                + " (SELECT count(*) FROM ops.reading_corrections c WHERE c.reading_id = r.id)"
                + " AS corrections FROM ops.readings r JOIN ops.effective_readings e ON e.id ="
                + " r.id WHERE r.tank_id = :tank"
                + " AND (CAST(:from AS timestamptz) IS NULL OR r.observed_at >= :from)"
                + " AND (CAST(:to AS timestamptz) IS NULL OR r.observed_at <= :to)"
                + " AND (CAST(:status AS varchar) IS NULL OR r.validation_status = :status)"
                + " AND (CAST(:before AS timestamptz) IS NULL OR"
                + " (r.observed_at, r.id) < (CAST(:before AS timestamptz), CAST(:beforeId AS uuid)))"
                + " ORDER BY r.observed_at DESC, r.id DESC LIMIT :limit")
        .param("tank", filter.tankId())
        .param("from", SqlTime.offset(filter.from()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param("to", SqlTime.offset(filter.to()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "status",
            filter.status() == null ? null : filter.status().name(),
            java.sql.Types.VARCHAR)
        .param("before", SqlTime.offset(before), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param("beforeId", beforeId, java.sql.Types.OTHER)
        .param("limit", limit)
        .query(
            (rs, row) ->
                new ListedReading(
                    reading(rs, row),
                    rs.getBigDecimal("effective_value"),
                    rs.getInt("corrections")))
        .list();
  }

  @Override
  public List<LevelPoint> effectiveSince(UUID tankId, Instant since) {
    return jdbc.sql(
            "SELECT gauge_value, observed_at FROM ops.effective_readings WHERE tank_id = :tank"
                + " AND observed_at >= :since ORDER BY observed_at")
        .param("tank", tankId)
        .param("since", SqlTime.timestamp(since))
        .query(ReadingPersistenceAdapter::point)
        .list();
  }

  @Override
  public Optional<LevelPoint> latestEffective(UUID tankId) {
    return jdbc.sql(
            "SELECT gauge_value, observed_at FROM ops.effective_readings WHERE tank_id = :tank"
                + " ORDER BY observed_at DESC LIMIT 1")
        .param("tank", tankId)
        .query(ReadingPersistenceAdapter::point)
        .optional();
  }

  private static LevelPoint point(ResultSet rs, int row) throws SQLException {
    return new LevelPoint(rs.getBigDecimal("gauge_value"), SqlTime.instant(rs, "observed_at"));
  }

  private static ReadingView reading(ResultSet rs, int row) throws SQLException {
    return new ReadingView(
        rs.getObject("id", UUID.class),
        rs.getObject("tank_id", UUID.class),
        rs.getBigDecimal("gauge_value"),
        rs.getString("water_appearance_code"),
        rs.getBoolean("damage_noticed"),
        rs.getString("note"),
        SqlTime.instant(rs, "observed_at"),
        SqlTime.instant(rs, "received_at"),
        rs.getString("source"),
        rs.getString("validation_status"),
        rs.getObject("rule_set_id", UUID.class));
  }

  @Override
  public List<CorrectionView> corrections(UUID readingId) {
    return jdbc.sql(
            "SELECT id, reading_id, corrected_gauge_value, reason, corrected_by, created_at"
                + " FROM ops.reading_corrections WHERE reading_id = :reading"
                + " ORDER BY created_at, id")
        .param("reading", readingId)
        .query(ReadingPersistenceAdapter::correction)
        .list();
  }

  @Override
  public Optional<UUID> recordedBy(UUID readingId) {
    return jdbc.sql("SELECT recorded_by FROM ops.readings WHERE id = :id")
        .param("id", readingId)
        .query((rs, row) -> rs.getObject("recorded_by", UUID.class))
        .optional();
  }

  @Override
  public CorrectionView insertCorrection(
      UUID aqueductId,
      UUID readingId,
      BigDecimal correctedGaugeValue,
      String reason,
      UUID correctedBy) {
    return jdbc.sql(
            "INSERT INTO ops.reading_corrections (aqueduct_id, reading_id, corrected_gauge_value,"
                + " reason, corrected_by) VALUES (:aqueduct, :reading, :value, :reason, :by)"
                + " RETURNING id, reading_id, corrected_gauge_value, reason, corrected_by,"
                + " created_at")
        .param("aqueduct", aqueductId)
        .param("reading", readingId)
        .param("value", correctedGaugeValue)
        .param("reason", reason)
        .param("by", correctedBy)
        .query(ReadingPersistenceAdapter::correction)
        .single();
  }

  @Override
  public Optional<BigDecimal> effectiveValue(UUID readingId) {
    return jdbc.sql("SELECT gauge_value FROM ops.effective_readings WHERE id = :id")
        .param("id", readingId)
        .query(BigDecimal.class)
        .optional();
  }

  private static CorrectionView correction(ResultSet rs, int row) throws SQLException {
    return new CorrectionView(
        rs.getObject("id", UUID.class),
        rs.getObject("reading_id", UUID.class),
        rs.getBigDecimal("corrected_gauge_value"),
        rs.getString("reason"),
        rs.getObject("corrected_by", UUID.class),
        SqlTime.instant(rs, "created_at"));
  }
}
