package co.caudal.api.controller;

import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.dto.response.LiveValveResponse;
import co.caudal.api.mapper.AutopilotMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.autopilot.ListLiveValvesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The live state of the valves for the 3D scene. */
@RestController
@RequestMapping("/api/v1/valves")
@Tag(name = "Red", description = "Tanques, sectores y válvulas.")
public class LiveValveController {

  private final ListLiveValvesUseCase live;

  LiveValveController(ListLiveValvesUseCase live) {
    this.live = live;
  }

  @Operation(
      summary = "Estado en vivo de las llaves",
      description =
          "Para cada llave activa: su estado (OPEN, CLOSED o UNKNOWN según el último comando"
              + " confirmado por el actuador) y su último comando con el origen (SCHEDULE,"
              + " MANUAL o AUTOPILOT). La escena 3D lo consulta cada 2 segundos. Requiere el"
              + " permiso NETWORK_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Estado de las llaves")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_VIEW")
  @GetMapping("/live")
  @PreAuthorize("hasAuthority('NETWORK_VIEW')")
  public ResponseEntity<ListResponse<LiveValveResponse>> live(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new ListResponse<>(
                live.execute(CallerFactory.from(jwt, http)).stream()
                    .map(AutopilotMapper::toResponse)
                    .toList()));
  }
}
