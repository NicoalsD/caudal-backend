package co.caudal.application.rules;

import java.time.Instant;
import java.util.UUID;

/**
 * One version of the rules in a list (docs/API.md, section 6.2).
 *
 * @param id the version
 * @param version its number in the aqueduct
 * @param status {@code DRAFT}, {@code ACTIVE}, {@code SUPERSEDED} or {@code DISCARDED}
 * @param validFrom start of its validity, null for a draft
 * @param validTo end of its validity, null while it is active or a draft
 * @param activatedAt when it was activated, null if never
 * @param basedOnRuleSetId the version it was copied from, null for the first one
 */
public record RuleSetSummary(
    UUID id,
    int version,
    String status,
    Instant validFrom,
    Instant validTo,
    Instant activatedAt,
    UUID basedOnRuleSetId) {}
