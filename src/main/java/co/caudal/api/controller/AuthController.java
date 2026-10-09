package co.caudal.api.controller;

import co.caudal.api.dto.request.LoginRequest;
import co.caudal.api.dto.response.CurrentUserResponse;
import co.caudal.api.dto.response.LoginResponse;
import co.caudal.api.dto.response.RefreshResponse;
import co.caudal.api.mapper.AuthMapper;
import co.caudal.api.security.RefreshCookieFactory;
import co.caudal.api.security.RefreshRequestGuard;
import co.caudal.application.auth.GetCurrentUserUseCase;
import co.caudal.application.auth.LoginInput;
import co.caudal.application.auth.LoginResult;
import co.caudal.application.auth.LoginUseCase;
import co.caudal.application.auth.RefreshInput;
import co.caudal.application.auth.RefreshResult;
import co.caudal.application.auth.RefreshSessionUseCase;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session endpoints: who am I, sign in and refresh. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y datos de la sesión.")
public class AuthController {

  private final GetCurrentUserUseCase currentUser;
  private final LoginUseCase login;
  private final RefreshSessionUseCase refresh;
  private final RefreshCookieFactory cookies;
  private final RefreshRequestGuard refreshGuard;

  AuthController(
      GetCurrentUserUseCase currentUser,
      LoginUseCase login,
      RefreshSessionUseCase refresh,
      RefreshCookieFactory cookies,
      RefreshRequestGuard refreshGuard) {
    this.currentUser = currentUser;
    this.login = login;
    this.refresh = refresh;
    this.cookies = cookies;
    this.refreshGuard = refreshGuard;
  }

  @Operation(
      summary = "Renovar la sesión",
      description =
          "Cambia el refresh token de la cookie HttpOnly caudal_rt por un access token nuevo y"
              + " rota la cookie: el token anterior deja de servir. Exige la cabecera"
              + " X-Requested-With: caudal-web y un Origin permitido. Si se presenta un refresh"
              + " token que ya fue rotado, se revoca toda la sesión (SESSION_REVOKED) y se registra"
              + " TOKEN_REUSE_DETECTED. No usa Authorization: la cookie es la credencial.")
  @SecurityRequirements
  @ApiResponse(
      responseCode = "200",
      description = "Sesión renovada",
      content = @Content(schema = @Schema(implementation = RefreshResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED (sin cookie o vencida) o SESSION_REVOKED (reutilización)")
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: falta X-Requested-With u Origin no permitido")
  @PostMapping("/refresh")
  public ResponseEntity<RefreshResponse> refresh(
      @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String secret,
      HttpServletRequest http) {
    refreshGuard.verify(http);
    RefreshResult result =
        refresh.execute(
            new RefreshInput(secret, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT)));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE,
            cookies
                .create(result.refreshToken().rawValue(), result.refreshToken().timeToLive())
                .toString())
        .body(AuthMapper.toResponse(result));
  }

  @Operation(
      summary = "Iniciar sesión",
      description =
          "Valida el usuario y la contraseña. Devuelve el access token (15 minutos) y entrega el"
              + " refresh token en la cookie HttpOnly caudal_rt. Cualquier fallo, sea usuario"
              + " inexistente, contraseña incorrecta, cuenta bloqueada o desactivada, responde"
              + " 401 INVALID_CREDENTIALS con el mismo mensaje. Tras 5 fallos seguidos la cuenta se"
              + " bloquea (el bloqueo no se revela); 20 fallos desde una misma IP en 15 minutos"
              + " responden 429 RATE_LIMITED.")
  @SecurityRequirements
  @ApiResponse(
      responseCode = "200",
      description = "Sesión iniciada",
      content = @Content(schema = @Schema(implementation = LoginResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "INVALID_CREDENTIALS: usuario o contraseña incorrectos")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: demasiados intentos desde la IP")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    LoginResult result =
        login.execute(
            new LoginInput(
                request.username(),
                request.password(),
                http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT)));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE,
            cookies
                .create(result.refreshToken().rawValue(), result.refreshToken().timeToLive())
                .toString())
        .body(AuthMapper.toResponse(result));
  }

  @Operation(
      summary = "Consultar la persona autenticada",
      description =
          "Devuelve la cuenta, las membresías vigentes y los permisos del rol de la sesión. Los"
              + " permisos se leen de la base de datos, así que reflejan la matriz actual. Está"
              + " permitido aunque la persona deba cambiar su contraseña.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(
      responseCode = "200",
      description = "Datos de la sesión",
      content = @Content(schema = @Schema(implementation = CurrentUserResponse.class)))
  @GetMapping("/me")
  public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = parseUserId(jwt.getSubject());
    CurrentUserResponse body =
        AuthMapper.toResponse(currentUser.execute(userId, jwt.getClaimAsString("role")));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  private static UUID parseUserId(String subject) {
    try {
      if (subject == null) {
        throw new DomainException(ErrorCode.UNAUTHORIZED);
      }
      return UUID.fromString(subject);
    } catch (IllegalArgumentException notAUuid) {
      throw new DomainException(ErrorCode.UNAUTHORIZED);
    }
  }
}
