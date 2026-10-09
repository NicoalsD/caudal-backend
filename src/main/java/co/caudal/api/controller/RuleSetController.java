package co.caudal.api.controller;

import co.caudal.api.dto.response.RuleSetResponse;
import co.caudal.api.mapper.RuleSetMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.rules.GetCurrentRuleSetUseCase;
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

/** Reading of the Board's rules (permission {@code RULESET_VIEW}). */
@RestController
@RequestMapping("/api/v1/rule-sets")
@Tag(name = "Reglas de la Junta", description = "Versiones de las reglas de operación.")
public class RuleSetController {

  private final GetCurrentRuleSetUseCase current;

  RuleSetController(GetCurrentRuleSetUseCase current) {
    this.current = current;
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
