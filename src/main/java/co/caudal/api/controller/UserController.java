package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateUserRequest;
import co.caudal.api.dto.request.UpdateUserRequest;
import co.caudal.api.dto.response.CreatedUserResponse;
import co.caudal.api.dto.response.ManagedUserResponse;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.mapper.UserMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.pagination.Page;
import co.caudal.application.user.CreateUserCommand;
import co.caudal.application.user.ListUsersUseCase;
import co.caudal.application.user.ManagedUser;
import co.caudal.application.user.UpdateUserCommand;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administration of accounts by the Board (permission {@code USER_MANAGE}). */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios y membresías", description = "Altas y cambios de cuentas y de sus roles.")
public class UserController {

  private final CommandBus commands;
  private final ListUsersUseCase list;

  UserController(CommandBus commands, ListUsersUseCase list) {
    this.commands = commands;
    this.list = list;
  }

  @Operation(
      summary = "Listar los usuarios del acueducto",
      description =
          "Cuentas con alguna membresía, vigente o pasada, en el acueducto de la sesión, ordenadas"
              + " por nombre de usuario. Filtro opcional status (ACTIVE, LOCKED o DISABLED). Se"
              + " pagina con limit (1 a 100, por defecto 20) y un cursor firmado. full_name es un"
              + " dato confidencial. Requiere el permiso USER_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de usuarios")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso USER_MANAGE")
  @GetMapping
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<PageResponse<ManagedUserResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @Pattern(regexp = FieldLimits.ACCOUNT_STATUS_PATTERN)
          String status,
      @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @RequestParam(required = false) String cursor,
      HttpServletRequest http) {
    Page<ManagedUser> page = list.execute(CallerFactory.from(jwt, http), status, limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(UserMapper::toResponse).toList(), page.nextCursor()));
  }

  @Operation(
      summary = "Crear un usuario",
      description =
          "Crea la cuenta con su primera membresía en el acueducto de la sesión. La cuenta no"
              + " tiene una contraseña conocida (se guarda el hash Argon2id de 32 bytes aleatorios)"
              + " y queda con must_change_password en verdadero. La respuesta trae un código de"
              + " activación de un solo uso (activation_code) que se muestra una sola vez; la"
              + " persona lo usa en POST /auth/reset-password para fijar su contraseña. Solo se"
              + " guarda el SHA-256 del código. Requiere el permiso USER_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(
      responseCode = "201",
      description = "Usuario creado",
      content = @Content(schema = @Schema(implementation = CreatedUserResponse.class)))
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: algún campo no es válido")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta el permiso USER_MANAGE o el acueducto no es el de la sesión")
  @ApiResponse(responseCode = "409", description = "CONFLICT: el nombre de usuario ya existe")
  @PostMapping
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<CreatedUserResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateUserRequest request,
      HttpServletRequest http) {
    CreatedUserResponse body =
        UserMapper.toResponse(
            commands.dispatch(
                new CreateUserCommand(
                    CallerFactory.from(jwt, http),
                    request.username(),
                    request.fullName(),
                    request.aqueductId(),
                    request.role())));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(body);
  }

  @Operation(
      summary = "Cambiar el nombre o el estado de un usuario",
      description =
          "Cambia full_name o status de una cuenta que tiene membresía en el acueducto de la"
              + " sesión. ACTIVE desbloquea y borra los intentos fallidos; LOCKED bloquea hasta"
              + " que un administrador desbloquee; DISABLED desactiva para siempre. Bloquear o"
              + " desactivar incrementa token_version y revoca sus refresh tokens. Un"
              + " administrador no puede bloquear ni desactivar su propia cuenta. Requiere el"
              + " permiso USER_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Usuario actualizado")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: algún campo no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso USER_MANAGE")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: la cuenta no existe o no pertenece al acueducto")
  @ApiResponse(
      responseCode = "409",
      description =
          "INVALID_STATE_TRANSITION (una cuenta desactivada no vuelve) o CONFLICT (la propia"
              + " cuenta)")
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<ManagedUserResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest request,
      HttpServletRequest http) {
    ManagedUser user =
        commands.dispatch(
            new UpdateUserCommand(
                CallerFactory.from(jwt, http), id, request.fullName(), request.status()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(UserMapper.toResponse(user));
  }
}
