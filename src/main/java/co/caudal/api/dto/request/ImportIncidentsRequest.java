package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Simulated reports of damage for a demo aqueduct.
 *
 * @param aqueductId the demo aqueduct
 * @param scenarioName the scenario of the simulator
 * @param seed the seed of the simulator
 * @param simulatorVersion the version of the simulator
 * @param fileSha256 SHA-256 of the file
 * @param rows the reports
 */
@Schema(description = "Historial simulado de reportes de daño para un acueducto demo.")
public record ImportIncidentsRequest(
    @Schema(description = "Acueducto demo (is_demo = true).") @NotNull UUID aqueductId,
    @Schema(description = "Escenario del simulador, 1 a 60.", example = "temporada-seca")
        @NotBlank
        @Size(max = FieldLimits.SCENARIO_NAME_MAX)
        String scenarioName,
    @Schema(description = "Semilla del simulador.", example = "20261011") @NotNull Long seed,
    @Schema(description = "Versión del simulador, 1 a 30.", example = "0.3.0")
        @NotBlank
        @Size(max = FieldLimits.SIMULATOR_VERSION_MAX)
        String simulatorVersion,
    @Schema(description = "SHA-256 del archivo; un archivo repetido responde 409.")
        @NotNull
        @Pattern(regexp = FieldLimits.SHA256_HEX_PATTERN)
        String fileSha256,
    @Schema(description = "Reportes; hasta 5.000.") @NotNull @Size(min = 1)
        List<@Valid @NotNull Row> rows)
    implements ImportEnvelope {

  @Override
  public int rowCount() {
    return rows.size();
  }

  /**
   * One report.
   *
   * @param categoryCode code of DAMAGE_CATEGORY
   * @param sectorId the sector
   * @param description what happened
   * @param locationHint where
   * @param reportedAt when it was reported
   */
  @Schema(description = "Reporte de daño simulado.")
  public record Row(
      @Schema(description = "Código de DAMAGE_CATEGORY.", example = "LEAK")
          @Size(max = FieldLimits.CATALOG_CODE_MAX)
          String categoryCode,
      @Schema(description = "Sector.", nullable = true) UUID sectorId,
      @Schema(description = "Qué pasó, 10 a 500.") @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
          String description,
      @Schema(description = "Dónde.", nullable = true) @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
          String locationHint,
      @Schema(description = "Cuándo se reportó.") OffsetDateTime reportedAt) {}
}
