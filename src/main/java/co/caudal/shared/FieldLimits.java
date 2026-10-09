package co.caudal.shared;

/**
 * Single source of technical and security limits (lengths and patterns).
 *
 * <p>These constants feed Bean Validation, JPA column definitions, Flyway placeholders and {@code
 * GET /api/v1/meta/constraints}. Business parameters never live here: they belong to the versioned
 * rule sets in the database.
 */
public final class FieldLimits {

  /** Maximum length of a correlation id ({@code request_id}, {@code varchar(40)} in the DB). */
  public static final int REQUEST_ID_MAX = 40;

  /** Characters accepted in a client supplied {@code X-Request-Id}. */
  public static final String REQUEST_ID_PATTERN = "^[A-Za-z0-9-]{1," + REQUEST_ID_MAX + "}$";

  private FieldLimits() {}
}
