package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateManualValveCommandRequest;
import co.caudal.api.dto.response.ValveCommandResponse;
import co.caudal.api.mapper.DeviceCommandMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.device.command.CreateManualValveCommand;
import co.caudal.application.device.command.ValveCommandView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual commands for the valves (docs/API.md, section 16.6). */
@RestController
@RequestMapping("/api/v1/valve-commands")
@Tag(name = "Dispositivos", description = "Sensores, actuadores y gateways firmados.")
public class ManualValveCommandController {

  private final CommandBus commands;

  ManualValveCommandController(CommandBus commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Enviar un comando manual a una válvula",
      description =
          "Crea un comando OPEN (abrir) o CLOSE (cerrar) en estado PENDING para una válvula"
              + " motorizada del acueducto que tenga un actuador activo. El motivo es obligatorio"
              + " (de 10 a 500 caracteres) y queda en la auditoría con el usuario que lo envió."
              + " not_before debe ser anterior a expires_at. No puede haber otro comando activo"
              + " (PENDING o DELIVERED) de la misma válvula con el mismo not_before. Requiere el"
              + " permiso VALVE_COMMAND_MANUAL (solo BOARD_ADMIN).")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Comando creado en estado PENDING")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR: algún campo no es válido o expires_at no es posterior a"
              + " not_before")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso VALVE_COMMAND_MANUAL")
  @ApiResponse(
      responseCode = "404",
      description =
          "NOT_FOUND: la válvula no existe, es de otro acueducto, no es motorizada o no"
              + " tiene un actuador activo")
  @ApiResponse(
      responseCode = "409",
      description = "CONFLICT: ya hay un comando activo de la válvula con ese not_before")
  @ApiResponse(
      responseCode = "422",
      description = "REASON_REQUIRED: falta el motivo o tiene menos de 10 o más de 500 caracteres")
  @PostMapping("/manual")
  @PreAuthorize("hasAuthority('VALVE_COMMAND_MANUAL')")
  public ResponseEntity<ValveCommandResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateManualValveCommandRequest request,
      HttpServletRequest http) {
    ValveCommandView command =
        commands.dispatch(
            new CreateManualValveCommand(
                CallerFactory.from(jwt, http),
                request.valveId(),
                request.command(),
                request.manualReason(),
                request.notBefore(),
                request.expiresAt()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(DeviceCommandMapper.toStatusResponse(command));
  }
}
