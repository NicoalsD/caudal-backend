package co.caudal.api.controller;

import co.caudal.api.dto.request.RejectDecisionRequest;
import co.caudal.api.dto.request.UpdateAutopilotSettingsRequest;
import co.caudal.api.dto.response.AutopilotDecisionResponse;
import co.caudal.api.dto.response.AutopilotSettingsResponse;
import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.mapper.AutopilotMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.auth.Caller;
import co.caudal.application.autopilot.AcceptDecisionCommand;
import co.caudal.application.autopilot.GetSettingsUseCase;
import co.caudal.application.autopilot.ListDecisionsUseCase;
import co.caudal.application.autopilot.RejectDecisionCommand;
import co.caudal.application.autopilot.RunAutopilotUseCase;
import co.caudal.application.autopilot.UpdateSettingsCommand;
import co.caudal.application.command.CommandBus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The autopilot: its mode, its decisions and the buttons to accept or reject them. */
@RestController
@RequestMapping("/api/v1/autopilot")
@Tag(
    name = "Piloto automático",
    description = "Modo del piloto, decisiones del asistente y su aceptación.")
public class AutopilotController {

  private final GetSettingsUseCase settings;
  private final ListDecisionsUseCase decisions;
  private final RunAutopilotUseCase run;
  private final CommandBus commands;

  AutopilotController(
      GetSettingsUseCase settings,
      ListDecisionsUseCase decisions,
      RunAutopilotUseCase run,
      CommandBus commands) {
    this.settings = settings;
    this.decisions = decisions;
    this.run = run;
    this.commands = commands;
  }

  @Operation(
      summary = "Consultar el modo del piloto",
      description =
          "Modo (MANUAL, ASSISTED o AUTOMATIC), minutos de ausencia del operario y piso de"
              + " reserva del acueducto. Si el acueducto aún no los configuró, devuelve los"
              + " valores por defecto (ASSISTED, 30 minutos, 25 %). Requiere el permiso"
              + " ASSISTANT_USE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Configuración del piloto")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso ASSISTANT_USE")
  @GetMapping("/settings")
  @PreAuthorize("hasAuthority('ASSISTANT_USE')")
  public ResponseEntity<AutopilotSettingsResponse> getSettings(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AutopilotMapper.toResponse(settings.execute(CallerFactory.from(jwt, http))));
  }

  @Operation(
      summary = "Cambiar el modo del piloto",
      description =
          "Cambia el modo y los parámetros del piloto. Los valores rigen desde el siguiente ciclo."
              + " operator_absence_minutes va de 1 a 1440 y reserve_floor_pct de 0 a 100. El"
              + " cambio queda en la auditoría. Requiere el permiso AUTOPILOT_CONFIGURE (solo"
              + " BOARD_ADMIN).")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Configuración guardada")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: algún valor está fuera de límites")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso AUTOPILOT_CONFIGURE")
  @PutMapping("/settings")
  @PreAuthorize("hasAuthority('AUTOPILOT_CONFIGURE')")
  public ResponseEntity<AutopilotSettingsResponse> putSettings(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateAutopilotSettingsRequest request,
      HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AutopilotMapper.toResponse(
                commands.dispatch(
                    new UpdateSettingsCommand(
                        CallerFactory.from(jwt, http),
                        request.mode(),
                        request.operatorAbsenceMinutes(),
                        request.reserveFloorPct()))));
  }

  @Operation(
      summary = "Listar las decisiones del piloto",
      description =
          "Decisiones del piloto, de la más nueva a la más vieja. Filtra por status (PENDING,"
              + " ACCEPTED, REJECTED, EXECUTED, EXPIRED o SKIPPED) y limita con limit (1 a 100,"
              + " por defecto 100). La pantalla consulta PENDING cada 2 segundos. Requiere el"
              + " permiso ASSISTANT_USE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Decisiones")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: status o limit no válidos")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso ASSISTANT_USE")
  @GetMapping("/decisions")
  @PreAuthorize("hasAuthority('ASSISTANT_USE')")
  public ResponseEntity<ListResponse<AutopilotDecisionResponse>> listDecisions(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer limit,
      HttpServletRequest http) {
    return list(decisions.execute(CallerFactory.from(jwt, http), status, limit));
  }

  @Operation(
      summary = "Listar las recomendaciones pendientes",
      description =
          "Atajo de GET /autopilot/decisions?status=PENDING: las recomendaciones que esperan a"
              + " una persona, de la más nueva a la más vieja. Requiere el permiso ASSISTANT_USE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Recomendaciones pendientes")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso ASSISTANT_USE")
  @GetMapping("/recommendations")
  @PreAuthorize("hasAuthority('ASSISTANT_USE')")
  public ResponseEntity<ListResponse<AutopilotDecisionResponse>> recommendations(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    return list(decisions.execute(CallerFactory.from(jwt, http), "PENDING", null));
  }

  @Operation(
      summary = "Aceptar una decisión",
      description =
          "Acepta una decisión PENDING. Si es OPEN o CLOSE crea el comando de la llave (origen"
              + " AUTOPILOT, a nombre de quien acepta) y cuenta como acción humana: pausa al"
              + " piloto. Solo funciona en modo ASSISTED o AUTOMATIC. Un OPEN con el tanque en o"
              + " bajo el piso de reserva deja la decisión SKIPPED y responde 409. Requiere el"
              + " permiso VALVE_COMMAND_MANUAL.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Decisión aceptada (ACCEPTED)")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso VALVE_COMMAND_MANUAL")
  @ApiResponse(
      responseCode = "404",
      description =
          "NOT_FOUND: no existe la decisión, o la llave no es motorizada o no tiene actuador")
  @ApiResponse(
      responseCode = "409",
      description =
          "CONFLICT: modo MANUAL, piso de reserva o comando activo en ese momento;"
              + " INVALID_STATE_TRANSITION: la decisión ya no está PENDING")
  @PostMapping("/decisions/{id}/accept")
  @PreAuthorize("hasAuthority('VALVE_COMMAND_MANUAL')")
  public ResponseEntity<AutopilotDecisionResponse> accept(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AutopilotMapper.toResponse(
                commands.dispatch(new AcceptDecisionCommand(CallerFactory.from(jwt, http), id))));
  }

  @Operation(
      summary = "Rechazar una decisión",
      description =
          "Rechaza una decisión PENDING con un motivo opcional (hasta 200 caracteres). No crea"
              + " comando. Cuenta como acción humana. Requiere el permiso ASSISTANT_USE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Decisión rechazada (REJECTED)")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso ASSISTANT_USE")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la decisión no existe")
  @ApiResponse(
      responseCode = "409",
      description = "INVALID_STATE_TRANSITION: la decisión ya no está PENDING")
  @PostMapping("/decisions/{id}/reject")
  @PreAuthorize("hasAuthority('ASSISTANT_USE')")
  public ResponseEntity<AutopilotDecisionResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) RejectDecisionRequest request,
      HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AutopilotMapper.toResponse(
                commands.dispatch(
                    new RejectDecisionCommand(
                        CallerFactory.from(jwt, http),
                        id,
                        request == null ? null : request.reason()))));
  }

  @Operation(
      summary = "Forzar un ciclo del piloto",
      description =
          "Corre un ciclo del piloto ahora, sin esperar al planificador (para la demostración):"
              + " toma el estado del acueducto, pide recomendaciones al asistente (o a las"
              + " reglas si el servicio no responde) y las trata según el modo. Devuelve las"
              + " decisiones del ciclo. Requiere el permiso AUTOPILOT_CONFIGURE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(
      responseCode = "200",
      description = "Decisiones del ciclo (vacío si no hay llaves motorizadas)")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso AUTOPILOT_CONFIGURE")
  @PostMapping("/run")
  @PreAuthorize("hasAuthority('AUTOPILOT_CONFIGURE')")
  public ResponseEntity<ListResponse<AutopilotDecisionResponse>> forceRun(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    Caller caller = CallerFactory.from(jwt, http);
    return list(run.execute(caller));
  }

  private static ResponseEntity<ListResponse<AutopilotDecisionResponse>> list(
      List<co.caudal.application.autopilot.DecisionView> views) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new ListResponse<>(views.stream().map(AutopilotMapper::toResponse).toList()));
  }
}
