package co.caudal.shared;

/**
 * Physical constants with a name. They are not business parameters and never change with the rules
 * of the Board. Like {@link FieldLimits}, each one is also a Flyway placeholder in lower case
 * ({@code ${hours_per_day}}).
 */
public final class PhysicalConstants {

  /** Hours in a day. */
  public static final int HOURS_PER_DAY = 24;

  /** Days in a week (ISO numbering 1 to 7). */
  public static final int DAYS_PER_WEEK = 7;

  private PhysicalConstants() {}
}
