package co.caudal.api.controller;

import co.caudal.api.dto.response.DayScheduleResponse;
import co.caudal.api.mapper.ScheduleMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.schedule.GetDayScheduleUseCase;
import co.caudal.application.schedule.PublishedDay;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The published schedule of a day, with the ids of its shifts for the execution (docs/API.md,
 * section 11.4).
 */
@RestController
@Tag(name = "Ejecución y cierre", description = "Ejecución real de los turnos y cierre del día.")
public class DayScheduleController {

  private final GetDayScheduleUseCase schedule;

  DayScheduleController(GetDayScheduleUseCase schedule) {
    this.schedule = schedule;
  }

  @Operation(
      summary = "Ver el horario publicado de un día",
      description =
          "Devuelve solo el horario publicado del día (service_date en la zona horaria del"
              + " acueducto): sus turnos con los ids que usa el registro de ejecución. Un borrador"
              + " o una propuesta pendiente no se muestra. Requiere el permiso SCHEDULE_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "El horario publicado del día")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: la fecha no tiene el formato YYYY-MM-DD")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: el día no tiene horario publicado en el acueducto")
  @GetMapping("/api/v1/days/{date}/schedule")
  @PreAuthorize("hasAuthority('SCHEDULE_VIEW')")
  public ResponseEntity<DayScheduleResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date, HttpServletRequest http) {
    PublishedDay day = schedule.execute(CallerFactory.from(jwt, http), date);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new DayScheduleResponse(
                date,
                day.proposal().summary().id(),
                day.publishedAt(),
                day.proposal().items().stream().map(ScheduleMapper::toItem).toList()));
  }
}
