package co.caudal.application.simulation;

import co.caudal.application.auth.Caller;
import java.util.UUID;

/**
 * What every import of simulated history declares (docs/API.md, section 17).
 *
 * @param caller who imports
 * @param aqueductId the demo aqueduct
 * @param scenarioName the scenario of the simulator
 * @param seed the seed of the simulator
 * @param simulatorVersion the version of the simulator
 * @param fileSha256 SHA-256 of the file
 * @param rowCount how many rows it has
 */
public record ImportRequest(
    Caller caller,
    UUID aqueductId,
    String scenarioName,
    long seed,
    String simulatorVersion,
    String fileSha256,
    int rowCount) {}
