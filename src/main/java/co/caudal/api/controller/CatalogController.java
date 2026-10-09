package co.caudal.api.controller;

import co.caudal.api.dto.response.CatalogItemResponse;
import co.caudal.api.dto.response.ListResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.catalog.GetCatalogUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Option lists with their labels in Spanish. Public: only labels. */
@RestController
@RequestMapping("/api/v1/catalogs")
@Tag(name = "Catálogos", description = "Listas de opciones con etiquetas en español.")
public class CatalogController {

  private static final Duration CACHE = Duration.ofMinutes(5);

  private final GetCatalogUseCase catalogs;

  CatalogController(GetCatalogUseCase catalogs) {
    this.catalogs = catalogs;
  }

  @Operation(
      summary = "Consultar un catálogo",
      description =
          "Opciones activas del catálogo WATER_APPEARANCE (aspecto del agua) o DAMAGE_CATEGORY"
              + " (categoría de daño). No requiere sesión y devuelve los elementos globales; si"
              + " hay sesión, suma los del acueducto. Otro nombre responde 404.")
  @ApiResponse(responseCode = "200", description = "Opciones del catálogo")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el catálogo no existe")
  @GetMapping("/{catalog}")
  public ResponseEntity<ListResponse<CatalogItemResponse>> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String catalog, HttpServletRequest http) {
    UUID aqueductId = jwt == null ? null : CallerFactory.from(jwt, http).aqueductId();
    var items =
        catalogs.execute(catalog, aqueductId).stream()
            .map(i -> new CatalogItemResponse(i.code(), i.labelEs(), i.sortOrder()))
            .toList();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(CACHE).cachePrivate())
        .body(new ListResponse<>(items));
  }
}
