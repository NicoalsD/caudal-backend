package co.caudal.api.controller;

import co.caudal.api.dto.response.PublicScheduleResponse;
import co.caudal.application.publication.GetPublicScheduleUseCase;
import co.caudal.application.publication.PosterFile;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
  private static final String SHA_HEADER = "X-Content-SHA256";
  private static final String POSTER_NAME = "horario.pdf";

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

  @Operation(
      summary = "Texto de WhatsApp del horario",
      description =
          "El mensaje que se guardó al publicar el horario del día, listo para compartir. Solo"
              + " lleva sectores y horas. No requiere sesión.")
  @ApiResponse(responseCode = "200", description = "El mensaje en texto plano")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND")
  @GetMapping(value = "/schedule/whatsapp-text", produces = "text/plain;charset=UTF-8")
  public ResponseEntity<String> whatsappText(
      @PathVariable
          @Size(min = FieldLimits.AQUEDUCT_SLUG_MIN, max = FieldLimits.AQUEDUCT_SLUG_MAX)
          @Pattern(regexp = FieldLimits.AQUEDUCT_SLUG_PATTERN)
          String aqueductSlug,
      @RequestParam(required = false) LocalDate date) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
        .body(schedule.execute(aqueductSlug, date).whatsappText());
  }

  @Operation(
      summary = "Cartel PDF del horario",
      description =
          "El cartel para imprimir del horario publicado. La cabecera X-Content-SHA256 trae el"
              + " hash guardado al publicar, que coincide con el del archivo. No requiere sesión.")
  @ApiResponse(responseCode = "200", description = "El cartel en PDF")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND")
  @GetMapping(value = "/schedule/poster.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> poster(
      @PathVariable
          @Size(min = FieldLimits.AQUEDUCT_SLUG_MIN, max = FieldLimits.AQUEDUCT_SLUG_MAX)
          @Pattern(regexp = FieldLimits.AQUEDUCT_SLUG_PATTERN)
          String aqueductSlug,
      @RequestParam(required = false) LocalDate date) {
    PosterFile poster = schedule.poster(aqueductSlug, date);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
        .header(SHA_HEADER, poster.sha256())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(POSTER_NAME).build().toString())
        .body(poster.pdf());
  }
}
