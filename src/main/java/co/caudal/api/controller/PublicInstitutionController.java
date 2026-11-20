package co.caudal.api.controller;

import co.caudal.api.dto.response.PublicInstitutionsResponse;
import co.caudal.application.institution.PublicInstitution;
import co.caudal.application.institution.PublicInstitutionDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public directory of institutions, read before the login (docs/API.md, section 2.1). */
@RestController
@RequestMapping("/api/v1/public/institutions")
@Tag(name = "Público", description = "Directorio de instituciones, sin iniciar sesión.")
public class PublicInstitutionController {

  private final PublicInstitutionDirectory directory;

  PublicInstitutionController(PublicInstitutionDirectory directory) {
    this.directory = directory;
  }

  @Operation(
      summary = "Listar las instituciones",
      description =
          "Devuelve las instituciones activas con su identificador (slug) y su nombre, ordenadas"
              + " por nombre. Sirve para elegir la institución antes de iniciar sesión. No"
              + " requiere sesión y tiene límite de tasa por dirección IP.")
  @SecurityRequirements
  @ApiResponse(
      responseCode = "200",
      description = "Directorio de instituciones",
      content = @Content(schema = @Schema(implementation = PublicInstitutionsResponse.class)))
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: demasiadas consultas desde la IP")
  @GetMapping
  public ResponseEntity<PublicInstitutionsResponse> list(HttpServletRequest http) {
    List<PublicInstitutionsResponse.Item> items =
        directory.list(http.getRemoteAddr()).stream()
            .map(PublicInstitutionController::toItem)
            .toList();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new PublicInstitutionsResponse(items));
  }

  private static PublicInstitutionsResponse.Item toItem(PublicInstitution institution) {
    return new PublicInstitutionsResponse.Item(institution.slug(), institution.name());
  }
}
