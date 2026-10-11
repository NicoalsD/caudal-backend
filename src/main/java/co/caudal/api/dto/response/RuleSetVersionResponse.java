package co.caudal.api.dto.response;

import co.caudal.api.dto.response.RuleSetResponse.Band;
import co.caudal.api.dto.response.RuleSetResponse.OperatingWindow;
import co.caudal.api.dto.response.RuleSetResponse.SectorSetting;
import co.caudal.api.dto.response.RuleSetResponse.ValveOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A full version of the rules with its history (docs/API.md, section 6.3).
 *
 * @param id the version
 * @param version its number
 * @param status its status
 * @param basedOnRuleSetId the version it was copied from
 * @param validFrom start of its validity
 * @param validTo end of its validity
 * @param changeReason why the Board changed the rules
 * @param createdBy who created it
 * @param createdAt when it was created
 * @param activatedBy who activated it
 * @param activatedAt when it was activated
 * @param reserveLevel minimum reserve
 * @param reservePolicy reserve policy
 * @param maxDailyServiceHours daily service cap
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
 * @param bands level bands
 * @param sectors sector settings
 * @param valveOrder valve order
 * @param operatingWindows operating windows
 */
@Schema(description = "Versión completa de las reglas con su historia.")
public record RuleSetVersionResponse(
    @Schema(description = "Identificador de la versión.") UUID id,
    @Schema(description = "Número de versión.", example = "4") int version,
    @Schema(description = "DRAFT, ACTIVE, SUPERSEDED o DISCARDED.", example = "DRAFT")
        String status,
    @Schema(description = "Versión de la que se copió.", nullable = true) UUID basedOnRuleSetId,
    @Schema(description = "Inicio de la vigencia; nulo en un borrador.", nullable = true)
        Instant validFrom,
    @Schema(description = "Fin de la vigencia.", nullable = true) Instant validTo,
    @Schema(description = "Motivo del cambio (10 a 500 caracteres).", nullable = true)
        String changeReason,
    @Schema(description = "Quién creó la versión.") UUID createdBy,
    @Schema(description = "Creación.") Instant createdAt,
    @Schema(description = "Quién la activó.", nullable = true) UUID activatedBy,
    @Schema(description = "Activación.", nullable = true) Instant activatedAt,
    @Schema(description = "Reserva mínima en unidades de la regla.", example = "1.00")
        BigDecimal reserveLevel,
    @Schema(description = "NONE, REDUCE_TO_CRITICAL_BAND o PRIORITY_ONLY.") String reservePolicy,
    @Schema(description = "Máximo de horas de servicio por día.") BigDecimal maxDailyServiceHours,
    @Schema(description = "Duración mínima de un turno, en horas.") BigDecimal minShiftHours,
    @Schema(description = "Duración máxima de un turno, en horas.") BigDecimal maxShiftHours,
    @Schema(description = "PRIORITY_THEN_LONGEST_WAIT o EQUAL_SPLIT.") String allocationStrategy,
    @Schema(description = "Minutos para detectar lecturas repetidas.") int duplicateWindowMinutes,
    @Schema(description = "Cambio máximo de nivel por hora.") BigDecimal maxLevelChangePerHour,
    @Schema(description = "Horas tras las que una lectura es vieja.") int staleReadingHours,
    @Schema(description = "Días máximos hacia atrás de una lectura.") int maxBackdateDays,
    @Schema(description = "Horizonte del pronóstico, en días.") int forecastHorizonDays,
    @Schema(description = "Días de historia del pronóstico.") int forecastContextDays,
    @Schema(description = "Umbral de tendencia por día.") BigDecimal trendThresholdPerDay,
    @Schema(description = "Bandas de nivel.") List<Band> bands,
    @Schema(description = "Ajustes de los sectores.") List<SectorSetting> sectors,
    @Schema(description = "Orden de apertura de las válvulas.") List<ValveOrder> valveOrder,
    @Schema(description = "Ventanas de operación.") List<OperatingWindow> operatingWindows) {}
