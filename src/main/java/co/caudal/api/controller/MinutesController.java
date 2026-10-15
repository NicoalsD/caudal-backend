package co.caudal.api.controller;

import co.caudal.api.dto.request.GenerateMinutesRequest;
import co.caudal.api.dto.response.MinutesResponse;
import co.caudal.api.dto.response.MinutesSummaryResponse;
import co.caudal.api.dto.response.PageResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.minutes.FinalizeMinutesCommand;
import co.caudal.application.minutes.GenerateMinutesCommand;
import co.caudal.application.minutes.GetMinutesPdfUseCase;
import co.caudal.application.minutes.GetMinutesUseCase;
import co.caudal.application.minutes.ListMinutesUseCase;
import co.caudal.application.minutes.MinutesPdf;
import co.caudal.application.minutes.MinutesSummaryView;
import co.caudal.application.minutes.MinutesView;
import co.caudal.application.pagination.Page;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Minutes of the Board (docs/API.md, section 13). */
@RestController
@RequestMapping("/api/v1/minutes")
@Tag(name = "Actas", description = "Actas por estatus epistémico y su PDF.")
public class MinutesController {

  private static final String SHA_HEADER = "X-Content-SHA256";

  private final CommandBus commands;
  private final GetMinutesUseCase get;
  private final GetMinutesPdfUseCase pdf;
  private final ListMinutesUseCase list;

  MinutesController(
      CommandBus commands,
      GetMinutesUseCase get,
      GetMinutesPdfUseCase pdf,
      ListMinutesUseCase list) {
    this.commands = commands;
    this.get = get;
    this.pdf = pdf;
    this.list = list;
  }

  @Operation(
      summary = "Listar las actas",
      description =
          "Actas del acueducto de la sesión, de la más reciente a la más antigua, sin sus"
              + " secciones. Se pagina con limit (de 1 a 100, por defecto 20) y un cursor firmado."
              + " Requiere el permiso MINUTES_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Página de actas")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o CURSOR_INVALID")
  @GetMapping
  @PreAuthorize("hasAuthority('MINUTES_VIEW')")
  public ResponseEntity<PageResponse<MinutesSummaryResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Tamaño de la página, de 1 a 100.", example = "20")
          @RequestParam(defaultValue = "" + FieldLimits.PAGE_LIMIT_DEFAULT)
          @Min(FieldLimits.PAGE_LIMIT_MIN)
          @Max(FieldLimits.PAGE_LIMIT_MAX)
          int limit,
      @Parameter(description = "Cursor de la página siguiente, devuelto por la lista anterior.")
          @RequestParam(required = false)
          String cursor,
      HttpServletRequest http) {
    Page<MinutesSummaryView> page = list.execute(CallerFactory.from(jwt, http), limit, cursor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PageResponse<>(
                page.items().stream().map(MinutesSummaryResponse::of).toList(), page.nextCursor()));
  }

  @Operation(
      summary = "Generar un acta",
      description =
          "Genera el acta de un periodo en DRAFT, con los registros clasificados en observado,"
              + " estimado, inferido y confirmado. Requiere el permiso MINUTES_GENERATE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Acta generada")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: periodo invertido o largo")
  @PostMapping
  @PreAuthorize("hasAuthority('MINUTES_GENERATE')")
  public ResponseEntity<MinutesResponse> generate(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody GenerateMinutesRequest request,
      HttpServletRequest http) {
    MinutesView minutes =
        commands.dispatch(
            new GenerateMinutesCommand(
                CallerFactory.from(jwt, http), request.periodFrom(), request.periodTo()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(MinutesResponse.of(minutes));
  }

  @Operation(
      summary = "Ver un acta",
      description = "El acta con sus secciones. Requiere el permiso MINUTES_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "El acta")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND")
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('MINUTES_VIEW')")
  public ResponseEntity<MinutesResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MinutesResponse.of(get.execute(CallerFactory.from(jwt, http), id)));
  }

  @Operation(
      summary = "Cerrar un acta como final",
      description =
          "Pasa el acta de DRAFT a FINAL, con quién y cuándo, y el hash cabeza de la cadena de"
              + " auditoría. Un acta FINAL ya no cambia. Requiere el permiso MINUTES_GENERATE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Acta final")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND")
  @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION: ya es FINAL")
  @PostMapping("/{id}/finalize")
  @PreAuthorize("hasAuthority('MINUTES_GENERATE')")
  public ResponseEntity<MinutesResponse> finalizeMinutes(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    MinutesView minutes =
        commands.dispatch(new FinalizeMinutesCommand(CallerFactory.from(jwt, http), id));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MinutesResponse.of(minutes));
  }

  @Operation(
      summary = "Descargar el PDF del acta",
      description =
          "El acta en PDF. Un borrador lleva la marca BORRADOR; un acta FINAL es el mismo PDF"
              + " cuyo hash se guardó (cabecera X-Content-SHA256). Cada descarga queda en el"
              + " registro de accesos. Requiere el permiso MINUTES_VIEW.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "El PDF")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND")
  @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("hasAuthority('MINUTES_VIEW')")
  public ResponseEntity<byte[]> pdf(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
    MinutesPdf file = pdf.execute(CallerFactory.from(jwt, http), id);
    ResponseEntity.BodyBuilder response =
        ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName()).build().toString());
    if (file.sha256() != null) {
      response.header(SHA_HEADER, file.sha256());
    }
    return response.body(file.pdf());
  }
}
