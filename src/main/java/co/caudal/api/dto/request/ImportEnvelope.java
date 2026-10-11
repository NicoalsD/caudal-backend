package co.caudal.api.dto.request;

import java.util.UUID;

/** The fields every import of simulated history declares (docs/API.md, section 17). */
public interface ImportEnvelope {

  /**
   * The demo aqueduct.
   *
   * @return its id
   */
  UUID aqueductId();

  /**
   * The scenario of the simulator.
   *
   * @return its name
   */
  String scenarioName();

  /**
   * The seed of the simulator.
   *
   * @return the seed
   */
  Long seed();

  /**
   * The version of the simulator.
   *
   * @return the version
   */
  String simulatorVersion();

  /**
   * SHA-256 of the file.
   *
   * @return the hexadecimal hash
   */
  String fileSha256();

  /**
   * How many rows it has.
   *
   * @return the number of rows
   */
  int rowCount();
}
