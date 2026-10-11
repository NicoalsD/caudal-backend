package co.caudal.api.controller;

import co.caudal.api.dto.response.PublicScheduleResponse;
import co.caudal.application.publication.GetPublicScheduleUseCase;
import co.caudal.application.publication.PublicSchedule;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The public page of an aqueduct, without login (docs/API.md, section 15). */
@RestController
@RequestMapping("/api/v1/public/{aqueductSlug}")
@Tag(name = "Público", description = "Horario publicado y reportes de daños, sin iniciar sesión.")
public class PublicScheduleController {

  private static final Duration MAX_AGE = Duration.ofMinutes(1);

  private final GetPublicScheduleUseCase schedule;

  PublicScheduleController(GetPublicScheduleUseCase schedule) {
    this.schedule = schedule;
  }

  static PublicScheduleResponse toResponse(PublicSchedule s, String banner) {
    return new PublicScheduleResponse(
        new PublicScheduleResponse.Aqueduct(s.aqueductName(), s.demo(), banner),
        s.serviceDate(),
        new PublicScheduleResponse.Publication(s.publicationId(), s.publishedAt()),
        s.lines().stream()
            .map(l -> new PublicScheduleResponse.Item(l.sectorName(), l.startAt(), l.endAt()))
            .toList());
  }

  @Operation(
      summary = "Ver el horario publicado",
      description =
          "Horario publicado de un día (por defecto hoy en la zona del acueducto): sector, inicio"
              + " y fin de cada turno. No muestra nombres, teléfonos, niveles ni motivos. No"
              + " requiere sesión.")
  @ApiResponse(responseCode = "200", description = "El horario")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: acueducto inexistente, página desactivada o sin publicación")
  @GetMapping("/schedule")
  public ResponseEntity<PublicScheduleResponse> schedule(
      @PathVariable
          @Size(min = FieldLimits.AQUEDUCT_SLUG_MIN, max = FieldLimits.AQUEDUCT_SLUG_MAX)
          @Pattern(regexp = FieldLimits.AQUEDUCT_SLUG_PATTERN)
          String aqueductSlug,
      @RequestParam(required = false) LocalDate date) {
    PublicSchedule found = schedule.execute(aqueductSlug, date);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
        .body(toResponse(found, null));
  }
}
