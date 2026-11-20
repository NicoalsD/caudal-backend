package co.caudal.application.autopilot;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.AutopilotMode;
import co.caudal.domain.autopilot.AutopilotSettings;

/**
 * Validates and stores the settings of the autopilot (HU-40). The new values rule from the next
 * cycle.
 *
 * @pattern P13 Command
 */
public final class UpdateSettingsHandler
    implements CommandHandler<UpdateSettingsCommand, SettingsView> {

  private final AutopilotSettingsPort settings;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param settings the settings
   * @param unitOfWork transaction boundary
   */
  public UpdateSettingsHandler(AutopilotSettingsPort settings, UnitOfWorkPort unitOfWork) {
    this.settings = settings;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public SettingsView handle(UpdateSettingsCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          AutopilotSettings current = settings.getOrCreate(command.caller().aqueductId());
          AutopilotSettings changed =
              current.change(
                  AutopilotMode.of(command.mode()),
                  command.operatorAbsenceMinutes(),
                  command.reserveFloorPct());
          return SettingsView.of(
              settings.save(
                  changed.aqueductId(),
                  changed.mode(),
                  changed.operatorAbsenceMinutes(),
                  changed.reserveFloorPct(),
                  command.caller().userId()));
        });
  }

  @Override
  public Class<UpdateSettingsCommand> commandType() {
    return UpdateSettingsCommand.class;
  }
}
