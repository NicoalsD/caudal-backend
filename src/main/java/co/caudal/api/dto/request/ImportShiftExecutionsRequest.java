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
 * Simulated executions of shifts for a demo aqueduct.
 *
 * @param aqueductId the demo aqueduct
 * @param scenarioName the scenario of the simulator
 * @param seed the seed of the simulator
 * @param simulatorVersion the version of the simulator
 * @param fileSha256 SHA-256 of the file
 * @param rows the executions
 */
@Schema(description = "Historial simulado de ejecuciones de turnos para un acueducto demo.")
public record ImportShiftExecutionsRequest(
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
    @Schema(description = "Ejecuciones; hasta 5.000.") @NotNull @Size(min = 1)
        List<@Valid @NotNull Row> rows)
    implements ImportEnvelope {

  @Override
  public int rowCount() {
    return rows.size();
  }

  /**
   * One execution.
   *
   * @param scheduleItemId the shift of the schedule
   * @param status COMPLETED, PARTIAL or NOT_EXECUTED
   * @param actualStart when the valve really opened
   * @param actualEnd when it really closed
   * @param note a note
   */
  @Schema(description = "Ejecución simulada de un turno.")
  public record Row(
      @Schema(description = "Turno del horario.") UUID scheduleItemId,
      @Schema(description = "COMPLETED, PARTIAL o NOT_EXECUTED.") String status,
      @Schema(description = "Apertura real.", nullable = true) OffsetDateTime actualStart,
      @Schema(description = "Cierre real.", nullable = true) OffsetDateTime actualEnd,
      @Schema(description = "Nota.", nullable = true) @Size(max = FieldLimits.NOTE_MAX)
          String note) {}
}
