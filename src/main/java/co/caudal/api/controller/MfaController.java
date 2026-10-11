package co.caudal.api.controller;

import co.caudal.api.dto.request.ConfirmMfaRequest;
import co.caudal.api.dto.response.MfaEnrollmentResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.mfa.ConfirmMfaCommand;
import co.caudal.application.mfa.EnrollMfaCommand;
import co.caudal.application.mfa.MfaEnrollment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Second factor (TOTP) of the caller's own account (docs/Seguridad.md, section 2.4; HU-06). */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@Tag(name = "Autenticación", description = "Inicio de sesión, sesiones y segundo factor.")
public class MfaController {

  private final CommandBus commands;

  MfaController(CommandBus commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Iniciar el enrolamiento MFA",
      description =
          "Genera un secreto TOTP (RFC 6238: SHA-1, 6 dígitos, 30 segundos), lo guarda cifrado"
              + " con AES-256-GCM y lo devuelve una sola vez en Base32 y como URI otpauth:// para"
              + " el código QR. El factor queda pendiente hasta POST /auth/mfa/confirm; repetir"
              + " esta llamada reemplaza un factor pendiente. Solo para los roles BOARD_ADMIN y"
              + " PROJECT_TEAM.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Secreto generado")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: el rol no puede activar MFA")
  @ApiResponse(responseCode = "409", description = "CONFLICT: MFA ya está activo")
  @PostMapping("/enroll")
  public ResponseEntity<MfaEnrollmentResponse> enroll(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    MfaEnrollment enrollment =
        commands.dispatch(new EnrollMfaCommand(CallerFactory.from(jwt, http)));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(new MfaEnrollmentResponse(enrollment.secret(), enrollment.otpauthUri()));
  }

  @Operation(
      summary = "Confirmar MFA con el primer código",
      description =
          "Verifica un código de la aplicación contra el factor pendiente y lo activa. Desde"
              + " entonces el login pide mfa_code. Un código ya usado no vuelve a servir.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "MFA activo")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: el código no es válido")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: no hay un enrolamiento pendiente")
  @ApiResponse(responseCode = "409", description = "CONFLICT: MFA ya está activo")
  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ConfirmMfaRequest request,
      HttpServletRequest http) {
    commands.dispatch(new ConfirmMfaCommand(CallerFactory.from(jwt, http), request.code()));
    return ResponseEntity.noContent().build();
  }
}
