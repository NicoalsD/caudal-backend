package co.caudal.application.summary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An authorization to store.
 *
 * @param granteeUserId the user of the entity
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day it covers
 * @param periodTo last day it covers
 * @param reason why it is authorized
 * @param expiresAt when it stops working
 * @param authorizedBy who authorizes it
 */
public record NewSummaryShare(
    UUID granteeUserId,
    String scope,
    LocalDate periodFrom,
    LocalDate periodTo,
    String reason,
    Instant expiresAt,
    UUID authorizedBy) {}
