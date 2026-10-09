package co.caudal.shared;

/**
 * Physical constants with a name. They are not business parameters and never change with the rules
 * of the Board. Like {@link FieldLimits}, each one is also a Flyway placeholder in lower case
 * ({@code ${hours_per_day}}).
 */
public final class PhysicalConstants {

  /** Hours in a day. */
  public static final int HOURS_PER_DAY = 24;

  private PhysicalConstants() {}
}
