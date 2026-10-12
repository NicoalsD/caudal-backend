package co.caudal.api.controller;

import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.dto.response.SummaryResponse;
import co.caudal.api.dto.response.SummarySectionsResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.pagination.Page;
import co.caudal.application.summary.ListSummariesUseCase;
import co.caudal.application.summary.SummaryView;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Summaries shared with entities of support (docs/API.md, section 13.6). */
@RestController
@RequestMapping("/api/v1/summaries")
@Tag(name = "Actas", description = "Actas, resúmenes y autorizaciones para entidades de apoyo.")
public class SummaryController {

  private final ListSummariesUseCase list;

  SummaryController(ListSummariesUseCase list) {
    this.list = list;
  }

  static SummaryResponse toResponse(SummaryView summary) {
    SummarySectionsResponse content =
        new SummarySectionsResponse(
            summary.content().observed(),
            summary.content().estimated(),
            summary.content().inferred(),
            summary.content().confirmed());
    return new SummaryResponse(
        summary.summaryShareId(),
        summary.scope(),
        summary.periodFrom(),
        summary.periodTo(),
        content);
  }

  @Operation(
      summary = "Listar los resúmenes autorizados",
      description =
          "Resúmenes que la Junta autorizó con vigencia: sin revocar, no vencidos y, para una"
              + " entidad de apoyo, con membresía vigente. Solo trae cifras por sección, nunca"
              + " texto de los registros. Con period_from y period_to, el periodo pedido debe"
              + " caber en el de la autorización; si no, se omite. Cada resumen que lee una entidad"
              + " queda en el registro de accesos. Requiere el permiso SUMMARY_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de resúmenes")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @GetMapping
  @PreAuthorize("hasAuthority('SUMMARY_VIEW')")
  public ResponseEntity<PageResponse<SummaryResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "period_from", required = false) LocalDate periodFrom,
      @RequestParam(name = "period_to", required = false) LocalDate periodTo,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<SummaryView> page =
        list.execute(CallerFactory.from(jwt, http), periodFrom, periodTo, limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(SummaryController::toResponse).toList(),
                page.nextCursor()));
  }
}
