package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateSummaryShareRequest;
import co.caudal.api.dto.response.SummaryShareResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.summary.CreateSummaryShareCommand;
import co.caudal.application.summary.RevokeSummaryShareCommand;
import co.caudal.application.summary.SummaryShareView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authorizations of the Board for entities to see summaries (docs/API.md, section 13.4-13.5). */
@RestController
@RequestMapping("/api/v1/summary-shares")
@Tag(name = "Actas", description = "Actas, resúmenes y autorizaciones para entidades de apoyo.")
public class SummaryShareController {

  private final CommandBus commands;

  SummaryShareController(CommandBus commands) {
    this.commands = commands;
  }

  static SummaryShareResponse toResponse(SummaryShareView share) {
    return new SummaryShareResponse(
        share.id(),
        share.granteeUserId(),
        share.scope(),
        share.periodFrom(),
        share.periodTo(),
        share.reason(),
        share.authorizedBy(),
        share.authorizedAt(),
        share.expiresAt(),
        share.revokedAt(),
        share.revokedBy());
  }

  @Operation(
      summary = "Autorizar un resumen para una entidad",
      description =
          "La Junta autoriza a un usuario con rol SUPPORT_ENTITY a ver un resumen de un periodo"
              + " hasta un vencimiento obligatorio y futuro. El motivo es de 10 a 500 caracteres."
              + " Requiere el permiso SUMMARY_SHARE_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Autorización creada")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR: el usuario no es SUPPORT_ENTITY, el vencimiento ya pasó o el"
              + " periodo está invertido")
  @ApiResponse(responseCode = "422", description = "REASON_REQUIRED: motivo ausente o corto")
  @PostMapping
  @PreAuthorize("hasAuthority('SUMMARY_SHARE_MANAGE')")
  public ResponseEntity<SummaryShareResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateSummaryShareRequest request,
      HttpServletRequest http) {
    SummaryShareView share =
        commands.dispatch(
            new CreateSummaryShareCommand(
                CallerFactory.from(jwt, http),
                request.granteeUserId(),
                request.scope(),
                request.periodFrom(),
                request.periodTo(),
                request.reason(),
                request.expiresAt()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(toResponse(share));
  }

  @Operation(
      summary = "Revocar una autorización de resumen",
      description =
          "Pone la fecha y quién revocó la autorización; la fila se conserva. La entidad deja de"
              + " verla en el acto. Requiere el permiso SUMMARY_SHARE_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "Autorización revocada")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la autorización no existe")
  @ApiResponse(responseCode = "409", description = "CONFLICT: ya estaba revocada")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('SUMMARY_SHARE_MANAGE')")
  public ResponseEntity<Void> revoke(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    commands.dispatch(new RevokeSummaryShareCommand(CallerFactory.from(jwt, http), id));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
