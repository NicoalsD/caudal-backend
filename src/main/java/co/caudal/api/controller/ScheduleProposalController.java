package co.caudal.api.controller;

import co.caudal.api.dto.request.GenerateProposalRequest;
import co.caudal.api.dto.response.ProposalResponse;
import co.caudal.api.mapper.ScheduleMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.schedule.GenerateProposalCommand;
import co.caudal.application.schedule.ProposalDetail;
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

/** Proposals of shifts and the decisions of the Board (docs/API.md, section 10). */
@RestController
@RequestMapping("/api/v1/schedule-proposals")
@Tag(name = "Turnos", description = "Propuestas de turnos, revisión y decisión de la Junta.")
public class ScheduleProposalController {

  private final CommandBus commands;

  ScheduleProposalController(CommandBus commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Generar la propuesta de turnos de un día",
      description =
          "Usa el último nivel efectivo y su banda, el pronóstico del día, la reserva y el horario"
              + " de las reglas activas. Ordena los sectores por prioridad y horas sin servicio,"
              + " reparte las horas con la estrategia de las reglas y explica cada turno. Queda en"
              + " PENDING_REVIEW. Requiere el permiso PROPOSAL_GENERATE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Propuesta creada")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: no hay reglas activas")
  @ApiResponse(responseCode = "409", description = "CONFLICT: ya hay una propuesta abierta")
  @ApiResponse(responseCode = "422", description = "NO_LEVEL_DATA u OUTSIDE_OPERATING_WINDOW")
  @PostMapping
  @PreAuthorize("hasAuthority('PROPOSAL_GENERATE')")
  public ResponseEntity<ProposalResponse> generate(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody GenerateProposalRequest request,
      HttpServletRequest http) {
    ProposalDetail proposal =
        commands.dispatch(
            new GenerateProposalCommand(CallerFactory.from(jwt, http), request.serviceDate()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ScheduleMapper.toResponse(proposal));
  }
}
