package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.SyncBatchPort;
import co.caudal.application.reading.SyncBatchRecord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes {@code ops.sync_batches}. Row level security limits the aqueduct. */
@Component
public class SyncBatchPersistenceAdapter implements SyncBatchPort {

  private final JdbcClient jdbc;

  SyncBatchPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<SyncBatchRecord> find(UUID id) {
    return jdbc.sql(
            "SELECT id, items_received, items_accepted, items_rejected FROM ops.sync_batches"
                + " WHERE id = :id")
        .param("id", id)
        .query(
            (rs, row) ->
                new SyncBatchRecord(
                    rs.getObject("id", UUID.class),
                    rs.getInt("items_received"),
                    rs.getInt("items_accepted"),
                    rs.getInt("items_rejected")))
        .optional();
  }

  @Override
  public void insert(UUID aqueductId, UUID userId, SyncBatchRecord batch) {
    jdbc.sql(
            "INSERT INTO ops.sync_batches (id, aqueduct_id, user_id, items_received,"
                + " items_accepted, items_rejected) VALUES (:id, :aqueduct, :user, :received,"
                + " :accepted, :rejected)")
        .param("id", batch.id())
        .param("aqueduct", aqueductId)
        .param("user", userId)
        .param("received", batch.itemsReceived())
        .param("accepted", batch.itemsAccepted())
        .param("rejected", batch.itemsRejected())
        .update();
  }

  @Override
  public Set<UUID> readingIds(UUID id) {
    return new HashSet<>(
        jdbc.sql("SELECT id FROM ops.readings WHERE sync_batch_id = :id")
            .param("id", id)
            .query(UUID.class)
            .list());
  }
}
