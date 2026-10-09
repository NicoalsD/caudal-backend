package co.caudal.api.controller;

import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.dto.response.SectorResponse;
import co.caudal.api.dto.response.ValveResponse;
import co.caudal.api.mapper.NetworkMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.network.ListNetworkUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sectors and valves of the aqueduct of the session. */
@RestController
@RequestMapping("/api/v1/sectors")
@Tag(name = "Red", description = "Tanques, sectores y válvulas.")
public class SectorController {

  private final ListNetworkUseCase network;

  SectorController(ListNetworkUseCase network) {
    this.network = network;
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
}
