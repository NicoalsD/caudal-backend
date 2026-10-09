package co.caudal.domain.security;

/** Kinds of security event ({@code iam.security_events.type}). */
public enum SecurityEventType {
  ACCOUNT_LOCKED,
  TOKEN_REUSE_DETECTED,
  PERMISSION_DENIED,
  RATE_LIMITED,
  INVALID_SIGNATURE,
  NONCE_REPLAY,
  HONEYPOT_TRIGGERED,
  IMPORT_REJECTED,
  MFA_FAILED
}
