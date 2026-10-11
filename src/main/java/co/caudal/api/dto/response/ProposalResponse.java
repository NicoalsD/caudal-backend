package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A full proposal of shifts.
 *
 * @param id the proposal
 * @param serviceDate the day of service
 * @param status its status
 * @param ruleSetId the rules used
 * @param tankLevelSnapshot the level used
 * @param tankBand its band
 * @param availableHours hours of service of the day
 * @param forecastRunId the forecast used, or null
 * @param strategyCode how the hours were handed out
 * @param generatedAt when it was made
 * @param lockVersion version for the next decision
 * @param items the shifts
 * @param unservedSectors sectors without a shift
 * @param decisions decisions of the Board
 * @param snapshotTaken true if the original was saved before a change
 */
@Schema(description = "Propuesta de turnos con sus explicaciones.")
@SuppressWarnings("checkstyle:ParameterNumber")
public record ProposalResponse(
    UUID id,
    LocalDate serviceDate,
    @Schema(example = "PENDING_REVIEW") String status,
    UUID ruleSetId,
    @Schema(example = "2.10") BigDecimal tankLevelSnapshot,
    @Schema(example = "LOW") String tankBand,
    @Schema(example = "8.00") BigDecimal availableHours,
    @Schema(nullable = true) UUID forecastRunId,
    @Schema(example = "PRIORITY_THEN_LONGEST_WAIT") String strategyCode,
    Instant generatedAt,
    int lockVersion,
    List<Item> items,
    List<Unserved> unservedSectors,
    List<Decision> decisions,
    boolean snapshotTaken) {

  /**
   * A shift.
   *
   * @param id the shift
   * @param sectorId the sector
   * @param sequence its order
   * @param startAt start
   * @param endAt end
   * @param origin PROPOSED, MODIFIED or ADDED
   * @param reasons why
   */
  @Schema(description = "Turno de un sector.")
  public record Item(
      UUID id,
      UUID sectorId,
      int sequence,
      Instant startAt,
      Instant endAt,
      @Schema(example = "PROPOSED") String origin,
      List<Reason> reasons) {}

  /**
   * One reason of a shift.
   *
   * @param code the code
   * @param params its values
   * @param text the sentence people read
   */
  @Schema(description = "Motivo de un turno.")
  public record Reason(
      @Schema(example = "PRIORITY_SECTOR") String code, Map<String, Object> params, String text) {}

  /**
   * A sector without a shift.
   *
   * @param sectorId the sector
   * @param reason why
   */
  @Schema(description = "Sector sin turno y su motivo.")
  public record Unserved(UUID sectorId, @Schema(example = "NO_BUDGET") String reason) {}

  /**
   * A decision of the Board.
   *
   * @param decision APPROVED, APPROVED_WITH_CHANGES or REJECTED
   * @param reason the reason, or null
   * @param decidedBy who decided
   * @param decidedAt when
   */
  @Schema(description = "Decisión de la Junta.")
  public record Decision(
      String decision, @Schema(nullable = true) String reason, UUID decidedBy, Instant decidedAt) {}
}
