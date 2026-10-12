package co.caudal.api.controller;

import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.dto.response.SecurityEventResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.audit.ListSecurityEventsUseCase;
import co.caudal.application.audit.SecurityEventFilter;
import co.caudal.application.audit.SecurityEventView;
import co.caudal.application.pagination.Page;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Security events of the aqueduct: blocked accounts, denied permissions, bad signatures... */
@RestController
@RequestMapping("/api/v1/security-events")
@Tag(name = "Auditoría", description = "Bitácora de auditoría y eventos de seguridad.")
public class SecurityEventController {

  private final ListSecurityEventsUseCase list;

  SecurityEventController(ListSecurityEventsUseCase list) {
    this.list = list;
  }

  static SecurityEventResponse toResponse(SecurityEventView event) {
    return new SecurityEventResponse(
        event.id(),
        event.type(),
        event.severity(),
        event.actorUserId(),
        event.deviceId(),
        event.requestId(),
        event.createdAt(),
        event.details());
  }

  @Operation(
      summary = "Consultar los eventos de seguridad",
      description =
          "Eventos de seguridad del acueducto de la sesión, del más reciente al más antiguo."
              + " Filtros opcionales type, severity, from y to (momento de creación). Se pagina"
              + " con limit y un cursor firmado. Nunca devuelve el HMAC de la IP. Los eventos sin"
              + " acueducto no se muestran aquí. Cada consulta queda en el registro de accesos a"
              + " datos. Requiere el permiso AUDIT_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de eventos de seguridad")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso AUDIT_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('AUDIT_VIEW')")
  public ResponseEntity<PageResponse<SecurityEventResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.SECURITY_EVENT_TYPE_PATTERN)
          String type,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.SECURITY_EVENT_SEVERITY_PATTERN)
          String severity,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<SecurityEventView> page =
        list.execute(
            CallerFactory.from(jwt, http),
            new SecurityEventFilter(type, severity, from, to),
            limit,
            cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(SecurityEventController::toResponse).toList(),
                page.nextCursor()));
  }
}
