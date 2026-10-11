package co.caudal.api.controller;

import co.caudal.api.dto.request.DamageReportRequest;
import co.caudal.api.dto.response.DamageReportResponse;
import co.caudal.application.command.CommandBus;
import co.caudal.application.incident.DamageReportCommand;
import co.caudal.application.incident.DamageReportReceipt;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Damage reports from the public page, without login (docs/API.md, sections 15.4 and 15.5). */
@RestController
@Tag(name = "Público", description = "Horario publicado y reportes de daños, sin iniciar sesión.")
public class PublicReportController {

  private final CommandBus commands;

  PublicReportController(CommandBus commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Reportar un daño",
      description =
          "Cualquier persona reporta un daño sin iniciar sesión y sin datos personales. Responde"
              + " un código de seguimiento que se muestra una sola vez.")
  @ApiResponse(responseCode = "201", description = "Reporte recibido")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND: el acueducto no existe")
  @PostMapping("/api/v1/public/{aqueductSlug}/damage-reports")
  public ResponseEntity<DamageReportResponse> report(
      @PathVariable
          @Size(min = FieldLimits.AQUEDUCT_SLUG_MIN, max = FieldLimits.AQUEDUCT_SLUG_MAX)
          @Pattern(regexp = FieldLimits.AQUEDUCT_SLUG_PATTERN)
          String aqueductSlug,
      @Valid @RequestBody DamageReportRequest request,
      HttpServletRequest http) {
    DamageReportReceipt receipt =
        commands.dispatch(
            new DamageReportCommand(
                aqueductSlug,
                request.categoryCode(),
                request.sectorId(),
                request.description(),
                request.locationHint(),
                http.getRemoteAddr()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(
            new DamageReportResponse(receipt.trackingCode(), receipt.status(), receipt.message()));
  }
}
