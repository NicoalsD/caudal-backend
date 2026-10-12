package co.caudal.api.controller;

import co.caudal.api.dto.response.AuditLogResponse;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.audit.AuditLogFilter;
import co.caudal.application.audit.AuditLogView;
import co.caudal.application.audit.ListAuditLogUseCase;
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
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log of the aqueduct: who changed what and when (docs/API.md, section 18.1). */
@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "Auditoría", description = "Bitácora de auditoría y eventos de seguridad.")
public class AuditLogController {

  private final ListAuditLogUseCase list;

  AuditLogController(ListAuditLogUseCase list) {
    this.list = list;
  }

  static AuditLogResponse toResponse(AuditLogView entry) {
    return new AuditLogResponse(
        entry.id(),
        entry.occurredAt(),
        entry.actorType(),
        entry.actorId(),
        entry.action(),
        entry.entityType(),
        entry.entityId(),
        entry.beforeState(),
        entry.afterState(),
        entry.prevHash(),
        entry.rowHash());
  }

  @Operation(
      summary = "Consultar la bitácora de auditoría",
      description =
          "Acciones sensibles del acueducto de la sesión, de la más reciente a la más antigua."
              + " Filtros opcionales from, to (momento), action, entity_type y actor_id. Se pagina"
              + " con limit y un cursor firmado. Nunca devuelve el identificador de la petición ni"
              + " el HMAC de la IP; los estados van redactados. Cada consulta queda en el registro"
              + " de accesos a datos. Requiere el permiso AUDIT_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de entradas de la bitácora")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso AUDIT_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('AUDIT_VIEW')")
  @SuppressWarnings("checkstyle:ParameterNumber")
  public ResponseEntity<PageResponse<AuditLogResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.AUDIT_ACTION_PATTERN)
          String action,
      @RequestParam(name = "entity_type", required = false)
          @Pattern(regexp = FieldLimits.AUDIT_ENTITY_TYPE_PATTERN)
          String entityType,
      @RequestParam(name = "actor_id", required = false) UUID actorId,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<AuditLogView> page =
        list.execute(
            CallerFactory.from(jwt, http),
            new AuditLogFilter(from, to, action, entityType, actorId),
            limit,
            cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(AuditLogController::toResponse).toList(),
                page.nextCursor()));
  }
}
