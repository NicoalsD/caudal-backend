package co.caudal.infrastructure.persistence;

import co.caudal.application.minutes.MinutesView;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.domain.minutes.Minutes;
import co.caudal.domain.minutes.MinutesEntry;
import co.caudal.domain.minutes.MinutesSection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores minutes with their content as JSON. Row level security limits the aqueduct. */
@Component
public class MinutesPersistenceAdapter implements MinutesPort {

  private static final TypeReference<Map<String, List<Map<String, String>>>> CONTENT =
      new TypeReference<>() {};
  static final String COLUMNS =
      "SELECT id, period_from, period_to, status, content, generated_by, generated_at,"
          + " pdf_sha256, audit_head_hash, finalized_by, finalized_at FROM reporting.minutes";

  private final JdbcClient jdbc;
  private final JsonMapper json;

  MinutesPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public UUID insert(UUID aqueductId, Minutes minutes, UUID generatedBy) {
    Map<String, List<Map<String, String>>> content = new LinkedHashMap<>();
    for (MinutesSection section : MinutesSection.values()) {
      content.put(
          section.key(),
          minutes.section(section).stream().map(MinutesPersistenceAdapter::entry).toList());
    }
    return jdbc.sql(
            "INSERT INTO reporting.minutes (aqueduct_id, period_from, period_to, content,"
                + " generated_by) VALUES (:aqueduct, :from, :to, CAST(:content AS jsonb), :by)"
                + " RETURNING id")
        .param("aqueduct", aqueductId)
        .param("from", minutes.periodFrom())
        .param("to", minutes.periodTo())
        .param("content", json.writeValueAsString(content))
        .param("by", generatedBy)
        .query(UUID.class)
        .single();
  }

  @Override
  public Optional<MinutesView> find(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = :id").param("id", id).query(this::view).optional();
  }

  @Override
  public boolean finalizeDraft(
      UUID id, UUID finalizedBy, Instant finalizedAt, String pdfSha256, String auditHeadHash) {
    return jdbc.sql(
                "UPDATE reporting.minutes SET status = 'FINAL', finalized_by = :by, finalized_at ="
                    + " :at, pdf_sha256 = :sha, audit_head_hash = :head WHERE id = :id AND status"
                    + " = 'DRAFT'")
            .param("by", finalizedBy)
            .param("at", SqlTime.offset(finalizedAt), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("sha", pdfSha256, Types.CHAR)
            .param("head", auditHeadHash, Types.CHAR)
            .param("id", id)
            .update()
        == 1;
  }

  @Override
  public Optional<String> auditHeadHash() {
    return jdbc.sql("SELECT row_hash FROM audit.audit_log ORDER BY id DESC LIMIT 1")
        .query(String.class)
        .optional();
  }

  private static Map<String, String> entry(MinutesEntry e) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("source", e.source());
    row.put("source_id", e.sourceId());
    row.put("at", e.at().toString());
    row.put("summary", e.summary());
    return row;
  }

  MinutesView view(ResultSet rs, int row) throws SQLException {
    Map<String, List<MinutesView.Entry>> content = new LinkedHashMap<>();
    json.readValue(rs.getString("content"), CONTENT)
        .forEach(
            (section, entries) ->
                content.put(
                    section,
                    entries.stream()
                        .map(
                            e ->
                                new MinutesView.Entry(
                                    e.get("source"),
                                    e.get("source_id"),
                                    Instant.parse(e.get("at")),
                                    e.get("summary")))
                        .toList()));
    return new MinutesView(
        rs.getObject("id", UUID.class),
        rs.getObject("period_from", LocalDate.class),
        rs.getObject("period_to", LocalDate.class),
        rs.getString("status"),
        content,
        rs.getObject("generated_by", UUID.class),
        SqlTime.instant(rs, "generated_at"),
        rs.getString("pdf_sha256"),
        rs.getString("audit_head_hash"),
        rs.getObject("finalized_by", UUID.class),
        SqlTime.instant(rs, "finalized_at"));
  }
}
