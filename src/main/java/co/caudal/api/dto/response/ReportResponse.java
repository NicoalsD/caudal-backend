package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * The numbers of a period.
 *
 * @param from start of the period
 * @param to end of the period
 * @param aggregates groups of numbers by topic
 */
@Schema(description = "Datos del periodo para un reporte.")
public record ReportResponse(
    Instant from,
    Instant to,
    @Schema(
            description =
                "Grupos: levels, shifts_by_sector, valve_commands, autopilot_decisions,"
                    + " incidents y anomalies.")
        Map<String, Object> aggregates) {}
