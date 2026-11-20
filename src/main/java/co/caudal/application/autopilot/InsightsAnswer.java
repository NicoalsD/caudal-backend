package co.caudal.application.autopilot;

import java.time.Instant;

/**
 * The answer to a question about the state of the aqueduct.
 *
 * @param source {@code LLM} or {@code RULES}
 * @param model the model or the version of the rules
 * @param generatedAt when it was produced
 * @param answer the answer in Spanish
 */
public record InsightsAnswer(String source, String model, Instant generatedAt, String answer) {}
