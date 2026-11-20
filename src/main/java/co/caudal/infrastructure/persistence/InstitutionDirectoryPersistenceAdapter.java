package co.caudal.infrastructure.persistence;

import co.caudal.application.institution.InstitutionListing;
import co.caudal.application.port.out.InstitutionDirectoryPort;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads {@code org.aqueducts} for the public directory. The table has no row level security, so the
 * application role reads it without fixing an institution.
 */
@Component
public class InstitutionDirectoryPersistenceAdapter implements InstitutionDirectoryPort {

  private final JdbcClient jdbc;

  InstitutionDirectoryPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<InstitutionListing> listAll() {
    return jdbc.sql(
            "SELECT slug, name, municipality, is_demo, public_page_enabled"
                + " FROM org.aqueducts ORDER BY name, slug")
        .query(
            (rs, row) ->
                new InstitutionListing(
                    rs.getString("slug"),
                    rs.getString("name"),
                    rs.getString("municipality"),
                    rs.getBoolean("is_demo"),
                    rs.getBoolean("public_page_enabled")))
        .list();
  }
}
