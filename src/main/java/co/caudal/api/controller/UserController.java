package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateUserRequest;
import co.caudal.api.dto.response.CreatedUserResponse;
import co.caudal.api.mapper.UserMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.user.CreateUserCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

/** Administration of accounts by the Board (permission {@code USER_MANAGE}). */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios y membresías", description = "Altas y cambios de cuentas y de sus roles.")
public class UserController {

  private final CommandBus commands;

  UserController(CommandBus commands) {
    this.commands = commands;
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
}
