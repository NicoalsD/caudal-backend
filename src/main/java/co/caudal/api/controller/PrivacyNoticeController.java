package co.caudal.api.controller;

import co.caudal.api.dto.request.AcceptPrivacyNoticeRequest;
import co.caudal.api.dto.response.PrivacyAcceptanceResponse;
import co.caudal.api.dto.response.PrivacyNoticeResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.privacy.PrivacyAcceptance;
import co.caudal.application.privacy.PrivacyNotice;
import co.caudal.application.privacy.PrivacyNoticeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The privacy notice (Ley 1581 de 2012; docs/API.md, sections 4.3 and 4.4). */
@RestController
@RequestMapping("/api/v1/privacy-notice")
@Tag(name = "Meta y catálogos", description = "Límites de los campos y catálogos públicos.")
public class PrivacyNoticeController {

  private final PrivacyNoticeService notices;

  PrivacyNoticeController(PrivacyNoticeService notices) {
    this.notices = notices;
  }

  @Operation(
      summary = "Aviso de privacidad vigente",
      description = "Versión vigente del aviso con su texto y el SHA-256 del texto.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Aviso vigente")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: no hay un aviso vigente")
  @GetMapping("/current")
  public ResponseEntity<PrivacyNoticeResponse> current() {
    PrivacyNotice notice = notices.current();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PrivacyNoticeResponse(
                notice.version(), notice.textEs(), notice.textSha256(), notice.effectiveAt()));
  }

  @Operation(
      summary = "Aceptar el aviso de privacidad",
      description =
          "Registra que la persona aceptó la versión vigente, con el momento y la IP en HMAC."
              + " Aceptarla otra vez devuelve la primera aceptación.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Aceptación registrada")
  @ApiResponse(responseCode = "409", description = "CONFLICT: la versión ya no es la vigente")
  @PostMapping("/accept")
  public ResponseEntity<PrivacyAcceptanceResponse> accept(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AcceptPrivacyNoticeRequest request,
      HttpServletRequest http) {
    PrivacyAcceptance acceptance = notices.accept(CallerFactory.from(jwt, http), request.version());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(
            new PrivacyAcceptanceResponse(
                acceptance.userId(), acceptance.version(), acceptance.acceptedAt()));
  }
}
