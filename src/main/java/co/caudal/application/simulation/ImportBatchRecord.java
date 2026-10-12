package co.caudal.application.simulation;

import java.util.UUID;

/**
 * A batch of simulated history imported into a demo aqueduct ({@code sim.import_batches}).
 *
 * @param aqueductId the demo aqueduct
 * @param kind {@code READINGS}, {@code SHIFT_EXECUTIONS} or {@code INCIDENTS}
 * @param scenarioName the scenario of the simulator
 * @param seed the seed of the simulator
 * @param simulatorVersion the version of the simulator
 * @param fileSha256 SHA-256 of the file, so the same file is never imported twice
 * @param rowsReceived rows it had
 * @param rowsAccepted rows stored
 * @param rowsRejected rows refused
 * @param importedBy who imported it
 */
public record ImportBatchRecord(
    UUID aqueductId,
    String kind,
    String scenarioName,
    long seed,
    String simulatorVersion,
    String fileSha256,
    int rowsReceived,
    int rowsAccepted,
    int rowsRejected,
    UUID importedBy) {}
