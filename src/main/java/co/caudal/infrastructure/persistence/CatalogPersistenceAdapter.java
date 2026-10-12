package co.caudal.infrastructure.persistence;

import co.caudal.application.catalog.CatalogItemView;
import co.caudal.application.port.out.CatalogPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads {@code org.catalog_items}: the global items and, if given, those of one aqueduct. */
@Component
public class CatalogPersistenceAdapter implements CatalogPort {

  private final JdbcClient jdbc;

  CatalogPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<CatalogItemView> activeItems(String catalog, UUID aqueductId) {
    return jdbc.sql(
            "SELECT code, label_es, sort_order FROM org.catalog_items WHERE catalog = :catalog"
                + " AND is_active AND (aqueduct_id IS NULL OR aqueduct_id = :aqueductId)"
                + " ORDER BY sort_order, code")
        .param("catalog", catalog)
        .param("aqueductId", aqueductId, java.sql.Types.OTHER)
        .query(
            (rs, row) ->
                new CatalogItemView(
                    rs.getString("code"), rs.getString("label_es"), rs.getInt("sort_order")))
        .list();
  }

  @Override
  public boolean isActiveCode(String catalog, String code, UUID aqueductId) {
    return jdbc.sql(
                "SELECT count(*) FROM org.catalog_items WHERE catalog = :catalog AND code = :code"
                    + " AND is_active AND (aqueduct_id IS NULL OR aqueduct_id = :aqueductId)")
            .param("catalog", catalog)
            .param("code", code)
            .param("aqueductId", aqueductId, java.sql.Types.OTHER)
            .query(Integer.class)
            .single()
        > 0;
  }
}
