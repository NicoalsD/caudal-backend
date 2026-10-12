package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.schedule.DecisionView;
import co.caudal.application.schedule.ItemView;
import co.caudal.application.schedule.ProposalDetail;
import co.caudal.application.schedule.ProposalSummary;
import co.caudal.application.schedule.ReasonTexts;
import co.caudal.application.schedule.ReasonView;
import co.caudal.application.schedule.UnservedView;
import co.caudal.domain.schedule.ProposalMemento;
import co.caudal.domain.schedule.ProposedTurn;
import co.caudal.domain.schedule.ScheduleProposal;
import co.caudal.domain.schedule.TurnReason;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes proposals, their items and reasons. Row level security limits the aqueduct. */
@Component
public class ProposalPersistenceAdapter implements ProposalPort {

  private static final TypeReference<Map<String, Object>> PARAMS = new TypeReference<>() {};
  private static final TypeReference<List<Map<String, String>>> UNSERVED = new TypeReference<>() {};
  private static final String PENDING_REVIEW = "PENDING_REVIEW";
  private static final String NO_OVERLAP = "schedule_items_no_overlap";

  static final String SUMMARY_COLUMNS =
      "SELECT id, service_date, status, rule_set_id, tank_level_snapshot, tank_band,"
          + " available_hours, forecast_run_id, strategy_code, generated_at, lock_version"
          + " FROM ops.schedule_proposals";

  private final JdbcClient jdbc;
  private final JsonMapper json;

  ProposalPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public boolean openExists(LocalDate serviceDate) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM ops.schedule_proposals WHERE service_date = :day"
                + " AND status <> 'REJECTED')")
        .param("day", serviceDate)
        .query(Boolean.class)
        .single();
  }

  @Override
  public UUID insert(UUID aqueductId, ScheduleProposal proposal, UUID generatedBy) {
    List<Map<String, String>> unserved =
        proposal.unserved().stream()
            .map(u -> Map.of("sector_id", u.sectorId().toString(), "reason", u.reason()))
            .toList();
    UUID id =
        jdbc.sql(
                "INSERT INTO ops.schedule_proposals (aqueduct_id, service_date, rule_set_id,"
                    + " status, tank_level_snapshot, tank_band, available_hours, forecast_run_id,"
                    + " unserved_sectors, strategy_code, generated_by) VALUES (:aqueduct, :day,"
                    + " :ruleSet, :status, :level, :band, :hours, :forecast, CAST(:unserved AS"
                    + " jsonb), :strategy, :by) RETURNING id")
            .param("aqueduct", aqueductId)
            .param("day", proposal.serviceDate())
            .param("ruleSet", proposal.ruleSetId())
            .param("status", PENDING_REVIEW)
            .param("level", proposal.tankLevel())
            .param("band", proposal.tankBand())
            .param("hours", proposal.availableHours())
            .param("forecast", proposal.forecastRunId(), Types.OTHER)
            .param("unserved", json.writeValueAsString(unserved))
            .param("strategy", proposal.strategyCode())
            .param("by", generatedBy, Types.OTHER)
            .query(UUID.class)
            .single();
    insertItems(aqueductId, id, proposal.turns());
    return id;
  }

  /**
   * Stores shifts and their reasons.
   *
   * @param aqueductId the aqueduct
   * @param proposalId the proposal
   * @param turns the shifts
   */
  void insertItems(UUID aqueductId, UUID proposalId, List<ProposedTurn> turns) {
    for (ProposedTurn turn : turns) {
      UUID item = insertItem(aqueductId, proposalId, turn);
      int order = 0;
      for (TurnReason reason : turn.reasons()) {
        jdbc.sql(
                "INSERT INTO ops.schedule_item_reasons (aqueduct_id, item_id, reason_code, params,"
                    + " sort_order) VALUES (:aqueduct, :item, :code, CAST(:params AS jsonb),"
                    + " :order)")
            .param("aqueduct", aqueductId)
            .param("item", item)
            .param("code", reason.code())
            .param("params", json.writeValueAsString(reason.params()))
            .param("order", order++)
            .update();
      }
    }
  }

  /**
   * Stores one shift. The exclusion {@code schedule_items_no_overlap} refuses two shifts of the
   * same proposal that overlap; it is answered as {@code SCHEDULE_OVERLAP}.
   */
  private UUID insertItem(UUID aqueductId, UUID proposalId, ProposedTurn turn) {
    try {
      return jdbc.sql(
              "INSERT INTO ops.schedule_items (aqueduct_id, proposal_id, sector_id, sequence,"
                  + " start_at, end_at, origin) VALUES (:aqueduct, :proposal, :sector,"
                  + " :sequence, :start, :end, :origin) RETURNING id")
          .param("aqueduct", aqueductId)
          .param("proposal", proposalId)
          .param("sector", turn.sectorId())
          .param("sequence", turn.sequence())
          .param("start", SqlTime.offset(turn.start()), Types.TIMESTAMP_WITH_TIMEZONE)
          .param("end", SqlTime.offset(turn.end()), Types.TIMESTAMP_WITH_TIMEZONE)
          .param("origin", turn.origin())
          .query(UUID.class)
          .single();
    } catch (DataIntegrityViolationException refused) {
      if (String.valueOf(refused.getMostSpecificCause().getMessage()).contains(NO_OVERLAP)) {
        throw new DomainException(ErrorCode.SCHEDULE_OVERLAP);
      }
      throw refused;
    }
  }

  @Override
  public void replaceItems(UUID aqueductId, UUID proposalId, List<ProposedTurn> turns) {
    jdbc.sql(
            "DELETE FROM ops.schedule_item_reasons WHERE item_id IN (SELECT id FROM"
                + " ops.schedule_items WHERE proposal_id = :proposal)")
        .param("proposal", proposalId)
        .update();
    jdbc.sql("DELETE FROM ops.schedule_items WHERE proposal_id = :proposal")
        .param("proposal", proposalId)
        .update();
    insertItems(aqueductId, proposalId, turns);
  }

  @Override
  public Optional<ProposalDetail> find(UUID id, LocalDate today) {
    return jdbc.sql(SUMMARY_COLUMNS + " WHERE id = :id")
        .param("id", id)
        .query(ProposalPersistenceAdapter::summary)
        .optional()
        .map(
            summary ->
                new ProposalDetail(
                    summary,
                    items(id, today),
                    unserved(id),
                    decisions(id),
                    jdbc.sql(
                            "SELECT EXISTS (SELECT 1 FROM ops.proposal_snapshots"
                                + " WHERE proposal_id = :id)")
                        .param("id", id)
                        .query(Boolean.class)
                        .single()));
  }

  @Override
  public List<ProposalSummary> list(
      LocalDate from, LocalDate to, String status, UUID beforeId, int limit) {
    return jdbc.sql(
            SUMMARY_COLUMNS
                + " WHERE (CAST(:from AS date) IS NULL OR service_date >= :from)"
                + " AND (CAST(:to AS date) IS NULL OR service_date <= :to)"
                + " AND (CAST(:status AS varchar) IS NULL OR status = :status)"
                + " AND (CAST(:before AS uuid) IS NULL OR id < :before)"
                + " ORDER BY id DESC LIMIT :limit")
        .param("from", from, Types.DATE)
        .param("to", to, Types.DATE)
        .param("status", status, Types.VARCHAR)
        .param("before", beforeId, Types.OTHER)
        .param("limit", limit)
        .query(ProposalPersistenceAdapter::summary)
        .list();
  }

  @Override
  public boolean transition(UUID id, int lockVersion, String status) {
    return jdbc.sql(
                "UPDATE ops.schedule_proposals SET status = :status, lock_version = lock_version"
                    + " + 1 WHERE id = :id AND lock_version = :lock")
            .param("status", status)
            .param("id", id)
            .param("lock", lockVersion)
            .update()
        == 1;
  }

  @Override
  public void insertDecision(
      UUID aqueductId, UUID proposalId, String decision, String reason, UUID decidedBy) {
    jdbc.sql(
            "INSERT INTO ops.proposal_decisions (aqueduct_id, proposal_id, decision, reason,"
                + " decided_by) VALUES (:aqueduct, :proposal, :decision, :reason, :by)")
        .param("aqueduct", aqueductId)
        .param("proposal", proposalId)
        .param("decision", decision)
        .param("reason", reason, Types.VARCHAR)
        .param("by", decidedBy)
        .update();
  }

  @Override
  public void saveSnapshot(UUID aqueductId, ProposalMemento memento) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("proposal_id", memento.proposalId().toString());
    snapshot.put("lock_version", memento.lockVersion());
    snapshot.put("strategy_code", memento.strategyCode());
    snapshot.put(
        "items",
        memento.items().stream()
            .map(
                i ->
                    Map.of(
                        "item_id", i.itemId().toString(),
                        "sector_id", i.sectorId().toString(),
                        "sequence", i.sequence(),
                        "start_at", i.start().toString(),
                        "end_at", i.end().toString(),
                        "origin", i.origin(),
                        "reasons",
                            i.reasons().stream()
                                .map(r -> Map.of("code", r.code(), "params", r.params()))
                                .toList()))
            .toList());
    jdbc.sql(
            "INSERT INTO ops.proposal_snapshots (aqueduct_id, proposal_id, snapshot, taken_at)"
                + " VALUES (:aqueduct, :proposal, CAST(:snapshot AS jsonb), :at)")
        .param("aqueduct", aqueductId)
        .param("proposal", memento.proposalId())
        .param("snapshot", json.writeValueAsString(snapshot))
        .param("at", SqlTime.offset(memento.takenAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
  }

  static ProposalSummary summary(ResultSet rs, int row) throws SQLException {
    return new ProposalSummary(
        rs.getObject("id", UUID.class),
        rs.getObject("service_date", LocalDate.class),
        rs.getString("status"),
        rs.getObject("rule_set_id", UUID.class),
        rs.getBigDecimal("tank_level_snapshot"),
        rs.getString("tank_band"),
        rs.getBigDecimal("available_hours"),
        rs.getObject("forecast_run_id", UUID.class),
        rs.getString("strategy_code"),
        SqlTime.instant(rs, "generated_at"),
        rs.getInt("lock_version"));
  }

  private List<ItemView> items(UUID proposalId, LocalDate today) {
    return jdbc
        .sql(
            "SELECT id, sector_id, sequence, start_at, end_at, origin FROM ops.schedule_items"
                + " WHERE proposal_id = :proposal ORDER BY sequence")
        .param("proposal", proposalId)
        .query(
            (rs, row) ->
                new ItemView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("sector_id", UUID.class),
                    rs.getInt("sequence"),
                    SqlTime.instant(rs, "start_at"),
                    SqlTime.instant(rs, "end_at"),
                    rs.getString("origin"),
                    List.of()))
        .list()
        .stream()
        .map(
            i ->
                new ItemView(
                    i.id(),
                    i.sectorId(),
                    i.sequence(),
                    i.startAt(),
                    i.endAt(),
                    i.origin(),
                    reasons(i.id(), today)))
        .toList();
  }

  private List<ReasonView> reasons(UUID itemId, LocalDate today) {
    return jdbc.sql(
            "SELECT reason_code, params FROM ops.schedule_item_reasons WHERE item_id = :item"
                + " ORDER BY sort_order")
        .param("item", itemId)
        .query(
            (rs, row) -> {
              Map<String, Object> params = json.readValue(rs.getString("params"), PARAMS);
              String code = rs.getString("reason_code");
              return new ReasonView(code, params, ReasonTexts.text(code, params, today));
            })
        .list();
  }

  private List<UnservedView> unserved(UUID proposalId) {
    String raw =
        jdbc.sql("SELECT unserved_sectors FROM ops.schedule_proposals WHERE id = :id")
            .param("id", proposalId)
            .query(String.class)
            .single();
    return json.readValue(raw, UNSERVED).stream()
        .map(u -> new UnservedView(UUID.fromString(u.get("sector_id")), u.get("reason")))
        .toList();
  }

  private List<DecisionView> decisions(UUID proposalId) {
    return jdbc.sql(
            "SELECT decision, reason, decided_by, decided_at FROM ops.proposal_decisions"
                + " WHERE proposal_id = :proposal ORDER BY decided_at, id")
        .param("proposal", proposalId)
        .query(
            (rs, row) ->
                new DecisionView(
                    rs.getString("decision"),
                    rs.getString("reason"),
                    rs.getObject("decided_by", UUID.class),
                    SqlTime.instant(rs, "decided_at")))
        .list();
  }
}
