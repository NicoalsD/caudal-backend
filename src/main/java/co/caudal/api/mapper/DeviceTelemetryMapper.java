package co.caudal.api.mapper;

import co.caudal.api.dto.response.TelemetryErrorResponse;
import co.caudal.api.dto.response.TelemetryPointResponse;
import co.caudal.api.dto.response.TelemetryResponse;
import co.caudal.application.device.telemetry.TelemetryBatchResult;
import co.caudal.application.device.telemetry.TelemetryPointResult;
import java.util.List;

/** Maps the telemetry results to their answer (docs/API.md, section 16.1). */
public final class DeviceTelemetryMapper {

  private DeviceTelemetryMapper() {}

  /**
   * Maps the result of a batch.
   *
   * @param batch the result
   * @return the answer
   */
  public static TelemetryResponse toResponse(TelemetryBatchResult batch) {
    List<TelemetryPointResponse> results =
        batch.results().stream().map(DeviceTelemetryMapper::point).toList();
    return new TelemetryResponse(batch.accepted(), batch.duplicates(), batch.rejected(), results);
  }

  private static TelemetryPointResponse point(TelemetryPointResult result) {
    TelemetryErrorResponse error =
        result.errorCode() == null ? null : new TelemetryErrorResponse(result.errorCode());
    return new TelemetryPointResponse(
        result.id(), result.status(), result.readingId(), result.validationStatus(), error);
  }
}
