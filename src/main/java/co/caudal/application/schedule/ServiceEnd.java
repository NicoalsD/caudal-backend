package co.caudal.application.schedule;

import java.time.Instant;

/**
 * When a sector last had water (docs/Reglas-de-negocio.md, section 4).
 *
 * @param endedAt the end of its last service
 * @param origin {@code CONFIRMED} from an execution, {@code ESTIMATED} from a published schedule
 */
public record ServiceEnd(Instant endedAt, String origin) {}
