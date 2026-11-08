package co.caudal.application.autopilot;

import co.caudal.domain.autopilot.AutopilotSettings;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The settings of the autopilot as the API shows them.
 *
 * @param mode {@code MANUAL}, {@code ASSISTED} or {@code AUTOMATIC}
 * @param operatorAbsenceMinutes minutes without human action before AUTOMATIC acts
 * @param reserveFloorPct reserve floor, in percent
 * @param updatedAt when they last changed
 */
public record SettingsView(
    String mode, int operatorAbsenceMinutes, BigDecimal reserveFloorPct, Instant updatedAt) {

  /**
   * Shows the settings.
   *
   * @param settings the settings
   * @return the view
   */
  public static SettingsView of(AutopilotSettings settings) {
    return new SettingsView(
        settings.mode().name(),
        settings.operatorAbsenceMinutes(),
        settings.reserveFloorPct(),
        settings.updatedAt());
  }
}
