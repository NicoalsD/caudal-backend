package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.PhysicalConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Changes to a draft of the rules. Every field is optional; a list replaces the current one.
 *
 * @param changeReason why the rules change
 * @param reserveLevel minimum reserve
 * @param reservePolicy reserve policy
 * @param maxDailyServiceHours daily cap
 * @param minShiftHours shortest shift
 * @param maxShiftHours longest shift
 * @param allocationStrategy allocation strategy
 * @param duplicateWindowMinutes window of duplicate readings
 * @param maxLevelChangePerHour sudden jump threshold
 * @param staleReadingHours age of a stale reading
 * @param maxBackdateDays oldest reading accepted
 * @param forecastHorizonDays forecast horizon
 * @param forecastContextDays forecast history
 * @param trendThresholdPerDay trend threshold
 * @param bands new level bands
 * @param sectors new sector settings
 * @param valveOrder new valve order
 * @param operatingWindows new operating windows
 */
@Schema(description = "Cambios de un borrador de reglas. Las listas reemplazan a las actuales.")
public record UpdateRuleSetRequest(
    @Schema(description = "Motivo del cambio, de 10 a 500 caracteres.", nullable = true)
        @Size(min = FieldLimits.REASON_MIN, max = FieldLimits.REASON_MAX)
        String changeReason,
    @Schema(description = "Reserva mínima.", example = "1.20", nullable = true)
        @PositiveOrZero
        @Digits(
            integer = FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal reserveLevel,
    @Schema(description = "NONE, REDUCE_TO_CRITICAL_BAND o PRIORITY_ONLY.", nullable = true)
        @Pattern(regexp = FieldLimits.RESERVE_POLICY_PATTERN)
        String reservePolicy,
    @Schema(
            description = "Máximo de horas de servicio por día (más de 0 y hasta 24).",
            nullable = true)
        @Positive
        @DecimalMin("0.01")
        @Digits(
            integer = FieldLimits.HOURS_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal maxDailyServiceHours,
    @Schema(description = "Duración mínima de un turno, en horas.", nullable = true)
        @Positive
        @Digits(
            integer = FieldLimits.HOURS_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal minShiftHours,
    @Schema(description = "Duración máxima de un turno, en horas.", nullable = true)
        @Positive
        @Digits(
            integer = FieldLimits.HOURS_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal maxShiftHours,
    @Schema(description = "PRIORITY_THEN_LONGEST_WAIT o EQUAL_SPLIT.", nullable = true)
        @Pattern(regexp = FieldLimits.ALLOCATION_STRATEGY_PATTERN)
        String allocationStrategy,
    @Schema(description = "Minutos para detectar lecturas repetidas.", nullable = true) @Positive
        Integer duplicateWindowMinutes,
    @Schema(description = "Cambio máximo de nivel por hora.", nullable = true)
        @Positive
        @Digits(
            integer = FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal maxLevelChangePerHour,
    @Schema(description = "Horas tras las que una lectura es vieja.", nullable = true) @Positive
        Integer staleReadingHours,
    @Schema(description = "Días máximos hacia atrás de una lectura.", nullable = true) @Positive
        Integer maxBackdateDays,
    @Schema(description = "Horizonte del pronóstico (1 a 3 días).", nullable = true)
        @Min(FieldLimits.FORECAST_HORIZON_DAYS_MIN)
        @Max(FieldLimits.FORECAST_HORIZON_DAYS_MAX)
        Integer forecastHorizonDays,
    @Schema(description = "Días de historia del pronóstico.", nullable = true) @Positive
        Integer forecastContextDays,
    @Schema(description = "Umbral de tendencia por día.", nullable = true)
        @PositiveOrZero
        @Digits(
            integer = FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal trendThresholdPerDay,
    @Schema(description = "Bandas de nivel (hasta 10).", nullable = true)
        @Size(min = 1, max = FieldLimits.RULE_BANDS_MAX)
        List<@Valid @NotNull Band> bands,
    @Schema(description = "Ajustes de los sectores.", nullable = true)
        @Size(max = FieldLimits.LIST_MAX_ITEMS)
        List<@Valid @NotNull SectorSetting> sectors,
    @Schema(description = "Orden de apertura de las válvulas.", nullable = true)
        @Size(max = FieldLimits.LIST_MAX_ITEMS)
        List<@Valid @NotNull ValveOrder> valveOrder,
    @Schema(description = "Ventanas de operación (hasta 100).", nullable = true)
        @Size(max = FieldLimits.LIST_MAX_ITEMS)
        List<@Valid @NotNull OperatingWindow> operatingWindows) {

  /**
   * A level band.
   *
   * @param band band code
   * @param minLevel lower limit, included
   * @param maxLevel upper limit, excluded
   * @param dailyServiceHours service hours per day
   * @param priorityOnly only priority sectors
   */
  @Schema(description = "Banda de nivel [min_level, max_level).")
  public record Band(
      @Schema(description = "HIGH, LOW o CRITICAL.")
          @NotNull
          @Pattern(regexp = FieldLimits.RULE_BAND_PATTERN)
          String band,
      @Schema(description = "Nivel mínimo (incluido).")
          @NotNull
          @Digits(
              integer = FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
              fraction = FieldLimits.GAUGE_DECIMALS_MAX)
          BigDecimal minLevel,
      @Schema(description = "Nivel máximo (excluido).")
          @NotNull
          @Digits(
              integer = FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
              fraction = FieldLimits.GAUGE_DECIMALS_MAX)
          BigDecimal maxLevel,
      @Schema(description = "Horas de servicio al día (0 a 24).")
          @NotNull
          @PositiveOrZero
          @Max(PhysicalConstants.HOURS_PER_DAY)
          @Digits(
              integer = FieldLimits.HOURS_INTEGER_DIGITS_MAX,
              fraction = FieldLimits.GAUGE_DECIMALS_MAX)
          BigDecimal dailyServiceHours,
      @Schema(description = "Solo sectores prioritarios; falso si no llega.", nullable = true)
          Boolean priorityOnly) {}

  /**
   * Setting of a sector.
   *
   * @param sectorId sector
   * @param isIncluded receives shifts
   * @param isPriority goes first
   * @param priorityRank order among priority sectors
   */
  @Schema(description = "Ajuste de un sector.")
  public record SectorSetting(
      @Schema(description = "Sector del acueducto.") @NotNull UUID sectorId,
      @Schema(description = "Recibe turnos; verdadero si no llega.", nullable = true)
          Boolean isIncluded,
      @Schema(description = "Es prioritario; falso si no llega.", nullable = true)
          Boolean isPriority,
      @Schema(description = "Orden entre prioritarios, desde 1.", nullable = true) @Positive
          Integer priorityRank) {}

  /**
   * Position of a valve.
   *
   * @param valveId valve
   * @param sequenceOrder position, from 1
   */
  @Schema(description = "Posición de una válvula en la secuencia.")
  public record ValveOrder(
      @Schema(description = "Válvula.") @NotNull UUID valveId,
      @Schema(description = "Posición, desde 1.") @Positive int sequenceOrder) {}

  /**
   * Operating window.
   *
   * @param dayOfWeek ISO day or null for every day
   * @param startTime start
   * @param endTime end, after the start
   */
  @Schema(description = "Ventana de operación en hora local.")
  public record OperatingWindow(
      @Schema(description = "Día ISO (1 lunes a 7 domingo); nulo es todos.", nullable = true)
          @Min(1)
          @Max(PhysicalConstants.DAYS_PER_WEEK)
          Integer dayOfWeek,
      @Schema(description = "Inicio.", example = "05:00") @NotNull LocalTime startTime,
      @Schema(description = "Fin.", example = "21:00") @NotNull LocalTime endTime) {}
}
