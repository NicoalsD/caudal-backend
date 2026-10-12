package co.caudal.application.port.out;

import co.caudal.application.summary.NewSummaryShare;
import co.caudal.application.summary.SummaryShareView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Authorizations for entities to see summaries ({@code reporting.summary_shares}). */
public interface SummarySharePort {

  /**
   * Stores an authorization.
   *
   * @param aqueductId the aqueduct
   * @param share the authorization
   * @return its id
   */
  UUID insert(UUID aqueductId, NewSummaryShare share);

  /**
   * Finds an authorization of the aqueduct of the session, revoked or not.
   *
   * @param id the authorization
   * @return the authorization, or empty
   */
  Optional<SummaryShareView> find(UUID id);

  /**
   * Revokes an authorization that is not revoked yet. The row stays.
   *
   * @param id the authorization
   * @param revokedBy who revokes it
   * @param revokedAt when
   * @return true if it was revoked now, false if it was already revoked
   */
  boolean revoke(UUID id, UUID revokedBy, Instant revokedAt);

  /**
   * Lists the authorizations that work now: not revoked and not expired. A period given must lie
   * inside the period of the authorization.
   *
   * @param granteeUserId only this grantee, or null for every grantee of the aqueduct
   * @param from first day requested, or null
   * @param to last day requested, or null
   * @param now the moment of the query
   * @param beforeId only ids before this one, or null from the newest
   * @param limit maximum number
   * @return the authorizations, newest first
   */
  List<SummaryShareView> listActive(
      UUID granteeUserId, LocalDate from, LocalDate to, Instant now, UUID beforeId, int limit);
}
