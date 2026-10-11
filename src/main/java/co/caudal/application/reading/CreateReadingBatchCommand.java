package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;
import java.util.List;
import java.util.UUID;

/**
 * The offline queue of the app sends its pending readings (docs/API.md, section 7.2; HU-10).
 *
 * @param caller who sends them
 * @param syncBatchId the id the app chose for this batch
 * @param readings the readings, 1 to 100
 */
public record CreateReadingBatchCommand(
    Caller caller, UUID syncBatchId, List<ReadingInput> readings) implements Command<BatchResult> {

  /** Copies the list. */
  public CreateReadingBatchCommand {
    readings = List.copyOf(readings);
  }
}
