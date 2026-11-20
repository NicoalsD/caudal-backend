package co.caudal.application.port.out;

import co.caudal.application.institution.InstitutionListing;
import java.util.List;

/**
 * Reads every institution with its full row. Only the public directory proxy may expose it, and
 * only as {@code slug} and {@code name}.
 */
public interface InstitutionDirectoryPort {

  /**
   * Lists the institutions, ordered by name.
   *
   * @return every institution, including the ones whose public page is off
   */
  List<InstitutionListing> listAll();
}
