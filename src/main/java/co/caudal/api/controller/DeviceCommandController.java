package co.caudal.api.controller;

import co.caudal.api.dto.response.ValveCommandsResponse;
import co.caudal.api.mapper.DeviceCommandMapper;
import co.caudal.application.device.auth.SignedHeaders;
import co.caudal.application.device.auth.SignedRequest;
import co.caudal.application.device.command.ValveCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands the valve commands to the actuators (docs/API.md, section 16.2). The request is signed, not
 * authenticated with a token: the signature check is in the application layer.
 */
@RestController
@Tag(name = "Dispositivos", description = "Sensores, actuadores y gateways firmados.")
public class DeviceCommandController {

  private static final String DEVICE_ID = "X-Device-Id";
  private static final String TIMESTAMP = "X-Timestamp";
  private static final String NONCE = "X-Nonce";
  private static final String SIGNATURE = "X-Signature";

  /** A GET has no body; its signature covers the hash of the empty string. */
  private static final byte[] NO_BODY = new byte[0];

  private final ValveCommandService commands;

  DeviceCommandController(ValveCommandService commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Obtener los comandos de válvula del dispositivo",
      description =
          "Devuelve hasta 100 comandos de OPEN o CLOSE del dispositivo que ya pueden ejecutarse"
              + " (entre not_before y expires_at), ordenados por not_before. Los pendientes pasan"
              + " a DELIVERED. Un comando entregado y sin confirmar se vuelve a enviar mientras"
              + " siga vigente; el dispositivo debe tratarlo como idempotente. Un comando vencido"
              + " pasa a EXPIRED y no se envía. Firma en las cabeceras X-Device-Id, X-Timestamp,"
              + " X-Nonce y X-Signature, con cuerpo vacío.")
  @SecurityRequirement(name = "deviceSignature")
  @ApiResponse(responseCode = "200", description = "Comandos que el dispositivo debe ejecutar")
  @ApiResponse(
      responseCode = "401",
      description =
          "INVALID_SIGNATURE, NONCE_REPLAY o TIMESTAMP_OUT_OF_WINDOW: firma, nonce o hora no válidos")
  @ApiResponse(
      responseCode = "403",
      description = "DEVICE_INACTIVE: el dispositivo está suspendido o retirado")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: más de 60 peticiones por minuto")
  @GetMapping("/api/v1/devices/commands")
  public ResponseEntity<ValveCommandsResponse> list(HttpServletRequest http) {
    SignedRequest request =
        SignedRequest.of(
            http.getMethod(),
            http.getRequestURI(),
            new SignedHeaders(
                values(http, DEVICE_ID),
                values(http, TIMESTAMP),
                values(http, NONCE),
                values(http, SIGNATURE)),
            NO_BODY,
            http.getRemoteAddr());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(DeviceCommandMapper.toResponse(commands.deliver(request)));
  }

  private static List<String> values(HttpServletRequest http, String header) {
    return Collections.list(http.getHeaders(header));
  }
}
