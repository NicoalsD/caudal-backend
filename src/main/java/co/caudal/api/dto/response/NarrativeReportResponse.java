package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The narrative report of a period.
 *
 * @param source LLM or RULES
 * @param model the model or the version of the rules
 * @param generatedAt when it was produced
 * @param title title
 * @param summary short summary
 * @param findings findings
 * @param recommendations recommendations for the board
 * @param markdown the whole report as Markdown
 */
@Schema(description = "Reporte narrativo del periodo, en español.")
public record NarrativeReportResponse(
    @Schema(description = "LLM o RULES.") String source,
    String model,
    Instant generatedAt,
    String title,
    String summary,
    List<String> findings,
    List<String> recommendations,
    @Schema(description = "Reporte completo en Markdown, listo para imprimir.") String markdown) {}
