package co.caudal.application.port.out;

import co.caudal.application.catalog.CatalogItemView;
import java.util.List;
import java.util.UUID;

/** Reads the option lists ({@code org.catalog_items}, which has no row level security). */
public interface CatalogPort {

  /**
   * Lists the active items of a catalog.
   *
   * @param catalog the catalog name
   * @param aqueductId the aqueduct whose own items are included, or null for the global ones only
   * @return the items ordered by {@code sort_order}
   */
  List<CatalogItemView> activeItems(String catalog, UUID aqueductId);

  /**
   * Tells whether a code belongs to an active item of a catalog.
   *
   * @param catalog the catalog name
   * @param code the item code
   * @param aqueductId the aqueduct whose own items count, or null for the global ones only
   * @return true if the item exists and is active
   */
  boolean isActiveCode(String catalog, String code, UUID aqueductId);
}
