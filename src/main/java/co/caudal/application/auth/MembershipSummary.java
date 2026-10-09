package co.caudal.application.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * A membership as the API reports it.
 *
 * @param aqueductId the aqueduct
 * @param role role code
 * @param validFrom start of the validity
 * @param validTo end of the validity, null while open
 */
public record MembershipSummary(UUID aqueductId, String role, Instant validFrom, Instant validTo) {}
