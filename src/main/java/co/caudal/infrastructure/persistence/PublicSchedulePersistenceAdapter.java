package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.PublicSchedulePort;
import co.caudal.application.publication.PublicSchedule;
import co.caudal.application.publication.ScheduleLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads the last proposal of a day, not rejected, with its publication if there is one. It returns
 * everything it finds; {@code PublicScheduleProxy} decides what the public may see. Row level
 * security limits the aqueduct.
 */
public class PublicSchedulePersistenceAdapter implements PublicSchedulePort {

  private final JdbcClient jdbc;

  /**
   * Creates the adapter.
   *
   * @param jdbc database access
   */
  public PublicSchedulePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<PublicSchedule> find(LocalDate day) {
    return jdbc.sql(
            "SELECT p.id, p.status, a.name, a.is_demo, a.public_page_enabled, u.id AS"
                + " publication_id, u.published_at, u.published_by, u.whatsapp_text,"
                + " u.poster_sha256 FROM ops.schedule_proposals p JOIN org.aqueducts a ON a.id ="
                + " p.aqueduct_id LEFT JOIN ops.publications u ON u.proposal_id = p.id"
                + " WHERE p.service_date = :day AND p.status <> 'REJECTED'"
                + " ORDER BY p.generated_at DESC LIMIT 1")
        .param("day", day)
        .query(
            (rs, row) ->
                new PublicSchedule(
                    rs.getString("name"),
                    rs.getBoolean("is_demo"),
                    rs.getBoolean("public_page_enabled"),
                    day,
                    rs.getString("status"),
                    rs.getObject("publication_id", UUID.class),
                    SqlTime.instant(rs, "published_at"),
                    rs.getObject("published_by", UUID.class),
                    rs.getString("whatsapp_text"),
                    rs.getString("poster_sha256"),
                    lines(rs.getObject("id", UUID.class))))
        .optional();
  }

  private List<ScheduleLine> lines(UUID proposalId) {
    return jdbc.sql(
            "SELECT s.name, i.start_at, i.end_at FROM ops.schedule_items i JOIN org.sectors s"
                + " ON s.id = i.sector_id WHERE i.proposal_id = :proposal ORDER BY i.sequence")
        .param("proposal", proposalId)
        .query(
            (rs, row) ->
                new ScheduleLine(
                    rs.getString("name"),
                    SqlTime.instant(rs, "start_at"),
                    SqlTime.instant(rs, "end_at")))
        .list();
  }
}
