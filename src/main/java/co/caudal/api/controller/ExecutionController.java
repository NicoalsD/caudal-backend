package co.caudal.api.controller;

import co.caudal.api.dto.request.RecordExecutionRequest;
import co.caudal.api.dto.response.ExecutionResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.execution.ExecutionView;
import co.caudal.application.execution.RecordExecutionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Executions of shifts and closure of days (docs/API.md, section 11). */
@RestController
@Tag(name = "Ejecución y cierre", description = "Ejecución real de los turnos y cierre del día.")
public class ExecutionController {

  private final CommandBus commands;

  ExecutionController(CommandBus commands) {
    this.commands = commands;
  }

  private static java.time.Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  @Operation(
      summary = "Registrar la ejecución de un turno",
      description =
          "Registra si el turno se cumplió (COMPLETED), a medias (PARTIAL) o no se hizo"
              + " (NOT_EXECUTED). Sin horas, COMPLETED usa las del turno; PARTIAL exige el inicio"
              + " real. Una ejecución no se edita: para corregirla se envía otra con supersedes_id."
              + " Requiere el permiso DAY_CLOSE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Ejecución registrada")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: horas inválidas")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el turno no existe")
  @ApiResponse(
      responseCode = "409",
      description =
          "INVALID_STATE_TRANSITION (horario no publicado o día cerrado) o CONFLICT (ya"
              + " registrada o ya reemplazada)")
  @PostMapping("/api/v1/schedule-items/{id}/execution")
  @PreAuthorize("hasAuthority('DAY_CLOSE')")
  public ResponseEntity<ExecutionResponse> record(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody RecordExecutionRequest request,
      HttpServletRequest http) {
    ExecutionView execution =
        commands.dispatch(
            new RecordExecutionCommand(
                CallerFactory.from(jwt, http),
                id,
                request.status(),
                instant(request.actualStart()),
                instant(request.actualEnd()),
                request.note(),
                request.supersedesId()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ExecutionResponse.of(execution));
  }
}
