package co.caudal.api.controller;

import co.caudal.api.dto.request.ImportReadingsRequest;
import co.caudal.api.dto.response.ImportResponse;
import co.caudal.api.mapper.ReadingMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.simulation.ImportReadingsCommand;
import co.caudal.application.simulation.ImportRequest;
import co.caudal.application.simulation.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Imports of simulated history into demo aqueducts (docs/API.md, section 17). */
@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Datos simulados", description = "Importación de historial simulado (solo demo).")
public class ImportController {

  private final CommandBus commands;

  ImportController(CommandBus commands) {
    this.commands = commands;
  }

  /**
   * Turns the common fields of an import into the request of the use case.
   *
   * @param jwt the token
   * @param http the request
   * @param body the body
   * @return the import request
   */
  static ImportRequest request(Jwt jwt, HttpServletRequest http, ImportReadingsRequest body) {
    return new ImportRequest(
        CallerFactory.from(jwt, http),
        body.aqueductId(),
        body.scenarioName().strip(),
        body.seed(),
        body.simulatorVersion().strip(),
        body.fileSha256().toLowerCase(java.util.Locale.ROOT),
        body.rows().size());
  }

  @Operation(
      summary = "Importar lecturas simuladas",
      description =
          "Solo para acueductos demo (is_demo = true) y para el acueducto de la sesión; otro"
              + " responde 403 DEMO_ONLY. Cada fila pasa por las mismas reglas de POST /readings"
              + " salvo la antigüedad, y una fila rechazada no detiene la importación. Hasta 5.000"
              + " filas o 1 MiB. Las lecturas quedan con source IMPORT e import_batch_id. Un"
              + " archivo ya importado (mismo file_sha256) responde 409. Requiere el permiso"
              + " IMPORT_SIMULATED.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Importación registrada")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN o DEMO_ONLY")
  @ApiResponse(responseCode = "409", description = "CONFLICT: el archivo ya se importó")
  @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: más de 1 MiB")
  @ApiResponse(responseCode = "422", description = "IMPORT_LIMIT_EXCEEDED: más de 5.000 filas")
  @PostMapping("/readings")
  @PreAuthorize("hasAuthority('IMPORT_SIMULATED')")
  public ResponseEntity<ImportResponse> readings(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ImportReadingsRequest body,
      HttpServletRequest http) {
    ImportResult result =
        commands.dispatch(
            new ImportReadingsCommand(
                request(jwt, http, body),
                body.rows().stream().map(ReadingMapper::toInput).toList()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(toResponse(result));
  }

  static ImportResponse toResponse(ImportResult result) {
    return new ImportResponse(
        result.importBatchId(),
        result.rowsReceived(),
        result.rowsAccepted(),
        result.rowsRejected(),
        result.rejections().stream()
            .map(r -> new ImportResponse.Rejection(r.row(), r.code()))
            .toList());
  }
}
