package co.caudal.infrastructure.persistence;

import co.caudal.application.execution.ClosureView;
import co.caudal.application.port.out.ClosurePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stores closures of days. Row level security limits the aqueduct. */
@Component
public class ClosurePersistenceAdapter implements ClosurePort {

  private final JdbcClient jdbc;

  ClosurePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ClosureView> find(LocalDate serviceDate) {
    return jdbc.sql(
            "SELECT id, service_date, notes, closed_by, closed_at FROM ops.day_closures"
                + " WHERE service_date = :day")
        .param("day", serviceDate)
        .query(ClosurePersistenceAdapter::view)
        .optional();
  }

  @Override
  public ClosureView insert(UUID aqueductId, LocalDate serviceDate, String notes, UUID closedBy) {
    return jdbc.sql(
            "INSERT INTO ops.day_closures (aqueduct_id, service_date, notes, closed_by) VALUES"
                + " (:aqueduct, :day, :notes, :by)"
                + " RETURNING id, service_date, notes, closed_by, closed_at")
        .param("aqueduct", aqueductId)
        .param("day", serviceDate)
        .param("notes", notes, Types.VARCHAR)
        .param("by", closedBy)
        .query(ClosurePersistenceAdapter::view)
        .single();
  }

  private static ClosureView view(ResultSet rs, int row) throws SQLException {
    return new ClosureView(
        rs.getObject("id", UUID.class),
        rs.getObject("service_date", LocalDate.class),
        rs.getString("notes"),
        rs.getObject("closed_by", UUID.class),
        SqlTime.instant(rs, "closed_at"));
  }
}
