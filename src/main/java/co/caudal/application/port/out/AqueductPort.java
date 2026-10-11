package co.caudal.application.port.out;

import co.caudal.application.aqueduct.AqueductRef;
import java.util.Optional;
import java.util.UUID;

/** Reads aqueducts ({@code org.aqueducts}, which has no row level security). */
public interface AqueductPort {

  /**
   * Finds an aqueduct by its public slug.
   *
   * @param slug the slug
   * @return the aqueduct, or empty
   */
  Optional<AqueductRef> findBySlug(String slug);

  /**
   * Finds an aqueduct by id.
   *
   * @param id the aqueduct
   * @return the aqueduct, or empty
   */
  Optional<AqueductRef> findById(UUID id);
}
