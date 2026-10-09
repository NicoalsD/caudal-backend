package co.caudal.shared;

/**
 * Single source of technical and security limits (lengths and patterns).
 *
 * <p>These constants feed Bean Validation, JPA column definitions, Flyway placeholders and {@code
 * GET /api/v1/meta/constraints}. The Flyway placeholder of a constant is its name in lower case
 * ({@code PERSON_NAME_MAX} becomes {@code ${person_name_max}}). Business parameters never live
 * here: they belong to the versioned rule sets in the database. Canonical table:
 * .agents/input-validation.md, section 3.
 *
 * <p>Patterns with Unicode classes ({@code \p{L}}) are only checked by the API. The database only
 * receives lengths and ASCII patterns.
 */
public final class FieldLimits {

  // Correlation id.

  /** Maximum length of a correlation id ({@code request_id}, {@code varchar(40)} in the DB). */
  public static final int REQUEST_ID_MAX = 40;

  /** Characters accepted in a client supplied {@code X-Request-Id}. */
  public static final String REQUEST_ID_PATTERN = "^[A-Za-z0-9-]{1," + REQUEST_ID_MAX + "}$";

  // Accounts.

  /** Minimum length of a username. */
  public static final int USERNAME_MIN = 3;

  /** Maximum length of a username. */
  public static final int USERNAME_MAX = 32;

  /** Lower case ASCII username that starts and ends with a letter or digit. */
  public static final String USERNAME_PATTERN =
      "^[a-z0-9][a-z0-9._-]{" + (USERNAME_MIN - 2) + "," + (USERNAME_MAX - 2) + "}[a-z0-9]$";

  /** Minimum length of a new password, counted in code points after NFKC. */
  public static final int PASSWORD_MIN = 12;

  /** Maximum length of any password input, checked before hashing. */
  public static final int PASSWORD_MAX = 128;

  /** Length of the random salt of an Argon2id hash, in bytes. */
  public static final int ARGON2_SALT_BYTES = 16;

  /** Length of an Argon2id hash, in bytes. */
  public static final int ARGON2_HASH_BYTES = 32;

  /** Minimum length of the JWT signing secret, in bytes (256 bits for HS256). */
  public static final int JWT_SECRET_MIN_BYTES = 32;

  /** Minimum length of a person name. */
  public static final int PERSON_NAME_MIN = 2;

  /** Maximum length of a person name. */
  public static final int PERSON_NAME_MAX = 80;

  /** Letters, marks, space, dot, apostrophe and hyphen; starts with a letter. */
  public static final String PERSON_NAME_PATTERN = "^[\\p{L}][\\p{L}\\p{M} .'-]*$";

  /** Maximum length of a permission code. */
  public static final int PERMISSION_CODE_MAX = 50;

  /** Permission codes in upper snake case. */
  public static final String PERMISSION_CODE_PATTERN = "^[A-Z][A-Z0-9_]*$";

  // Organization and network.

  /** Minimum length of an aqueduct name. */
  public static final int AQUEDUCT_NAME_MIN = 3;

  /** Maximum length of an aqueduct name. */
  public static final int AQUEDUCT_NAME_MAX = 100;

  /** Letters, digits, space and basic punctuation. */
  public static final String AQUEDUCT_NAME_PATTERN = "^[\\p{L}\\p{Nd} .,()-]+$";

  /** Minimum length of an aqueduct slug. */
  public static final int AQUEDUCT_SLUG_MIN = 3;

  /** Maximum length of an aqueduct slug. */
  public static final int AQUEDUCT_SLUG_MAX = 40;

  /** Lower case ASCII words joined by single hyphens. */
  public static final String AQUEDUCT_SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

  /** Minimum length of a place name (tank, sector, valve, device). */
  public static final int PLACE_NAME_MIN = 2;

  /** Maximum length of a place name. */
  public static final int PLACE_NAME_MAX = 60;

  /** Letters, digits, space and basic punctuation. */
  public static final String PLACE_NAME_PATTERN = "^[\\p{L}\\p{Nd} .#()-]+$";

  /** Minimum length of a valve code. */
  public static final int VALVE_CODE_MIN = 1;

  /** Maximum length of a valve code. */
  public static final int VALVE_CODE_MAX = 20;

  /** Upper case ASCII letters, digits and hyphens. */
  public static final String VALVE_CODE_PATTERN = "^[A-Z0-9-]+$";

  /** Minimum length of a sector code. */
  public static final int SECTOR_CODE_MIN = 2;

  /** Maximum length of a sector code. */
  public static final int SECTOR_CODE_MAX = 20;

  /** Upper case ASCII letters, digits and hyphens. */
  public static final String SECTOR_CODE_PATTERN = "^[A-Z0-9-]+$";

  /** Maximum length of a location hint (one line). */
  public static final int LOCATION_HINT_MAX = 120;

  // Free text.

  /** Maximum length of a note (readings, closures, resolutions). */
  public static final int NOTE_MAX = 500;

  /** Maximum number of lines in notes and descriptions. */
  public static final int NOTE_MAX_LINES = 10;

  /** Minimum length of an incident description. */
  public static final int INCIDENT_DESCRIPTION_MIN = 10;

  /** Maximum length of an incident description. */
  public static final int INCIDENT_DESCRIPTION_MAX = 500;

  /** Minimum length of a reason (rule changes, corrections, decisions, manual commands). */
  public static final int REASON_MIN = 10;

  /** Maximum length of a reason. */
  public static final int REASON_MAX = 500;

  /** Upper bound of any text field, even when the field allows more. */
  public static final int TEXT_GLOBAL_MAX = 2_000;

  // Catalogs and codes.

  /** Minimum length of a catalog code. */
  public static final int CATALOG_CODE_MIN = 2;

  /** Maximum length of a catalog code. */
  public static final int CATALOG_CODE_MAX = 40;

  /** Upper snake case catalog code. */
  public static final String CATALOG_CODE_PATTERN = "^[A-Z][A-Z0-9_]*$";

  /** Minimum length of a catalog label. */
  public static final int CATALOG_LABEL_MIN = 2;

  /** Maximum length of a catalog label. */
  public static final int CATALOG_LABEL_MAX = 60;

  /** Safe text for catalog labels. */
  public static final String CATALOG_LABEL_PATTERN = "^[\\p{L}\\p{Nd} .,()/'-]+$";

  /** Length of an incident tracking code. */
  public static final int TRACKING_CODE_LENGTH = 8;

  /** Crockford base32 tracking code. */
  public static final String TRACKING_CODE_PATTERN =
      "^[0-9A-HJKMNP-TV-Z]{" + TRACKING_CODE_LENGTH + "}$";

  // Requests.

  /** Maximum number of elements in any list, unless a field says otherwise. */
  public static final int LIST_MAX_ITEMS = 100;

  /** Maximum JSON nesting depth. */
  public static final int JSON_DEPTH_MAX = 10;

  /** Maximum size of a general JSON body, in bytes (64 KiB). */
  public static final int BODY_MAX_BYTES = 65_536;

  /** Maximum size of a telemetry body, in bytes (256 KiB). */
  public static final int TELEMETRY_BODY_MAX_BYTES = 262_144;

  /** Maximum size of an import body, in bytes (1 MiB). */
  public static final int IMPORT_BODY_MAX_BYTES = 1_048_576;

  /** Maximum rows in an import batch. */
  public static final int IMPORT_MAX_ROWS = 5_000;

  /** Maximum points in a telemetry request. */
  public static final int TELEMETRY_BATCH_MAX = 100;

  /** Minimum page size. */
  public static final int PAGE_LIMIT_MIN = 1;

  /** Maximum page size. */
  public static final int PAGE_LIMIT_MAX = 100;

  /** Default page size. */
  public static final int PAGE_LIMIT_DEFAULT = 20;

  /** Maximum size of the {@code Authorization} header, in bytes (2 KiB). */
  public static final int AUTH_HEADER_MAX_BYTES = 2_048;

  // Time and measurements.

  /** Tolerance for instants in the future, in seconds. */
  public static final int FUTURE_TIMESTAMP_SKEW_SECONDS = 300;

  /** Allowed clock difference of a device signature, in seconds (plus or minus). */
  public static final int DEVICE_SIGNATURE_WINDOW_SECONDS = 300;

  /** Time a device nonce stays unique, in seconds. */
  public static final int DEVICE_NONCE_TTL_SECONDS = 600;

  /** Maximum decimals of a gauge value or correction. */
  public static final int GAUGE_DECIMALS_MAX = 2;

  /** Minimum forecast horizon accepted by the API, in days. */
  public static final int FORECAST_HORIZON_DAYS_MIN = 1;

  /** Maximum forecast horizon accepted by the API, in days. */
  public static final int FORECAST_HORIZON_DAYS_MAX = 3;

  private FieldLimits() {}
}
