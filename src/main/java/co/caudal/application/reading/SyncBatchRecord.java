package co.caudal.application.reading;

import java.util.UUID;

/**
 * An offline batch already received ({@code ops.sync_batches}).
 *
 * @param id the id chosen by the client
 * @param itemsReceived readings it had
 * @param itemsAccepted readings stored or recognized as retries
 * @param itemsRejected readings refused
 */
public record SyncBatchRecord(UUID id, int itemsReceived, int itemsAccepted, int itemsRejected) {}
