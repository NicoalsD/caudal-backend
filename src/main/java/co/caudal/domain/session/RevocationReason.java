package co.caudal.domain.session;

/** Why a refresh token stopped being usable ({@code iam.refresh_tokens.revoked_reason}). */
public enum RevocationReason {
  /** Replaced by its successor in a normal refresh. */
  ROTATED,
  /** A token that was already rotated was presented again; the whole family is revoked. */
  REUSE_DETECTED,
  /** The person closed the session. */
  LOGOUT,
  /** The person closed every session. */
  LOGOUT_ALL,
  /** The password changed. */
  PASSWORD_CHANGED,
  /** The role changed. */
  ROLE_CHANGED,
  /** An administrator revoked the session. */
  ADMIN
}
