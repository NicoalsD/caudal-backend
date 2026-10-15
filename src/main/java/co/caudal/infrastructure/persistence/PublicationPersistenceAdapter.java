package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.PublicationPort;
import co.caudal.application.publication.PublicationView;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stores publications of schedules. Row level security limits the aqueduct. */
@Component
public class PublicationPersistenceAdapter implements PublicationPort {

  private final JdbcClient jdbc;

  PublicationPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PublicationView insert(
      UUID aqueductId,
      UUID proposalId,
      UUID publishedBy,
      String whatsappText,
      String posterSha256) {
    return jdbc.sql(
            "INSERT INTO ops.publications (aqueduct_id, proposal_id, published_by, whatsapp_text,"
                + " poster_sha256) VALUES (:aqueduct, :proposal, :by, :text, :sha)"
                + " RETURNING id, proposal_id, published_at, whatsapp_text, poster_sha256")
        .param("aqueduct", aqueductId)
        .param("proposal", proposalId)
        .param("by", publishedBy)
        .param("text", whatsappText)
        .param("sha", posterSha256, Types.CHAR)
        .query(
            (rs, row) ->
                new PublicationView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("proposal_id", UUID.class),
                    SqlTime.instant(rs, "published_at"),
                    rs.getString("whatsapp_text"),
                    rs.getString("poster_sha256")))
        .single();
  }

  @Override
  public Optional<PublicationView> findByProposal(UUID proposalId) {
    return jdbc.sql(
            "SELECT id, proposal_id, published_at, whatsapp_text, poster_sha256"
                + " FROM ops.publications WHERE proposal_id = :proposal"
                + " ORDER BY published_at DESC LIMIT 1")
        .param("proposal", proposalId)
        .query(
            (rs, row) ->
                new PublicationView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("proposal_id", UUID.class),
                    SqlTime.instant(rs, "published_at"),
                    rs.getString("whatsapp_text"),
                    rs.getString("poster_sha256")))
        .optional();
  }
}
