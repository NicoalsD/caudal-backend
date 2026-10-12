package co.caudal.api.controller;

import co.caudal.api.dto.request.ReviewAnomalyRequest;
import co.caudal.api.dto.response.AnomalyResponse;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.anomaly.AnomalyView;
import co.caudal.application.anomaly.ListAnomaliesUseCase;
import co.caudal.application.anomaly.ReviewAnomalyCommand;
import co.caudal.application.command.CommandBus;
import co.caudal.application.pagination.Page;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anomalies: suspicions inferred by rules (docs/API.md, section 9). */
@RestController
@RequestMapping("/api/v1/anomalies")
@Tag(name = "Estado y pronóstico", description = "Estado del tanque, pronóstico y anomalías.")
public class AnomalyController {

  private final CommandBus commands;
  private final ListAnomaliesUseCase list;

  AnomalyController(CommandBus commands, ListAnomaliesUseCase list) {
    this.commands = commands;
    this.list = list;
  }

  static AnomalyResponse toResponse(AnomalyView a) {
    return new AnomalyResponse(
        a.id(),
        a.kind(),
        a.detectedAt(),
        a.status(),
        a.evidence(),
        a.resolvedBy(),
        a.resolvedAt(),
        a.resolutionNote(),
        a.relatedIncidentId());
  }

  @Operation(
      summary = "Listar las anomalías",
      description =
          "Anomalías del acueducto de la sesión, de la más nueva a la más antigua. Filtros"
              + " opcionales status y kind. Se pagina con limit y un cursor firmado. Requiere el"
              + " permiso STATUS_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de anomalías")
  @GetMapping
  @PreAuthorize("hasAuthority('STATUS_VIEW')")
  public ResponseEntity<PageResponse<AnomalyResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.ANOMALY_STATUS_PATTERN)
          String status,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.ANOMALY_KIND_PATTERN)
          String kind,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<AnomalyView> page =
        list.execute(CallerFactory.from(jwt, http), status, kind, limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(AnomalyController::toResponse).toList(),
                page.nextCursor()));
  }

  @Operation(
      summary = "Confirmar o descartar una anomalía",
      description =
          "Solo una anomalía OPEN cambia. Descartarla exige una nota de 10 a 500 caracteres."
              + " Puede vincularse a un incidente del acueducto. Requiere el permiso"
              + " ANOMALY_REVIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Anomalía actualizada")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la anomalía no existe")
  @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION: no está OPEN")
  @ApiResponse(responseCode = "422", description = "REASON_REQUIRED: descarte sin motivo")
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('ANOMALY_REVIEW')")
  public ResponseEntity<AnomalyResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ReviewAnomalyRequest request,
      HttpServletRequest http) {
    AnomalyView anomaly =
        commands.dispatch(
            new ReviewAnomalyCommand(
                CallerFactory.from(jwt, http),
                id,
                request.status(),
                request.resolutionNote(),
                request.relatedIncidentId()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toResponse(anomaly));
  }
}
