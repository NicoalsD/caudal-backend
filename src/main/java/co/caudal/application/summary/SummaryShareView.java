package co.caudal.application.summary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An authorization of the Board for an entity to see summaries (docs/API.md, section 13.4;
 * docs/Reglas-de-negocio.md, section 16.3).
 *
 * @param id the authorization
 * @param granteeUserId the user of the entity
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day it covers
 * @param periodTo last day it covers
 * @param reason why it was authorized
 * @param authorizedBy who authorized it
 * @param authorizedAt when
 * @param expiresAt when it stops working
 * @param revokedAt when it was revoked, or null
 * @param revokedBy who revoked it, or null
 */
public record SummaryShareView(
    UUID id,
    UUID granteeUserId,
    String scope,
    LocalDate periodFrom,
    LocalDate periodTo,
    String reason,
    UUID authorizedBy,
    Instant authorizedAt,
    Instant expiresAt,
    Instant revokedAt,
    UUID revokedBy) {}
