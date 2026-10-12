package co.caudal.api.mapper;

import co.caudal.api.dto.response.ValveCommandItemResponse;
import co.caudal.api.dto.response.ValveCommandResponse;
import co.caudal.api.dto.response.ValveCommandsResponse;
import co.caudal.application.device.command.ValveCommandView;
import java.util.List;

/** Maps the valve commands to their answer (docs/API.md, section 16.2). */
public final class DeviceCommandMapper {

  private DeviceCommandMapper() {}

  /**
   * Maps the commands handed to a device.
   *
   * @param commands the commands
   * @return the answer
   */
  public static ValveCommandsResponse toResponse(List<ValveCommandView> commands) {
    return new ValveCommandsResponse(commands.stream().map(DeviceCommandMapper::item).toList());
  }

  private static ValveCommandItemResponse item(ValveCommandView command) {
    return new ValveCommandItemResponse(
        command.id(),
        command.valveId(),
        command.command(),
        command.notBefore(),
        command.expiresAt());
  }

  /**
   * Maps a command after its confirmation.
   *
   * @param command the command with its new status
   * @return the answer
   */
  public static ValveCommandResponse toStatusResponse(ValveCommandView command) {
    return new ValveCommandResponse(
        command.id(),
        command.valveId(),
        command.command(),
        command.status(),
        command.notBefore(),
        command.expiresAt());
  }
}
