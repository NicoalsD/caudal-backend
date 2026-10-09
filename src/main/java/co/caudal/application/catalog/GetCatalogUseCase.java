package co.caudal.application.catalog;

import co.caudal.application.port.out.CatalogPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Returns the items of an option list. */
public class GetCatalogUseCase {

  /** Names of the catalogs that exist. */
  public static final Set<String> CATALOGS = Set.of("WATER_APPEARANCE", "DAMAGE_CATEGORY");

  private final CatalogPort catalogs;

  /**
   * Creates the use case.
   *
   * @param catalogs catalogs
   */
  public GetCatalogUseCase(CatalogPort catalogs) {
    this.catalogs = catalogs;
  }

  /**
   * Lists the active items.
   *
   * @param catalog the catalog name
   * @param aqueductId the aqueduct of the session, or null if nobody is signed in
   * @return the items
   * @throws DomainException with {@code NOT_FOUND} if the catalog does not exist
   */
  public List<CatalogItemView> execute(String catalog, UUID aqueductId) {
    if (!CATALOGS.contains(catalog)) {
      throw new DomainException(ErrorCode.NOT_FOUND);
    }
    return catalogs.activeItems(catalog, aqueductId);
  }
}
