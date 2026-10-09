package co.caudal.api.controller;

import co.caudal.api.dto.request.ChangePasswordRequest;
import co.caudal.api.dto.request.ResetPasswordRequest;
import co.caudal.api.security.CallerFactory;
import co.caudal.api.security.RefreshCookieFactory;
import co.caudal.application.auth.ChangePasswordCommand;
import co.caudal.application.auth.ResetPasswordCommand;
import co.caudal.application.command.CommandBus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Passwords: change by the person who is signed in and reset with a one-time code. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y datos de la sesión.")
public class PasswordController {

  private final CommandBus commands;
  private final RefreshCookieFactory cookies;

  PasswordController(CommandBus commands, RefreshCookieFactory cookies) {
    this.commands = commands;
    this.cookies = cookies;
  }

  @Operation(
      summary = "Cambiar la contraseña",
      description =
          "Cambia la contraseña de la persona autenticada. Exige la contraseña actual. La nueva"
              + " debe tener de 12 a 128 caracteres, no ser una contraseña común, no contener el"
              + " usuario y no estar entre las últimas 5 usadas. Está permitido aunque la cuenta"
              + " deba cambiar la contraseña. Al terminar pone must_change_password en falso,"
              + " incrementa token_version y revoca todas las sesiones (PASSWORD_CHANGED): hay"
              + " que iniciar sesión de nuevo.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: faltan campos o son muy largos")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: sin sesión o la contraseña actual es incorrecta")
  @ApiResponse(
      responseCode = "422",
      description = "PASSWORD_POLICY_VIOLATION: la contraseña nueva no cumple la política")
  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest http) {
    commands.dispatch(
        new ChangePasswordCommand(
            CallerFactory.from(jwt, http), request.currentPassword(), request.newPassword()));
    return ResponseEntity.noContent()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  @Operation(
      summary = "Fijar la contraseña con un código de un solo uso",
      description =
          "Endpoint público. Sirve para activar una cuenta nueva (con el código de activación que"
              + " mostró la Junta al crearla) y para recuperar una contraseña olvidada (con un"
              + " código que emite la Junta). Cualquier fallo del código (usuario inexistente,"
              + " código mal escrito, vencido, ya usado o de otra cuenta) responde 401"
              + " UNAUTHORIZED con el mismo mensaje. Si la contraseña nueva no cumple la política"
              + " responde 422 y el código no se gasta. Al terminar marca el código como usado,"
              + " incrementa token_version y revoca todas las sesiones de la cuenta. No cambia"
              + " must_change_password: una cuenta nueva sigue obligada a cambiar su contraseña"
              + " tras el primer ingreso.")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "Contraseña fijada")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: faltan campos o son muy largos")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: código inválido, vencido o ya usado (mensaje genérico)")
  @ApiResponse(
      responseCode = "422",
      description = "PASSWORD_POLICY_VIOLATION: la contraseña nueva no cumple la política")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: demasiados intentos desde la IP")
  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    commands.dispatch(
        new ResetPasswordCommand(request.username(), request.code(), request.newPassword()));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
