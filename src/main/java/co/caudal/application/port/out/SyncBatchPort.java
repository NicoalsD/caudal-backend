package co.caudal.application.port.out;

import co.caudal.application.reading.SyncBatchRecord;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stores the offline batches ({@code ops.sync_batches}, append only). */
public interface SyncBatchPort {

  /**
   * Finds a batch of the aqueduct of the session.
   *
   * @param id the batch
   * @return the batch, or empty
   */
  Optional<SyncBatchRecord> find(UUID id);

  /**
   * Stores a batch with its counts; it never changes afterwards.
   *
   * @param aqueductId the aqueduct
   * @param userId who sent it
   * @param batch the batch
   */
  void insert(UUID aqueductId, UUID userId, SyncBatchRecord batch);

  /**
   * The ids of the readings stored with a batch.
   *
   * @param id the batch
   * @return the ids
   */
  Set<UUID> readingIds(UUID id);
}
