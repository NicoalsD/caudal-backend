package co.caudal.api.controller;

import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.dto.response.NetworkNodeResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.network.ListNetworkUseCase;
import co.caudal.application.network.NetworkNodeView;
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

/** The water network as a tree (P08 Composite). */
@RestController
@RequestMapping("/api/v1/network")
@Tag(name = "Red", description = "Tanques, sectores y válvulas.")
public class NetworkController {

  private final ListNetworkUseCase network;

  NetworkController(ListNetworkUseCase network) {
    this.network = network;
  }

  @Operation(
      summary = "Árbol de la red",
      description =
          "Sectores de primer nivel con sus válvulas y subsectores, y en cada parte las viviendas"
              + " y las válvulas activas de todo lo que contiene. Un sector inactivo no suma"
              + " viviendas ni válvulas. Requiere el permiso NETWORK_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Árbol de la red")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso NETWORK_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('NETWORK_VIEW')")
  public ResponseEntity<ListResponse<NetworkNodeResponse>> tree(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    var items =
        network.tree(CallerFactory.from(jwt, http)).stream()
            .map(NetworkController::toResponse)
            .toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ListResponse<>(items));
  }

  private static NetworkNodeResponse toResponse(NetworkNodeView node) {
    return new NetworkNodeResponse(
        node.kind(),
        node.id(),
        node.code(),
        node.name(),
        node.active(),
        node.households(),
        node.activeValves(),
        node.children().stream().map(NetworkController::toResponse).toList());
  }
}
