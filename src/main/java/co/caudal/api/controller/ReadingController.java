package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateReadingRequest;
import co.caudal.api.dto.response.ReadingCreatedResponse;
import co.caudal.api.dto.response.ReadingPageResponse;
import co.caudal.api.mapper.ReadingMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.reading.CreateReadingCommand;
import co.caudal.application.reading.ListReadingsUseCase;
import co.caudal.application.reading.ReadingFilter;
import co.caudal.application.reading.RecordedReading;
import co.caudal.application.reading.ValidationStatus;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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

/** Readings of the tank. */
@RestController
@RequestMapping("/api/v1/readings")
@Tag(name = "Lecturas", description = "Lecturas del nivel del tanque.")
public class ReadingController {

  private final CommandBus commands;
  private final ListReadingsUseCase list;

  ReadingController(CommandBus commands, ListReadingsUseCase list) {
    this.commands = commands;
    this.list = list;
  }

  @Operation(
      summary = "Registrar una lectura",
      description =
          "Registra una lectura del tanque. Es idempotente por el id del cliente: el mismo id con"
              + " el mismo contenido responde 200 con idempotent_replay en verdadero; con otro"
              + " contenido, 409 DUPLICATE_READING. Una lectura repetida o con un salto brusco se"
              + " guarda como FLAGGED con sus issues. Se rechaza con 422 si el valor sale del"
              + " rango del tanque (GAUGE_OUT_OF_RANGE), si falta la fecha (MISSING_TIMESTAMP), si"
              + " está más de 5 minutos en el futuro (FUTURE_TIMESTAMP) o es más vieja que"
              + " max_backdate_days (TOO_OLD). Requiere el permiso READING_CREATE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Lectura registrada")
  @ApiResponse(responseCode = "200", description = "Reintento idempotente de una lectura existente")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el tanque no existe")
  @ApiResponse(
      responseCode = "409",
      description = "DUPLICATE_READING: el id existe con otro contenido")
  @ApiResponse(responseCode = "422", description = "Lectura inválida: no se guarda")
  @PostMapping
  @PreAuthorize("hasAuthority('READING_CREATE')")
  public ResponseEntity<ReadingCreatedResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateReadingRequest request,
      HttpServletRequest http) {
    RecordedReading recorded =
        commands.dispatch(
            new CreateReadingCommand(
                CallerFactory.from(jwt, http),
                request.id(),
                request.tankId(),
                request.gaugeValue(),
                request.waterAppearanceCode(),
                Boolean.TRUE.equals(request.damageNoticed()),
                request.note(),
                request.observedAt() == null ? null : request.observedAt().toInstant()));
    return ResponseEntity.status(recorded.replay() ? HttpStatus.OK : HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ReadingMapper.toResponse(recorded));
  }

  @Operation(
      summary = "Listar las lecturas de un tanque",
      description =
          "Lecturas del tanque indicado, de la más reciente a la más antigua por observed_at,"
              + " con su valor efectivo y el número de correcciones. Se pagina con limit (1 a 100,"
              + " por defecto 20) y un cursor firmado: un cursor alterado responde 400"
              + " CURSOR_INVALID. Filtros opcionales: from y to (ISO-8601 con zona, from no puede"
              + " ser posterior a to) y validation_status (ACCEPTED o FLAGGED). Requiere el permiso"
              + " READING_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de lecturas")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el tanque no existe")
  @GetMapping
  @PreAuthorize("hasAuthority('READING_VIEW')")
  public ResponseEntity<ReadingPageResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("tank_id") UUID tankId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(name = "validation_status", required = false) ValidationStatus status,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    ListReadingsUseCase.Page page =
        list.execute(
            CallerFactory.from(jwt, http),
            new ReadingFilter(tankId, from, to, status),
            limit,
            cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new ReadingPageResponse(
                page.items().stream().map(ReadingMapper::toResponse).toList(), page.nextCursor()));
  }
}
