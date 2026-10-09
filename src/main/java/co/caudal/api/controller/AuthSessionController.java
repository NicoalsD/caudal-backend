package co.caudal.api.controller;

import co.caudal.api.security.CallerFactory;
import co.caudal.api.security.RefreshCookieFactory;
import co.caudal.application.auth.LogoutCommand;
import co.caudal.application.command.CommandBus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Closing sessions: this device ({@code logout}) or every device ({@code logout-all}). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y datos de la sesión.")
public class AuthSessionController {

  private final CommandBus commands;
  private final RefreshCookieFactory cookies;

  AuthSessionController(CommandBus commands, RefreshCookieFactory cookies) {
    this.commands = commands;
    this.cookies = cookies;
  }

  @Operation(
      summary = "Cerrar la sesión de este dispositivo",
      description =
          "Revoca la familia del refresh token de la cookie caudal_rt (motivo LOGOUT) y borra la"
              + " cookie. Las demás sesiones de la cuenta siguen abiertas. Si no hay cookie o ya"
              + " no sirve, responde igual 204 y borra la cookie. El access token vigente sigue"
              + " siendo válido hasta que venza (15 minutos); para invalidarlo en todos los"
              + " dispositivos usa logout-all.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "Sesión cerrada")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: falta el access token")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal Jwt jwt,
      @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String secret,
      HttpServletRequest http) {
    commands.dispatch(new LogoutCommand(CallerFactory.from(jwt, http), secret));
    return ResponseEntity.noContent()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }
}
