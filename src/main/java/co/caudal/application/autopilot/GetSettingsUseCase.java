package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.application.port.out.UnitOfWorkPort;

/** Reads the settings of the autopilot of the aqueduct of the session. */
public class GetSettingsUseCase {

  private final AutopilotSettingsPort settings;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param settings the settings
   * @param unitOfWork transaction boundary
   */
  public GetSettingsUseCase(AutopilotSettingsPort settings, UnitOfWorkPort unitOfWork) {
    this.settings = settings;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads the settings; an aqueduct that has none yet gets the defaults.
   *
   * @param caller who asks
   * @return the settings
   */
  public SettingsView execute(Caller caller) {
    return unitOfWork.executeAs(
        caller.scope(), () -> SettingsView.of(settings.getOrCreate(caller.aqueductId())));
  }
}
