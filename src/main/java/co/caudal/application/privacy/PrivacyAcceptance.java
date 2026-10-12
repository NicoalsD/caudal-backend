package co.caudal.application.privacy;

import java.time.Instant;
import java.util.UUID;

/**
 * A person accepted a version of the privacy notice.
 *
 * @param userId the account
 * @param version the version
 * @param acceptedAt when
 */
public record PrivacyAcceptance(UUID userId, int version, Instant acceptedAt) {}
