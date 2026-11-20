package co.caudal.api.controller;

import co.caudal.api.dto.request.NarrativeReportRequest;
import co.caudal.api.dto.response.NarrativeReportResponse;
import co.caudal.api.dto.response.ReportResponse;
import co.caudal.api.mapper.AutopilotMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.autopilot.ReportUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports of a period: the numbers and the narrative written by the assistant. */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reportes", description = "Datos del periodo y reporte narrativo.")
public class ReportController {

  private final ReportUseCases reports;

  ReportController(ReportUseCases reports) {
    this.reports = reports;
  }

  @Operation(
      summary = "Datos de un periodo",
      description =
          "Agrega el nivel del tanque, los turnos por sector, los comandos de llave por origen,"
              + " las decisiones del piloto, los incidentes y las anomalías entre from"
              + " (inclusive) y to (exclusive). El periodo dura como máximo 92 días. Requiere el"
              + " permiso REPORT_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Datos del periodo")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: el periodo no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso REPORT_VIEW")
  @GetMapping
  @PreAuthorize("hasAuthority('REPORT_VIEW')")
  public ResponseEntity<ReportResponse> aggregates(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam Instant from,
      @RequestParam Instant to,
      HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AutopilotMapper.toResponse(
                reports.aggregates(CallerFactory.from(jwt, http), from, to)));
  }

  @Operation(
      summary = "Reporte narrativo de un periodo",
      description =
          "Pide al asistente un reporte en español (título, resumen, hallazgos, recomendaciones y"
              + " Markdown) con los datos del periodo. Si el servicio no responde, el reporte"
              + " sale de una plantilla (source RULES). Requiere el permiso REPORT_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Reporte narrativo")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: el periodo no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso REPORT_VIEW")
  @PostMapping("/narrative")
  @PreAuthorize("hasAuthority('REPORT_VIEW')")
  public ResponseEntity<NarrativeReportResponse> narrative(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody NarrativeReportRequest request,
      HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AutopilotMapper.toResponse(
                reports.narrative(CallerFactory.from(jwt, http), request.from(), request.to())));
  }
}
