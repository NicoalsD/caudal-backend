package co.caudal.api.controller;

import co.caudal.api.dto.response.EvaluationResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.facade.EvaluationView;
import co.caudal.application.facade.ForecastEvaluationFacade;
import co.caudal.application.publication.DemoMark;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Evaluation of the AI against the simple estimate (docs/API.md, section 14). */
@RestController
@Tag(name = "Evaluación", description = "Si la IA le gana a la estimación simple.")
public class EvaluationController {

  private final ForecastEvaluationFacade evaluation;

  EvaluationController(ForecastEvaluationFacade evaluation) {
    this.evaluation = evaluation;
  }

  @Operation(
      summary = "Evaluar la IA",
      description =
          "Compara el pronóstico de la IA con la estimación simple sobre los mismos días reales:"
              + " MAE, WQL, cobertura, ancho y skill. Responde si la IA ayuda solo con 30"
              + " pronósticos o más y cobertura entre 70 % y 90 %. Por defecto, los últimos 90"
              + " días. Requiere el permiso EVALUATION_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "La evaluación")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: horizonte o periodo")
  @GetMapping("/api/v1/forecast-evaluation")
  @PreAuthorize("hasAuthority('EVALUATION_VIEW')")
  public ResponseEntity<EvaluationResponse> evaluate(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "horizon_days", defaultValue = "1")
          @Min(FieldLimits.FORECAST_HORIZON_DAYS_MIN)
          @Max(FieldLimits.FORECAST_HORIZON_DAYS_MAX)
          int horizonDays,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      HttpServletRequest http) {
    EvaluationView v = evaluation.evaluate(CallerFactory.from(jwt, http), horizonDays, from, to);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new EvaluationResponse(
                v.horizonDays(),
                v.from(),
                v.to(),
                v.n(),
                v.maeIa(),
                v.maeSimple(),
                v.wqlIa(),
                v.coverage80(),
                v.avgIntervalWidth(),
                v.skill(),
                v.helps(),
                v.verdict(),
                v.simulated(),
                DemoMark.banner(v.simulated())));
  }
}
