package co.caudal.api.mapper;

import co.caudal.api.dto.request.CreateReadingRequest;
import co.caudal.api.dto.response.ReadingBatchResponse;
import co.caudal.api.dto.response.ReadingCreatedResponse;
import co.caudal.api.dto.response.ReadingResponse;
import co.caudal.application.reading.BatchResult;
import co.caudal.application.reading.ListedReading;
import co.caudal.application.reading.ReadingInput;
import co.caudal.application.reading.ReadingRecorder;
import co.caudal.application.reading.ReadingView;
import co.caudal.application.reading.RecordedReading;

/** Maps readings to response DTOs. */
public final class ReadingMapper {

  private ReadingMapper() {}

  /**
   * Maps a recorded reading.
   *
   * @param recorded the result of the command
   * @return the response
   */
  public static ReadingCreatedResponse toResponse(RecordedReading recorded) {
    return new ReadingCreatedResponse(
        toResponse(recorded.reading(), null, null),
        recorded.issues().stream()
            .map(i -> new ReadingCreatedResponse.Issue(i.code(), i.severity(), i.details()))
            .toList(),
        recorded.replay());
  }

  /**
   * Maps a reading of a list.
   *
   * @param listed the reading with its effective value
   * @return the response
   */
  public static ReadingResponse toResponse(ListedReading listed) {
    return toResponse(listed.reading(), listed.effectiveGaugeValue(), listed.correctionCount());
  }

  private static ReadingResponse toResponse(
      ReadingView r, java.math.BigDecimal effective, Integer corrections) {
    return new ReadingResponse(
        r.id(),
        r.tankId(),
        r.gaugeValue(),
        effective,
        corrections,
        r.waterAppearanceCode(),
        r.damageNoticed(),
        r.note(),
        r.observedAt(),
        r.receivedAt(),
        r.source(),
        r.validationStatus(),
        r.ruleSetId());
  }

  /**
   * Maps a reading of a batch to the input of the use case.
   *
   * @param request the reading
   * @return the input
   */
  public static ReadingInput toInput(CreateReadingRequest request) {
    return new ReadingInput(
        request.id(),
        request.tankId(),
        request.gaugeValue(),
        request.waterAppearanceCode(),
        Boolean.TRUE.equals(request.damageNoticed()),
        ReadingRecorder.normalizeNote(request.note()),
        request.observedAt() == null ? null : request.observedAt().toInstant());
  }

  /**
   * Maps the result of a batch.
   *
   * @param result the result
   * @return the response
   */
  public static ReadingBatchResponse toResponse(BatchResult result) {
    return new ReadingBatchResponse(
        result.syncBatchId(),
        result.itemsReceived(),
        result.itemsAccepted(),
        result.itemsRejected(),
        result.replay(),
        result.results().stream()
            .map(
                item ->
                    new ReadingBatchResponse.Item(
                        item.id(),
                        item.status(),
                        item.errorCode() == null
                            ? null
                            : new ReadingBatchResponse.ItemError(
                                item.errorCode(), item.errorDetails())))
            .toList());
  }
}
