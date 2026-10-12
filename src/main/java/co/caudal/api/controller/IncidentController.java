package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateIncidentRequest;
import co.caudal.api.dto.request.UpdateIncidentStatusRequest;
import co.caudal.api.dto.response.IncidentResponse;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.incident.CreateIncidentCommand;
import co.caudal.application.incident.IncidentFilter;
import co.caudal.application.incident.IncidentView;
import co.caudal.application.incident.ListIncidentsUseCase;
import co.caudal.application.incident.UpdateIncidentStatusCommand;
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
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Incidents: damage reports of the aqueduct (docs/API.md, section 12). */
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidentes", description = "Reportes de daño del acueducto y su estado.")
public class IncidentController {

  private final CommandBus commands;
  private final ListIncidentsUseCase list;

  IncidentController(CommandBus commands, ListIncidentsUseCase list) {
    this.commands = commands;
    this.list = list;
  }

  static IncidentResponse toResponse(IncidentView incident) {
    return new IncidentResponse(
        incident.id(),
        incident.source(),
        incident.categoryCode(),
        incident.sectorId(),
        incident.description(),
        incident.locationHint(),
        incident.status(),
        incident.reportedAt());
  }

  @Operation(
      summary = "Listar los incidentes",
      description =
          "Incidentes del acueducto de la sesión, del más reciente al más antiguo. Filtros"
              + " opcionales status, source, sector_id, from y to (fecha de reporte). Se pagina"
              + " con limit y un cursor firmado. Nunca devuelve datos del reportante. Requiere el"
              + " permiso INCIDENT_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de incidentes")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @GetMapping
  @PreAuthorize("hasAuthority('INCIDENT_VIEW')")
  @SuppressWarnings("checkstyle:ParameterNumber")
  public ResponseEntity<PageResponse<IncidentResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.INCIDENT_STATUS_PATTERN)
          String status,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.INCIDENT_SOURCE_PATTERN)
          String source,
      @RequestParam(name = "sector_id", required = false) UUID sectorId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<IncidentView> page =
        list.execute(
            CallerFactory.from(jwt, http),
            new IncidentFilter(status, source, sectorId, from, to),
            limit,
            cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(IncidentController::toResponse).toList(),
                page.nextCursor()));
  }

  @Operation(
      summary = "Registrar un daño desde el panel",
      description =
          "Registra un daño con origen OPERATOR y estado REPORTED. La categoría sale del catálogo"
              + " DAMAGE_CATEGORY; el sector, si se envía, debe ser del acueducto. La descripción"
              + " va de 10 a 500 caracteres y el lugar hasta 120. Requiere el permiso"
              + " INCIDENT_CREATE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Incidente registrado")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: categoría, sector o texto no válidos")
  @PostMapping
  @PreAuthorize("hasAuthority('INCIDENT_CREATE')")
  public ResponseEntity<IncidentResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateIncidentRequest request,
      HttpServletRequest http) {
    IncidentView incident =
        commands.dispatch(
            new CreateIncidentCommand(
                CallerFactory.from(jwt, http),
                request.categoryCode(),
                request.sectorId(),
                request.description(),
                request.locationHint()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(toResponse(incident));
  }

  @Operation(
      summary = "Cambiar el estado de un incidente",
      description =
          "Mueve el incidente según las transiciones de las reglas: REPORTED a VERIFYING o"
              + " DISMISSED; VERIFYING a CONFIRMED o DISMISSED; CONFIRMED a RESOLVED. Descartar"
              + " exige una nota de 10 a 500 caracteres. Cada cambio queda en el historial del"
              + " incidente. Requiere el permiso INCIDENT_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Incidente actualizado")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el incidente no existe")
  @ApiResponse(
      responseCode = "409",
      description = "INVALID_STATE_TRANSITION: transición no permitida; CONFLICT si cambió antes")
  @ApiResponse(responseCode = "422", description = "REASON_REQUIRED: descarte sin nota válida")
  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
  public ResponseEntity<IncidentResponse> changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateIncidentStatusRequest request,
      HttpServletRequest http) {
    IncidentView incident =
        commands.dispatch(
            new UpdateIncidentStatusCommand(
                CallerFactory.from(jwt, http), id, request.status(), request.note()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toResponse(incident));
  }
}
