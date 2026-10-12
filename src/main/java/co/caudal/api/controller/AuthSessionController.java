package co.caudal.api.controller;

import co.caudal.api.dto.request.SwitchAqueductRequest;
import co.caudal.api.dto.response.RefreshResponse;
import co.caudal.api.mapper.AuthMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.api.security.RefreshCookieFactory;
import co.caudal.application.auth.IssuedAccessToken;
import co.caudal.application.auth.LogoutAllCommand;
import co.caudal.application.auth.LogoutCommand;
import co.caudal.application.auth.SwitchAqueductUseCase;
import co.caudal.application.command.CommandBus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Closing sessions: this device ({@code logout}) or every device ({@code logout-all}). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y datos de la sesión.")
public class AuthSessionController {

  private final CommandBus commands;
  private final RefreshCookieFactory cookies;
  private final SwitchAqueductUseCase switchAqueduct;

  AuthSessionController(
      CommandBus commands, RefreshCookieFactory cookies, SwitchAqueductUseCase switchAqueduct) {
    this.commands = commands;
    this.cookies = cookies;
    this.switchAqueduct = switchAqueduct;
  }

  @Operation(
      summary = "Cambiar de acueducto",
      description =
          "Emite un access token nuevo con el aqueduct_id indicado y el rol de la membresía"
              + " vigente de la persona en ese acueducto. Un JWT lleva un solo acueducto. El"
              + " refresh token no cambia.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Access token del otro acueducto")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: no hay una membresía vigente en ese acueducto")
  @PostMapping("/switch-aqueduct")
  public ResponseEntity<RefreshResponse> switchAqueduct(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody SwitchAqueductRequest request,
      HttpServletRequest http) {
    IssuedAccessToken token =
        switchAqueduct.execute(CallerFactory.from(jwt, http), request.aqueductId());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AuthMapper.toResponse(token));
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

  @Operation(
      summary = "Cerrar la sesión en todos los dispositivos",
      description =
          "Revoca todos los refresh tokens de la cuenta (motivo LOGOUT_ALL) e incrementa su"
              + " token_version, así que los access tokens ya emitidos, incluido el de esta"
              + " petición, dejan de servir en su siguiente uso y responden 401 UNAUTHORIZED."
              + " Borra la cookie caudal_rt.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "Todas las sesiones cerradas")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: falta el access token")
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    commands.dispatch(new LogoutAllCommand(CallerFactory.from(jwt, http)));
    return ResponseEntity.noContent()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }
}
