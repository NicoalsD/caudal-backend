package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.ScheduleHistoryPort;
import co.caudal.application.schedule.ServiceEnd;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads executions and published shifts to know when a sector last had water. */
@Component
public class ScheduleHistoryPersistenceAdapter implements ScheduleHistoryPort {

  private static final String CONFIRMED = "CONFIRMED";
  private static final String ESTIMATED = "ESTIMATED";

  private final JdbcClient jdbc;

  ScheduleHistoryPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ServiceEnd> lastServiceEnd(UUID sectorId, Instant before) {
    Optional<Instant> executed =
        jdbc.sql(
                "SELECT e.actual_end FROM ops.shift_executions e JOIN ops.schedule_items i"
                    + " ON i.id = e.schedule_item_id WHERE i.sector_id = :sector"
                    + " AND e.status IN ('COMPLETED', 'PARTIAL') AND e.actual_end IS NOT NULL"
                    + " AND e.actual_end <= :before AND NOT EXISTS (SELECT 1 FROM"
                    + " ops.shift_executions x WHERE x.supersedes_id = e.id)"
                    + " ORDER BY e.actual_end DESC LIMIT 1")
            .param("sector", sectorId)
            .param("before", SqlTime.offset(before), Types.TIMESTAMP_WITH_TIMEZONE)
            .query((rs, row) -> SqlTime.instant(rs, "actual_end"))
            .optional();
    if (executed.isPresent()) {
      return Optional.of(new ServiceEnd(executed.get(), CONFIRMED));
    }
    return jdbc.sql(
            "SELECT i.end_at FROM ops.schedule_items i JOIN ops.schedule_proposals p"
                + " ON p.id = i.proposal_id WHERE i.sector_id = :sector"
                + " AND p.status IN ('PUBLISHED', 'CLOSED') AND i.end_at <= :before"
                + " ORDER BY i.end_at DESC LIMIT 1")
        .param("sector", sectorId)
        .param("before", SqlTime.offset(before), Types.TIMESTAMP_WITH_TIMEZONE)
        .query((rs, row) -> new ServiceEnd(SqlTime.instant(rs, "end_at"), ESTIMATED))
        .optional();
  }
}
