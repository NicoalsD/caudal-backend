package co.caudal.api.controller;

import co.caudal.api.dto.request.UpdateValveRequest;
import co.caudal.api.dto.response.ValveResponse;
import co.caudal.api.mapper.NetworkMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.network.UpdateValveCommand;
import co.caudal.application.network.ValveView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Changes of a valve (docs/API.md, section 5.8). */
@RestController
@RequestMapping("/api/v1/valves")
@Tag(name = "Red", description = "Tanques, sectores y válvulas.")
public class ValveController {

  private final CommandBus commands;

  ValveController(CommandBus commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Cambiar una válvula",
      description =
          "Cambia el nombre, la ubicación, si es motorizada, la posición de seguridad o si sigue"
              + " en uso. El código no cambia. Requiere el permiso NETWORK_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Válvula actualizada")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: algún campo no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_MANAGE")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: la válvula no existe o es de otro acueducto")
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('NETWORK_MANAGE')")
  public ResponseEntity<ValveResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateValveRequest request,
      HttpServletRequest http) {
    ValveView valve =
        commands.dispatch(
            new UpdateValveCommand(
                CallerFactory.from(jwt, http),
                id,
                request.name(),
                request.locationHint(),
                request.isMotorized(),
                request.failSafePosition(),
                request.isActive()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(NetworkMapper.toResponse(valve));
  }
}
