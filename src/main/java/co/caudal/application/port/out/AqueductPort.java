package co.caudal.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Reads aqueducts ({@code org.aqueducts}, which has no row level security). */
public interface AqueductPort {

  /**
   * An aqueduct as the use cases need it.
   *
   * @param id the aqueduct
   * @param slug the public identifier
   * @param name the name
   * @param timezone the IANA time zone
   * @param demo true if it only holds simulated data
   * @param publicPageEnabled true if the public schedule page is on
   */
  record AqueductRef(
      UUID id,
      String slug,
      String name,
      String timezone,
      boolean demo,
      boolean publicPageEnabled) {}

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
