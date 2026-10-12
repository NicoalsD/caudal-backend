package co.caudal.api.controller;

import co.caudal.api.dto.request.ApproveProposalRequest;
import co.caudal.api.dto.request.GenerateProposalRequest;
import co.caudal.api.dto.request.ModifyProposalRequest;
import co.caudal.api.dto.request.PublishScheduleRequest;
import co.caudal.api.dto.request.RejectProposalRequest;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.dto.response.ProposalResponse;
import co.caudal.api.dto.response.ProposalSummaryResponse;
import co.caudal.api.dto.response.PublicationResponse;
import co.caudal.api.mapper.ScheduleMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.pagination.Page;
import co.caudal.application.publication.PublicationView;
import co.caudal.application.publication.PublishScheduleCommand;
import co.caudal.application.schedule.ApproveProposalCommand;
import co.caudal.application.schedule.GenerateProposalCommand;
import co.caudal.application.schedule.GetProposalUseCase;
import co.caudal.application.schedule.ListProposalsUseCase;
import co.caudal.application.schedule.ModifiedItem;
import co.caudal.application.schedule.ModifyProposalCommand;
import co.caudal.application.schedule.ProposalDetail;
import co.caudal.application.schedule.ProposalSummary;
import co.caudal.application.schedule.RejectProposalCommand;
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
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final GetProposalUseCase get;

  ScheduleProposalController(
      CommandBus commands, ListProposalsUseCase list, GetProposalUseCase get) {
    this.commands = commands;
    this.list = list;
    this.get = get;
  }

  @Operation(
      summary = "Ver una propuesta de turnos",
      description =
          "La propuesta completa: turnos con sus motivos en texto, sectores sin turno, historial"
              + " de decisiones de la Junta y si se guardó la versión original antes de un"
              + " cambio. Requiere el permiso PROPOSAL_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "La propuesta")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la propuesta no existe")
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PROPOSAL_VIEW')")
  public ResponseEntity<ProposalResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ScheduleMapper.toResponse(get.execute(CallerFactory.from(jwt, http), id)));
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

  @Operation(
      summary = "Aprobar una propuesta",
      description =
          "Aprueba una propuesta en PENDING_REVIEW tal como está. Exige el lock_version que vio"
              + " el miembro: si otro la cambió antes, responde CONFLICT. Requiere el permiso"
              + " PROPOSAL_DECIDE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Propuesta aprobada")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la propuesta no existe")
  @ApiResponse(
      responseCode = "409",
      description = "CONFLICT (lock_version distinto) o INVALID_STATE_TRANSITION")
  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('PROPOSAL_DECIDE')")
  public ResponseEntity<ProposalResponse> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ApproveProposalRequest request,
      HttpServletRequest http) {
    ProposalDetail proposal =
        commands.dispatch(
            new ApproveProposalCommand(CallerFactory.from(jwt, http), id, request.lockVersion()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ScheduleMapper.toResponse(proposal));
  }

  @Operation(
      summary = "Modificar y aprobar una propuesta",
      description =
          "La Junta deja la lista completa de turnos con un motivo de 10 a 500 caracteres. Se"
              + " valida la duración de cada turno, el horario de operación y las horas"
              + " disponibles. Antes del primer cambio se guarda la propuesta original. Los turnos"
              + " cambiados quedan MODIFIED y los nuevos ADDED, con el motivo de la Junta. La"
              + " propuesta pasa a APPROVED_WITH_CHANGES. Requiere el permiso PROPOSAL_DECIDE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Propuesta aprobada con cambios")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la propuesta no existe")
  @ApiResponse(
      responseCode = "409",
      description = "CONFLICT, INVALID_STATE_TRANSITION o SCHEDULE_OVERLAP")
  @ApiResponse(
      responseCode = "422",
      description =
          "REASON_REQUIRED, HOURS_EXCEEDED, SHIFT_LENGTH_INVALID u OUTSIDE_OPERATING_WINDOW")
  @PostMapping("/{id}/modify")
  @PreAuthorize("hasAuthority('PROPOSAL_DECIDE')")
  public ResponseEntity<ProposalResponse> modify(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ModifyProposalRequest request,
      HttpServletRequest http) {
    ProposalDetail proposal =
        commands.dispatch(
            new ModifyProposalCommand(
                CallerFactory.from(jwt, http),
                id,
                request.lockVersion(),
                request.reason(),
                request.items().stream()
                    .map(
                        i ->
                            new ModifiedItem(
                                i.id(),
                                i.sectorId(),
                                i.startAt().toInstant(),
                                i.endAt().toInstant(),
                                i.sequence()))
                    .toList()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ScheduleMapper.toResponse(proposal));
  }

  @Operation(
      summary = "Rechazar una propuesta",
      description =
          "Rechaza una propuesta en PENDING_REVIEW con un motivo de 10 a 500 caracteres. Es un"
              + " estado final: para el mismo día se genera otra propuesta. Requiere el permiso"
              + " PROPOSAL_DECIDE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Propuesta rechazada")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la propuesta no existe")
  @ApiResponse(responseCode = "409", description = "CONFLICT o INVALID_STATE_TRANSITION")
  @ApiResponse(responseCode = "422", description = "REASON_REQUIRED: falta el motivo")
  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('PROPOSAL_DECIDE')")
  public ResponseEntity<ProposalResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody RejectProposalRequest request,
      HttpServletRequest http) {
    ProposalDetail proposal =
        commands.dispatch(
            new RejectProposalCommand(
                CallerFactory.from(jwt, http), id, request.lockVersion(), request.reason()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ScheduleMapper.toResponse(proposal));
  }

  @Operation(
      summary = "Publicar un horario aprobado",
      description =
          "Publica una propuesta APPROVED o APPROVED_WITH_CHANGES. Guarda la publicación con el"
              + " mensaje de WhatsApp, que solo lleva sectores y horas. Exige el lock_version que"
              + " vio el miembro. Requiere el permiso SCHEDULE_PUBLISH.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Horario publicado")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la propuesta no existe")
  @ApiResponse(
      responseCode = "409",
      description = "CONFLICT o INVALID_STATE_TRANSITION (no está aprobada)")
  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAuthority('SCHEDULE_PUBLISH')")
  public ResponseEntity<PublicationResponse> publish(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody PublishScheduleRequest request,
      HttpServletRequest http) {
    PublicationView publication =
        commands.dispatch(
            new PublishScheduleCommand(CallerFactory.from(jwt, http), id, request.lockVersion()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(
            new PublicationResponse(
                publication.id(),
                publication.proposalId(),
                publication.publishedAt(),
                publication.whatsappText(),
                publication.posterSha256()));
  }
}
