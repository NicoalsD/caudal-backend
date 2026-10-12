package co.caudal.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A version of the rules came into force (docs/Reglas-de-negocio.md, section 1.2).
 *
 * @param aqueductId the aqueduct
 * @param ruleSetId the new active version
 * @param version its number
 * @param supersededRuleSetId the version it replaced, or null for the first one
 * @param activatedBy who activated it
 * @param changeReason why
 * @param occurredAt when it came into force
 */
public record RuleSetActivated(
    UUID aqueductId,
    UUID ruleSetId,
    int version,
    UUID supersededRuleSetId,
    UUID activatedBy,
    String changeReason,
    Instant occurredAt)
    implements DomainEvent {}
