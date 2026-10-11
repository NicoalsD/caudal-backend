package co.caudal.api.controller;

import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.dto.response.RuleSetResponse;
import co.caudal.api.dto.response.RuleSetSummaryResponse;
import co.caudal.api.dto.response.RuleSetVersionResponse;
import co.caudal.api.mapper.RuleSetMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.pagination.Page;
import co.caudal.application.rules.GetCurrentRuleSetUseCase;
import co.caudal.application.rules.GetRuleSetUseCase;
import co.caudal.application.rules.ListRuleSetsUseCase;
import co.caudal.application.rules.RuleSetSummary;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reading of the Board's rules (permission {@code RULESET_VIEW}). */
@RestController
@RequestMapping("/api/v1/rule-sets")
@Tag(name = "Reglas de la Junta", description = "Versiones de las reglas de operación.")
public class RuleSetController {

  private final GetCurrentRuleSetUseCase current;
  private final ListRuleSetsUseCase list;
  private final GetRuleSetUseCase get;

  RuleSetController(
      GetCurrentRuleSetUseCase current, ListRuleSetsUseCase list, GetRuleSetUseCase get) {
    this.current = current;
    this.list = list;
    this.get = get;
  }

  @Operation(
      summary = "Ver una versión de las reglas",
      description =
          "Versión completa del acueducto de la sesión, como en /rule-sets/current, con su"
              + " historia: status, based_on_rule_set_id, change_reason, created_by y activated_by."
              + " Requiere el permiso RULESET_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "La versión")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso RULESET_VIEW")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: la versión no existe o es de otro acueducto")
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('RULESET_VIEW')")
  public ResponseEntity<RuleSetVersionResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(RuleSetMapper.toResponse(get.execute(CallerFactory.from(jwt, http), id)));
  }

  @Operation(
      summary = "Listar las versiones de las reglas",
      description =
          "Versiones del acueducto de la sesión, de la más nueva a la más antigua. Filtro"
              + " opcional status (DRAFT, ACTIVE, SUPERSEDED o DISCARDED). Se pagina con limit"
              + " (1 a 100, por defecto 20) y un cursor firmado. Requiere el permiso RULESET_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de versiones")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso RULESET_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('RULESET_VIEW')")
  public ResponseEntity<PageResponse<RuleSetSummaryResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.RULE_SET_STATUS_PATTERN)
          String status,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<RuleSetSummary> page = list.execute(CallerFactory.from(jwt, http), status, limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(RuleSetMapper::toResponse).toList(), page.nextCursor()));
  }

  @Operation(
      summary = "Reglas vigentes",
      description =
          "Devuelve la versión ACTIVE de las reglas del acueducto de la sesión, completa: bandas"
              + " de nivel, ajustes de sectores, orden de válvulas, ventanas de operación y la"
              + " regla pintada del tanque. La app la usa para validar lecturas sin conexión."
              + " Requiere el permiso RULESET_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Reglas vigentes")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso RULESET_VIEW")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: no hay una versión activa")
  @GetMapping("/current")
  @PreAuthorize("hasAuthority('RULESET_VIEW')")
  public ResponseEntity<RuleSetResponse> current(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(RuleSetMapper.toResponse(current.execute(CallerFactory.from(jwt, http))));
  }
}
