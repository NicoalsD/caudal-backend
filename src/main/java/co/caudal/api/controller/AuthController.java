package co.caudal.api.controller;

import co.caudal.api.dto.response.CurrentUserResponse;
import co.caudal.api.mapper.AuthMapper;
import co.caudal.application.auth.GetCurrentUserUseCase;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session endpoints: who am I, sign in and refresh. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y datos de la sesión.")
public class AuthController {

  private final GetCurrentUserUseCase currentUser;

  AuthController(GetCurrentUserUseCase currentUser) {
    this.currentUser = currentUser;
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
