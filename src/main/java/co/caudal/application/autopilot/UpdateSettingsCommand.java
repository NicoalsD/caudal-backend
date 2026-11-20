package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Changes the mode and the parameters of the autopilot of the aqueduct.
 *
 * @param caller who changes them; needs the permission {@code AUTOPILOT_CONFIGURE}
 * @param mode {@code MANUAL}, {@code ASSISTED} or {@code AUTOMATIC}
 * @param operatorAbsenceMinutes minutes without human action before AUTOMATIC acts
 * @param reserveFloorPct reserve floor, in percent
 */
public record UpdateSettingsCommand(
    Caller caller, String mode, int operatorAbsenceMinutes, BigDecimal reserveFloorPct)
    implements AuditableCommand<SettingsView> {

  @Override
  public String auditEntityType() {
    return "autopilot_settings";
  }

  @Override
  public String auditEntityId(SettingsView result) {
    return caller.aqueductId().toString();
  }

  @Override
  public String auditAction() {
    return "AUTOPILOT_SETTINGS_CHANGED";
  }

  @Override
  public Map<String, Object> auditAfterState(SettingsView result) {
    return Map.of(
        "mode", result.mode(),
        "operator_absence_minutes", result.operatorAbsenceMinutes(),
        "reserve_floor_pct", result.reserveFloorPct().toPlainString());
  }
}
