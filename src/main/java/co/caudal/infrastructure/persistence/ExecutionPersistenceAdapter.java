package co.caudal.infrastructure.persistence;

import co.caudal.application.execution.ExecutionView;
import co.caudal.application.execution.NewExecution;
import co.caudal.application.execution.ShiftContext;
import co.caudal.application.port.out.ExecutionPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stores executions of shifts. Row level security limits the aqueduct. */
@Component
public class ExecutionPersistenceAdapter implements ExecutionPort {

  private static final String COLUMNS =
      "SELECT e.id, e.schedule_item_id, e.status, e.actual_start, e.actual_end, e.note,"
          + " e.supersedes_id, e.recorded_by, e.recorded_at FROM ops.shift_executions e";

  private final JdbcClient jdbc;

  ExecutionPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ShiftContext> shift(UUID itemId) {
    return jdbc.sql(
            "SELECT i.id, p.status, p.service_date, i.start_at, i.end_at FROM ops.schedule_items i"
                + " JOIN ops.schedule_proposals p ON p.id = i.proposal_id WHERE i.id = :id")
        .param("id", itemId)
        .query(
            (rs, row) ->
                new ShiftContext(
                    rs.getObject("id", UUID.class),
                    rs.getString("status"),
                    rs.getObject("service_date", LocalDate.class),
                    SqlTime.instant(rs, "start_at"),
                    SqlTime.instant(rs, "end_at")))
        .optional();
  }

  @Override
  public Optional<ExecutionView> find(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE e.id = :id")
        .param("id", id)
        .query(ExecutionPersistenceAdapter::view)
        .optional();
  }

  @Override
  public boolean superseded(UUID id) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM ops.shift_executions WHERE supersedes_id = :id)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  @Override
  public boolean hasCurrent(UUID itemId) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM ops.shift_executions e WHERE e.schedule_item_id = :item"
                + " AND NOT EXISTS (SELECT 1 FROM ops.shift_executions x"
                + " WHERE x.supersedes_id = e.id))")
        .param("item", itemId)
        .query(Boolean.class)
        .single();
  }

  @Override
  public ExecutionView insert(UUID aqueductId, NewExecution execution) {
    UUID id =
        jdbc.sql(
                "INSERT INTO ops.shift_executions (aqueduct_id, schedule_item_id, status,"
                    + " actual_start, actual_end, note, supersedes_id, recorded_by) VALUES"
                    + " (:aqueduct, :item, :status, :start, :end, :note, :supersedes, :by)"
                    + " RETURNING id")
            .param("aqueduct", aqueductId)
            .param("item", execution.scheduleItemId())
            .param("status", execution.status())
            .param("start", SqlTime.offset(execution.actualStart()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("end", SqlTime.offset(execution.actualEnd()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("note", execution.note(), Types.VARCHAR)
            .param("supersedes", execution.supersedesId(), Types.OTHER)
            .param("by", execution.recordedBy())
            .query(UUID.class)
            .single();
    return find(id).orElseThrow();
  }

  @Override
  public List<ExecutionView> forDay(LocalDate serviceDate) {
    return jdbc.sql(
            COLUMNS
                + " JOIN ops.schedule_items i ON i.id = e.schedule_item_id"
                + " JOIN ops.schedule_proposals p ON p.id = i.proposal_id"
                + " WHERE p.service_date = :day AND p.status IN ('PUBLISHED', 'CLOSED')"
                + " ORDER BY e.recorded_at, e.id")
        .param("day", serviceDate)
        .query(ExecutionPersistenceAdapter::view)
        .list();
  }

  private static ExecutionView view(ResultSet rs, int row) throws SQLException {
    return new ExecutionView(
        rs.getObject("id", UUID.class),
        rs.getObject("schedule_item_id", UUID.class),
        rs.getString("status"),
        SqlTime.instant(rs, "actual_start"),
        SqlTime.instant(rs, "actual_end"),
        rs.getString("note"),
        rs.getObject("supersedes_id", UUID.class),
        rs.getObject("recorded_by", UUID.class),
        SqlTime.instant(rs, "recorded_at"));
  }
}
