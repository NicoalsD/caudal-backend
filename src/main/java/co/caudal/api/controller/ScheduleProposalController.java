package co.caudal.api.controller;

import co.caudal.api.dto.request.GenerateProposalRequest;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.dto.response.ProposalResponse;
import co.caudal.api.dto.response.ProposalSummaryResponse;
import co.caudal.api.mapper.ScheduleMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.pagination.Page;
import co.caudal.application.schedule.GenerateProposalCommand;
import co.caudal.application.schedule.ListProposalsUseCase;
import co.caudal.application.schedule.ProposalDetail;
import co.caudal.application.schedule.ProposalSummary;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
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

/** Proposals of shifts and the decisions of the Board (docs/API.md, section 10). */
@RestController
@RequestMapping("/api/v1/schedule-proposals")
@Tag(name = "Turnos", description = "Propuestas de turnos, revisión y decisión de la Junta.")
public class ScheduleProposalController {

  private final CommandBus commands;
  private final ListProposalsUseCase list;

  ScheduleProposalController(CommandBus commands, ListProposalsUseCase list) {
    this.commands = commands;
    this.list = list;
  }

  @Operation(
      summary = "Listar las propuestas de turnos",
      description =
          "Propuestas del acueducto de la sesión, de la más nueva a la más antigua, sin sus"
              + " turnos. Filtros opcionales service_date_from, service_date_to y status. Se"
              + " pagina con limit y un cursor firmado. Requiere el permiso PROPOSAL_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de propuestas")
  @GetMapping
  @PreAuthorize("hasAuthority('PROPOSAL_VIEW')")
  public ResponseEntity<PageResponse<ProposalSummaryResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "service_date_from", required = false) LocalDate from,
      @RequestParam(name = "service_date_to", required = false) LocalDate to,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.PROPOSAL_STATUS_PATTERN)
          String status,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<ProposalSummary> page =
        list.execute(CallerFactory.from(jwt, http), from, to, status, limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(ScheduleMapper::toResponse).toList(), page.nextCursor()));
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
