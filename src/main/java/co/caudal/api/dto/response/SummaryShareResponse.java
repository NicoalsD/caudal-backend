package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An authorization of the Board for an entity.
 *
 * @param id the authorization
 * @param granteeUserId the user of the entity
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day
 * @param periodTo last day
 * @param reason why it was authorized
 * @param authorizedBy who authorized it
 * @param authorizedAt when
 * @param expiresAt when it stops working
 * @param revokedAt when it was revoked, or null
 * @param revokedBy who revoked it, or null
 */
@Schema(description = "Autorización de resumen para una entidad de apoyo.")
public record SummaryShareResponse(
    UUID id,
    UUID granteeUserId,
    @Schema(example = "MONTHLY_SUMMARY") String scope,
    LocalDate periodFrom,
    LocalDate periodTo,
    String reason,
    UUID authorizedBy,
    Instant authorizedAt,
    Instant expiresAt,
    @Schema(nullable = true) Instant revokedAt,
    @Schema(nullable = true) UUID revokedBy) {}
