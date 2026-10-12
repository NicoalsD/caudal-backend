package co.caudal.api.controller;

import co.caudal.api.dto.request.CreateDeviceRequest;
import co.caudal.api.dto.request.RegisterDeviceKeyRequest;
import co.caudal.api.dto.request.UpdateDeviceRequest;
import co.caudal.api.dto.response.DeviceKeyResponse;
import co.caudal.api.dto.response.DeviceResponse;
import co.caudal.api.mapper.DeviceMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.device.CreateDeviceCommand;
import co.caudal.application.device.DeviceKeyView;
import co.caudal.application.device.DeviceView;
import co.caudal.application.device.RevokeDeviceKeyCommand;
import co.caudal.application.device.RotateDeviceKeyCommand;
import co.caudal.application.device.UpdateDeviceCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration of the field devices (docs/API.md, section 16). */
@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Dispositivos", description = "Sensores, actuadores y gateways firmados.")
public class DeviceController {

  private final CommandBus commands;

  DeviceController(CommandBus commands) {
    this.commands = commands;
  }

  @Operation(
      summary = "Registrar un dispositivo",
      description =
          "Registra un sensor de nivel (con su tanque), un actuador de válvula (con su válvula) o"
              + " un gateway del acueducto de la sesión. Nace ACTIVE y sin clave: no acepta"
              + " firmas hasta registrar una clave pública. Requiere el permiso DEVICE_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Dispositivo registrado")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: algún campo no es válido o la referencia no corresponde")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso DEVICE_MANAGE")
  @PostMapping
  @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
  public ResponseEntity<DeviceResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateDeviceRequest request,
      HttpServletRequest http) {
    DeviceView device =
        commands.dispatch(
            new CreateDeviceCommand(
                CallerFactory.from(jwt, http),
                request.kind(),
                request.name(),
                request.tankId(),
                request.valveId(),
                request.firmwareVersion()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(DeviceMapper.toResponse(device));
  }

  @Operation(
      summary = "Cambiar el estado de un dispositivo",
      description =
          "Pone el dispositivo en ACTIVE, SUSPENDED o RETIRED. Un dispositivo suspendido o retirado"
              + " no acepta telemetría ni entrega comandos. Requiere el permiso DEVICE_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Dispositivo actualizado")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: el estado no es válido")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso DEVICE_MANAGE")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: el dispositivo no existe o es de otro acueducto")
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
  public ResponseEntity<DeviceResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateDeviceRequest request,
      HttpServletRequest http) {
    DeviceView device =
        commands.dispatch(
            new UpdateDeviceCommand(CallerFactory.from(jwt, http), id, request.status()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(DeviceMapper.toResponse(device));
  }

  @Operation(
      summary = "Registrar la clave pública de un dispositivo",
      description =
          "Registra una clave Ed25519 (base64url sin relleno, 43 caracteres, 32 bytes) y calcula su"
              + " huella SHA-256. La clave anterior, si existía, sigue válida los días de"
              + " DEVICE_KEY_ROTATION_DAYS (7 por defecto) y después queda revocada. Requiere el"
              + " permiso DEVICE_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "201", description = "Clave registrada")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: falta la clave")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso DEVICE_MANAGE")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: el dispositivo no existe o es de otro acueducto")
  @ApiResponse(responseCode = "409", description = "CONFLICT: la misma clave ya está registrada")
  @ApiResponse(
      responseCode = "422",
      description = "INVALID_KEY: la clave no decodifica a 32 bytes o no es un punto Ed25519")
  @PostMapping("/{id}/keys")
  @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
  public ResponseEntity<DeviceKeyResponse> registerKey(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody RegisterDeviceKeyRequest request,
      HttpServletRequest http) {
    DeviceKeyView key =
        commands.dispatch(
            new RotateDeviceKeyCommand(CallerFactory.from(jwt, http), id, request.publicKey()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(DeviceMapper.toResponse(key));
  }

  @Operation(
      summary = "Revocar una clave de un dispositivo",
      description =
          "Revoca la clave ahora, sin reemplazo. Desde ese momento las firmas con ella dejan de"
              + " aceptarse. Requiere el permiso DEVICE_MANAGE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "Clave revocada")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso DEVICE_MANAGE")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: el dispositivo o la clave no existen")
  @ApiResponse(responseCode = "409", description = "CONFLICT: la clave ya estaba revocada")
  @DeleteMapping("/{id}/keys/{keyId}")
  @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
  public ResponseEntity<Void> revokeKey(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID keyId,
      HttpServletRequest http) {
    commands.dispatch(new RevokeDeviceKeyCommand(CallerFactory.from(jwt, http), id, keyId));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
