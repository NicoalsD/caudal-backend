package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateTankRequest;
import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.dto.response.TankResponse;
import co.caudal.api.dto.response.TankStatusResponse;
import co.caudal.api.mapper.ForecastMapper;
import co.caudal.api.mapper.NetworkMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.facade.TankStatusFacade;
import co.caudal.application.network.CreateTankCommand;
import co.caudal.application.network.ListNetworkUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tanks of the aqueduct of the session. */
@RestController
@RequestMapping("/api/v1/tanks")
@Tag(name = "Red", description = "Tanques, sectores y válvulas.")
public class TankController {

  private final ListNetworkUseCase network;
  private final CommandBus commands;
  private final TankStatusFacade status;

  TankController(ListNetworkUseCase network, CommandBus commands, TankStatusFacade status) {
    this.network = network;
    this.commands = commands;
    this.status = status;
  }

  @Operation(
      summary = "Estado del tanque",
      description =
          "Último nivel efectivo y hace cuántas horas, tendencia por mínimos cuadrados de los"
              + " últimos días (viene bajando, estable o sube), si el dato está viejo según"
              + " stale_reading_hours, banda de la regla vigente, último pronóstico mostrado y"
              + " anomalías abiertas. Un dato viejo abre una vez la anomalía STALE_DATA. Requiere"
              + " el permiso STATUS_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Estado del tanque")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el tanque no existe")
  @GetMapping("/{id}/status")
  @PreAuthorize("hasAuthority('STATUS_VIEW')")
  public ResponseEntity<TankStatusResponse> status(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ForecastMapper.toResponse(status.status(CallerFactory.from(jwt, http), id)));
  }

  @Operation(
      summary = "Listar los tanques",
      description =
          "Tanques del acueducto de la sesión con el rango de su regla pintada. Requiere el"
              + " permiso NETWORK_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Lista de tanques")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('NETWORK_VIEW')")
  public ResponseEntity<ListResponse<TankResponse>> list(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    var items =
        network.tanks(CallerFactory.from(jwt, http)).stream()
            .map(NetworkMapper::toResponse)
            .toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ListResponse<>(items));
  }

  @Operation(
      summary = "Crear un tanque",
      description =
          "Crea un tanque con el rango de su regla pintada, que es la fuente del rango válido de"
              + " las lecturas. Requiere el permiso NETWORK_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Tanque creado")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR: algún campo no es válido (por ejemplo gauge_max <= gauge_min)")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_MANAGE")
  @PostMapping
  @PreAuthorize("hasAuthority('NETWORK_MANAGE')")
  public ResponseEntity<TankResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateTankRequest request,
      HttpServletRequest http) {
    TankResponse body =
        NetworkMapper.toResponse(
            commands.dispatch(
                new CreateTankCommand(
                    CallerFactory.from(jwt, http),
                    request.name(),
                    request.gaugeMin(),
                    request.gaugeMax(),
                    request.gaugeStep(),
                    request.capacityLiters())));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(body);
  }
}
