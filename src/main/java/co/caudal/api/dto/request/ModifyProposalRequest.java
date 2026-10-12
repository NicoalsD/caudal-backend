package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The shifts the Board leaves in a proposal.
 *
 * @param lockVersion the version the member saw
 * @param reason why it changes, 10 to 500 characters
 * @param items every shift the proposal keeps
 */
@Schema(description = "Cambio de los turnos de una propuesta con su motivo.")
public record ModifyProposalRequest(
    @Schema(description = "Versión que vio el miembro de la Junta.", example = "0") @NotNull @Min(0)
        Integer lockVersion,
    @Schema(
            description = "Motivo del cambio, de 10 a 500 caracteres.",
            example = "La escuela tiene clase especial; se corre su turno a las 07:00.")
        @Size(max = FieldLimits.REASON_MAX)
        String reason,
    @Schema(description = "Todos los turnos que quedan, de 1 a 100.")
        @NotNull
        @Size(min = 1, max = FieldLimits.LIST_MAX_ITEMS)
        List<@Valid @NotNull Item> items) {

  /**
   * A shift.
   *
   * @param id the existing shift, or null for a new one
   * @param sectorId the sector
   * @param startAt start
   * @param endAt end, after the start
   * @param sequence the order
   */
  @Schema(description = "Turno que deja la Junta.")
  public record Item(
      @Schema(description = "Turno existente, o nulo para uno nuevo.", nullable = true) UUID id,
      @Schema(description = "Sector.") @NotNull UUID sectorId,
      @Schema(description = "Inicio.", example = "2026-10-09T07:00:00-05:00") @NotNull
          OffsetDateTime startAt,
      @Schema(description = "Fin, después del inicio.", example = "2026-10-09T10:00:00-05:00")
          @NotNull
          OffsetDateTime endAt,
      @Schema(description = "Orden, desde 0.", example = "1") @NotNull @Min(0) Integer sequence) {}
}
