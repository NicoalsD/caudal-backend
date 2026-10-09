package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.AqueductPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads {@code org.aqueducts}. */
@Component
public class AqueductPersistenceAdapter implements AqueductPort {

  private static final String SELECT =
      "SELECT id, slug, name, timezone, is_demo, public_page_enabled FROM org.aqueducts WHERE ";

  private final JdbcClient jdbc;

  AqueductPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<AqueductRef> findBySlug(String slug) {
    return jdbc.sql(SELECT + "slug = :slug")
        .param("slug", slug)
        .query(AqueductPersistenceAdapter::map)
        .optional();
  }

  @Override
  public Optional<AqueductRef> findById(UUID id) {
    return jdbc.sql(SELECT + "id = :id")
        .param("id", id)
        .query(AqueductPersistenceAdapter::map)
        .optional();
  }

  private static AqueductRef map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new AqueductRef(
        rs.getObject("id", UUID.class),
        rs.getString("slug"),
        rs.getString("name"),
        rs.getString("timezone"),
        rs.getBoolean("is_demo"),
        rs.getBoolean("public_page_enabled"));
  }
}
