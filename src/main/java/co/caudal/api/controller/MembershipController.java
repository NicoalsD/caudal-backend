package co.caudal.api.controller;

import co.caudal.api.dto.request.GrantMembershipRequest;
import co.caudal.api.dto.response.MembershipResponse;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.mapper.UserMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.pagination.Page;
import co.caudal.application.user.GrantMembershipCommand;
import co.caudal.application.user.ListMembershipsUseCase;
import co.caudal.application.user.MembershipView;
import co.caudal.application.user.RevokeMembershipCommand;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Memberships of accounts in the aqueduct (permission {@code USER_MANAGE}). */
@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Usuarios y membresías", description = "Altas y cambios de cuentas y de sus roles.")
public class MembershipController {

  private final CommandBus commands;
  private final ListMembershipsUseCase list;

  MembershipController(CommandBus commands, ListMembershipsUseCase list) {
    this.commands = commands;
    this.list = list;
  }

  @Operation(
      summary = "Listar las membresías del acueducto",
      description =
          "Membresías vigentes y pasadas del acueducto de la sesión, en orden de creación. Filtros"
              + " opcionales user_id y aqueduct_id (solo el de la sesión; otro responde 403). Se"
              + " pagina con limit (1 a 100, por defecto 20) y un cursor firmado. Requiere el"
              + " permiso USER_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de membresías")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso o el acueducto no es el de la sesión")
  @GetMapping
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<PageResponse<MembershipResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "user_id", required = false) UUID userId,
      @RequestParam(name = "aqueduct_id", required = false) UUID aqueductId,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<MembershipView> page =
        list.execute(CallerFactory.from(jwt, http), userId, aqueductId, limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(UserMapper::toResponse).toList(), page.nextCursor()));
  }

  @Operation(
      summary = "Otorgar una membresía",
      description =
          "Da un rol en el acueducto de la sesión a una cuenta existente. valid_from es ahora si"
              + " no llega y valid_to debe ser mayor que valid_from. Una cuenta tiene un solo rol"
              + " a la vez en un acueducto: una vigencia que se cruza con otra membresía del mismo"
              + " acueducto responde 409. Requiere el permiso USER_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Membresía creada")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: rol desconocido o valid_to no mayor que valid_from")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso o el acueducto no es el de la sesión")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: la cuenta no existe")
  @ApiResponse(responseCode = "409", description = "CONFLICT: se cruza con otra membresía")
  @PostMapping
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<MembershipResponse> grant(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody GrantMembershipRequest request,
      HttpServletRequest http) {
    MembershipView membership =
        commands.dispatch(
            new GrantMembershipCommand(
                CallerFactory.from(jwt, http),
                request.userId(),
                request.aqueductId(),
                request.roleCode(),
                request.validFrom(),
                request.validTo()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(UserMapper.toResponse(membership));
  }

  @Operation(
      summary = "Terminar una membresía",
      description =
          "No borra la fila, porque la auditoría la referencia: fija valid_to en ahora e"
              + " incrementa token_version de la cuenta. Terminar una membresía ya terminada no"
              + " cambia nada. Un administrador no puede terminar su propia membresía vigente."
              + " Requiere el permiso USER_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "Membresía terminada")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: la membresía no existe o es de otro acueducto")
  @ApiResponse(responseCode = "409", description = "CONFLICT: la propia membresía")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<Void> revoke(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    commands.dispatch(new RevokeMembershipCommand(CallerFactory.from(jwt, http), id));
    return ResponseEntity.noContent().build();
  }
}
