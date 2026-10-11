package co.caudal.api.controller;

import co.caudal.api.dto.request.GenerateMinutesRequest;
import co.caudal.api.dto.response.MinutesResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.minutes.GenerateMinutesCommand;
import co.caudal.application.minutes.GetMinutesUseCase;
import co.caudal.application.minutes.MinutesView;
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

/** Minutes of the Board (docs/API.md, section 13). */
@RestController
@RequestMapping("/api/v1/minutes")
@Tag(name = "Actas", description = "Actas por estatus epistémico y su PDF.")
public class MinutesController {

  private final CommandBus commands;
  private final GetMinutesUseCase get;

  MinutesController(CommandBus commands, GetMinutesUseCase get) {
    this.commands = commands;
    this.get = get;
  }

  @Operation(
      summary = "Generar un acta",
      description =
          "Genera el acta de un periodo en DRAFT, con los registros clasificados en observado,"
              + " estimado, inferido y confirmado. Requiere el permiso MINUTES_GENERATE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Acta generada")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: periodo invertido o largo")
  @PostMapping
  @PreAuthorize("hasAuthority('MINUTES_GENERATE')")
  public ResponseEntity<MinutesResponse> generate(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody GenerateMinutesRequest request,
      HttpServletRequest http) {
    MinutesView minutes =
        commands.dispatch(
            new GenerateMinutesCommand(
                CallerFactory.from(jwt, http), request.periodFrom(), request.periodTo()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(MinutesResponse.of(minutes));
  }

  @Operation(
      summary = "Ver un acta",
      description = "El acta con sus secciones. Requiere el permiso MINUTES_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "El acta")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND")
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('MINUTES_VIEW')")
  public ResponseEntity<MinutesResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MinutesResponse.of(get.execute(CallerFactory.from(jwt, http), id)));
  }
}
