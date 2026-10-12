package co.caudal.application.user;

import java.time.Instant;
import java.util.UUID;

/**
 * A membership as the Board administers it (docs/API.md, section 3.5).
 *
 * @param id the membership
 * @param userId the account
 * @param aqueductId the aqueduct
 * @param roleCode the role
 * @param validFrom start of the validity
 * @param validTo end of the validity, null while open
 * @param grantedBy who granted it
 */
public record MembershipView(
    UUID id,
    UUID userId,
    UUID aqueductId,
    String roleCode,
    Instant validFrom,
    Instant validTo,
    UUID grantedBy) {}
