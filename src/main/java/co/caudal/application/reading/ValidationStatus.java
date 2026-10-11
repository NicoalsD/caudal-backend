package co.caudal.application.reading;

/** Result of validating a stored reading; invalid readings are rejected and never stored. */
public enum ValidationStatus {
  /** The reading passed every rule. */
  ACCEPTED,
  /** The reading was stored with a warning (duplicate, sudden jump) for a person to review. */
  FLAGGED
}
