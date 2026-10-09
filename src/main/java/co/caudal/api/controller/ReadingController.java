package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateReadingRequest;
import co.caudal.api.dto.response.ReadingCreatedResponse;
import co.caudal.api.mapper.ReadingMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.reading.CreateReadingCommand;
import co.caudal.application.reading.RecordedReading;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Readings of the tank. */
@RestController
@RequestMapping("/api/v1/readings")
@Tag(name = "Lecturas", description = "Lecturas del nivel del tanque.")
public class ReadingController {

  private final CommandBus commands;

  ReadingController(CommandBus commands) {
    this.commands = commands;
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
}
