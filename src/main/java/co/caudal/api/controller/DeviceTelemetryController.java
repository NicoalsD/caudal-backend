package co.caudal.api.controller;

import co.caudal.api.dto.request.TelemetryBatchRequest;
import co.caudal.api.dto.response.TelemetryResponse;
import co.caudal.api.mapper.DeviceTelemetryMapper;
import co.caudal.application.device.auth.SignedHeaders;
import co.caudal.application.device.auth.SignedRequest;
import co.caudal.application.device.telemetry.DeviceTelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the telemetry of the level sensors (docs/API.md, section 16.1). The request is signed,
 * not authenticated with a token: the signature check is in the application layer.
 */
@RestController
@Tag(name = "Dispositivos", description = "Sensores, actuadores y gateways firmados.")
public class DeviceTelemetryController {

  private static final String DEVICE_ID = "X-Device-Id";
  private static final String TIMESTAMP = "X-Timestamp";
  private static final String NONCE = "X-Nonce";
  private static final String SIGNATURE = "X-Signature";

  private final DeviceTelemetryService telemetry;

  DeviceTelemetryController(DeviceTelemetryService telemetry) {
    this.telemetry = telemetry;
  }

  @Operation(
      summary = "Enviar telemetría de un dispositivo",
      description =
          "Recibe de 1 a 100 puntos firmados (cabeceras X-Device-Id, X-Timestamp, X-Nonce y"
              + " X-Signature). Verifica la firma antes de leer el cuerpo y guarda cada punto por"
              + " separado: un punto rechazado no detiene el lote. Un punto LEVEL genera una"
              + " lectura del tanque del sensor. Un punto ya recibido con el mismo contenido"
              + " responde DUPLICATE. Límite de 256 KiB y 60 peticiones por minuto por dispositivo.")
  @SecurityRequirement(name = "deviceSignature")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Lote firmado; los bytes del cuerpo son los que se firman.",
      required = true,
      content = @Content(schema = @Schema(implementation = TelemetryBatchRequest.class)))
  @ApiResponse(
      responseCode = "200",
      description = "Lote procesado; cada punto trae su estado o su error")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: el cuerpo no tiene la forma del protocolo")
  @ApiResponse(
      responseCode = "401",
      description =
          "INVALID_SIGNATURE, NONCE_REPLAY o TIMESTAMP_OUT_OF_WINDOW: firma, nonce o hora no válidos")
  @ApiResponse(
      responseCode = "403",
      description = "DEVICE_INACTIVE: el dispositivo está suspendido o retirado")
  @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: más de 256 KiB")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: más de 60 peticiones por minuto")
  @PostMapping("/api/v1/devices/telemetry")
  public ResponseEntity<TelemetryResponse> receive(
      @RequestBody byte[] body, HttpServletRequest http) {
    SignedRequest request =
        SignedRequest.of(
            http.getMethod(),
            http.getRequestURI(),
            new SignedHeaders(
                values(http, DEVICE_ID),
                values(http, TIMESTAMP),
                values(http, NONCE),
                values(http, SIGNATURE)),
            body,
            http.getRemoteAddr());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(DeviceTelemetryMapper.toResponse(telemetry.receive(request)));
  }

  private static List<String> values(HttpServletRequest http, String header) {
    return Collections.list(http.getHeaders(header));
  }
}
