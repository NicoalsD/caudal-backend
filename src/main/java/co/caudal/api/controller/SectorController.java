package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateSectorRequest;
import co.caudal.api.dto.request.CreateValveRequest;
import co.caudal.api.dto.request.UpdateSectorRequest;
import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.dto.response.SectorResponse;
import co.caudal.api.dto.response.ValveResponse;
import co.caudal.api.mapper.NetworkMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.network.CreateSectorCommand;
import co.caudal.application.network.CreateValveCommand;
import co.caudal.application.network.ListNetworkUseCase;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.UpdateSectorCommand;
import co.caudal.application.network.ValveView;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sectors and valves of the aqueduct of the session. */
@RestController
@RequestMapping("/api/v1/sectors")
@Tag(name = "Red", description = "Tanques, sectores y válvulas.")
public class SectorController {

  private final ListNetworkUseCase network;
  private final CommandBus commands;

  SectorController(ListNetworkUseCase network, CommandBus commands) {
    this.network = network;
    this.commands = commands;
  }

  @Operation(
      summary = "Crear un sector",
      description =
          "Agrega un sector al acueducto de la sesión. El código es único en el acueducto."
              + " Requiere el permiso NETWORK_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Sector creado")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: algún campo no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_MANAGE")
  @ApiResponse(responseCode = "409", description = "CONFLICT: el código ya existe")
  @PostMapping
  @PreAuthorize("hasAuthority('NETWORK_MANAGE')")
  public ResponseEntity<SectorResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateSectorRequest request,
      HttpServletRequest http) {
    SectorView sector =
        commands.dispatch(
            new CreateSectorCommand(
                CallerFactory.from(jwt, http),
                request.code(),
                request.name(),
                request.parentSectorId(),
                request.householdsCount()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(NetworkMapper.toResponse(sector));
  }

  @Operation(
      summary = "Cambiar un sector",
      description =
          "Cambia el nombre, las viviendas o si sigue en uso. El código no cambia. Requiere el"
              + " permiso NETWORK_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Sector actualizado")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_MANAGE")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el sector no existe")
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('NETWORK_MANAGE')")
  public ResponseEntity<SectorResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSectorRequest request,
      HttpServletRequest http) {
    SectorView sector =
        commands.dispatch(
            new UpdateSectorCommand(
                CallerFactory.from(jwt, http),
                id,
                request.name(),
                request.householdsCount(),
                request.isActive()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(NetworkMapper.toResponse(sector));
  }

  @Operation(
      summary = "Listar los sectores",
      description =
          "Sectores del acueducto de la sesión, sin datos personales. Requiere el permiso"
              + " NETWORK_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Lista de sectores")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('NETWORK_VIEW')")
  public ResponseEntity<ListResponse<SectorResponse>> list(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    var items =
        network.sectors(CallerFactory.from(jwt, http)).stream()
            .map(NetworkMapper::toResponse)
            .toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ListResponse<>(items));
  }

  @Operation(
      summary = "Listar las válvulas de un sector",
      description = "Válvulas del sector indicado. Requiere el permiso NETWORK_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Lista de válvulas")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_VIEW")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el sector no existe")
  @GetMapping("/{id}/valves")
  @PreAuthorize("hasAuthority('NETWORK_VIEW')")
  public ResponseEntity<ListResponse<ValveResponse>> valves(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    var items =
        network.valves(CallerFactory.from(jwt, http), id).stream()
            .map(NetworkMapper::toResponse)
            .toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ListResponse<>(items));
  }

  @Operation(
      summary = "Crear una válvula en un sector",
      description =
          "Agrega una válvula al sector indicado. El código es único en el sector. Requiere el"
              + " permiso NETWORK_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Válvula creada")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: algún campo no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_MANAGE")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el sector no existe")
  @ApiResponse(responseCode = "409", description = "CONFLICT: el código ya existe en el sector")
  @PostMapping("/{id}/valves")
  @PreAuthorize("hasAuthority('NETWORK_MANAGE')")
  public ResponseEntity<ValveResponse> createValve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CreateValveRequest request,
      HttpServletRequest http) {
    ValveView valve =
        commands.dispatch(
            new CreateValveCommand(
                CallerFactory.from(jwt, http),
                id,
                request.code(),
                request.name(),
                request.locationHint(),
                request.isMotorized(),
                request.failSafePosition()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(NetworkMapper.toResponse(valve));
  }
}
