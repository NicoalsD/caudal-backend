package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Active rules of the aqueduct, complete, so the app can validate readings offline.
 *
 * @param id version id
 * @param version version number
 * @param validFrom start of validity
 * @param reserveLevel reserve in gauge units
 * @param reservePolicy reserve policy
 * @param maxDailyServiceHours maximum service hours per day
 * @param minShiftHours minimum shift duration
 * @param maxShiftHours maximum shift duration
 * @param allocationStrategy allocation strategy
 * @param duplicateWindowMinutes window to detect repeated readings
 * @param maxLevelChangePerHour sudden jump threshold
 * @param staleReadingHours hours to consider a reading old
 * @param maxBackdateDays days back a reading may be recorded
 * @param forecastHorizonDays forecast horizon
 * @param forecastContextDays days of history for the forecast
 * @param trendThresholdPerDay threshold for the trend
 * @param bands level bands
 * @param sectors sector settings
 * @param valveOrder valve opening order
 * @param operatingWindows operating windows
 * @param gauge gauge of the tank, or null
 * @param serverTime clock of the server
 */
@Schema(description = "Versión ACTIVE de las reglas de la Junta, completa.")
public record RuleSetResponse(
    @Schema(description = "Identificador de la versión.") UUID id,
    @Schema(description = "Número de versión.", example = "1") int version,
    @Schema(description = "Inicio de la vigencia.") Instant validFrom,
    @Schema(description = "Reserva mínima en unidades de la regla.", example = "1.00")
        BigDecimal reserveLevel,
    @Schema(
            description = "Política de reserva: NONE, REDUCE_TO_CRITICAL_BAND o PRIORITY_ONLY.",
            example = "REDUCE_TO_CRITICAL_BAND")
        String reservePolicy,
    @Schema(description = "Máximo de horas de servicio por día.", example = "16.00")
        BigDecimal maxDailyServiceHours,
    @Schema(description = "Duración mínima de un turno, en horas.", example = "1.00")
        BigDecimal minShiftHours,
    @Schema(description = "Duración máxima de un turno, en horas.", example = "4.00")
        BigDecimal maxShiftHours,
    @Schema(description = "Estrategia de asignación.", example = "PRIORITY_THEN_LONGEST_WAIT")
        String allocationStrategy,
    @Schema(description = "Minutos para detectar lecturas repetidas.", example = "30")
        int duplicateWindowMinutes,
    @Schema(description = "Cambio máximo de nivel por hora antes de marcar un salto brusco.")
        BigDecimal maxLevelChangePerHour,
    @Schema(description = "Horas tras las que una lectura se considera vieja.", example = "12")
        int staleReadingHours,
    @Schema(description = "Días máximos hacia atrás para registrar una lectura.", example = "7")
        int maxBackdateDays,
    @Schema(description = "Horizonte del pronóstico, en días.", example = "3")
        int forecastHorizonDays,
    @Schema(description = "Días de historia para el pronóstico.", example = "30")
        int forecastContextDays,
    @Schema(description = "Umbral por día para decir que el nivel viene bajando.")
        BigDecimal trendThresholdPerDay,
    @Schema(description = "Bandas de nivel.") List<Band> bands,
    @Schema(description = "Ajustes de los sectores.") List<SectorSetting> sectors,
    @Schema(description = "Orden de apertura de las válvulas.") List<ValveOrder> valveOrder,
    @Schema(description = "Ventanas de operación.") List<OperatingWindow> operatingWindows,
    @Schema(description = "Regla pintada del tanque activo.") Gauge gauge,
    @Schema(description = "Hora del servidor.") Instant serverTime) {

  /**
   * A level band.
   *
   * @param band band code
   * @param minLevel lower limit
   * @param maxLevel upper limit
   * @param dailyServiceHours service hours per day
   * @param priorityOnly only priority sectors
   */
  @Schema(description = "Banda de nivel.")
  public record Band(
      @Schema(description = "HIGH, LOW o CRITICAL.", example = "LOW") String band,
      @Schema(description = "Nivel mínimo (incluido).", example = "1.50") BigDecimal minLevel,
      @Schema(description = "Nivel máximo (excluido).", example = "3.50") BigDecimal maxLevel,
      @Schema(description = "Horas de servicio al día.", example = "8.00")
          BigDecimal dailyServiceHours,
      @Schema(description = "Solo sectores prioritarios.", example = "false")
          boolean priorityOnly) {}

  /**
   * Setting of a sector.
   *
   * @param sectorId sector
   * @param isIncluded receives shifts
   * @param isPriority is priority
   * @param priorityRank order among priority sectors
   */
  @Schema(description = "Ajuste de un sector.")
  public record SectorSetting(
      @Schema(description = "Sector.") UUID sectorId,
      @Schema(description = "Recibe turnos.") boolean isIncluded,
      @Schema(description = "Es prioritario.") boolean isPriority,
      @Schema(description = "Orden entre prioritarios.", nullable = true) Integer priorityRank) {}

  /**
   * Valve position.
   *
   * @param valveId valve
   * @param sequenceOrder position
   */
  @Schema(description = "Posición de una válvula en la secuencia.")
  public record ValveOrder(
      @Schema(description = "Válvula.") UUID valveId,
      @Schema(description = "Posición.", example = "1") int sequenceOrder) {}

  /**
   * Operating window.
   *
   * @param dayOfWeek ISO day or null
   * @param startTime start
   * @param endTime end
   */
  @Schema(description = "Ventana de operación en hora local.")
  public record OperatingWindow(
      @Schema(description = "Día ISO (1 lunes a 7 domingo); nulo es todos.", nullable = true)
          Integer dayOfWeek,
      @Schema(description = "Inicio.", example = "05:00:00") LocalTime startTime,
      @Schema(description = "Fin.", example = "21:00:00") LocalTime endTime) {}

  /**
   * Gauge of a tank.
   *
   * @param min minimum
   * @param max maximum
   * @param step precision
   */
  @Schema(description = "Rango de la regla pintada.")
  public record Gauge(
      @Schema(example = "0.00") BigDecimal min,
      @Schema(example = "5.00") BigDecimal max,
      @Schema(example = "0.05") BigDecimal step) {}
}
