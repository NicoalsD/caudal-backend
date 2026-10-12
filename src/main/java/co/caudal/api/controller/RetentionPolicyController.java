package co.caudal.api.controller;

import co.caudal.api.dto.request.UpdateRetentionPolicyRequest;
import co.caudal.api.dto.response.RetentionPolicyResponse;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.command.CommandBus;
import co.caudal.application.retention.RetentionPolicyView;
import co.caudal.application.retention.UpdateRetentionPolicyCommand;
import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Retention periods of the data classes (docs/API.md, section 18.3). */
@RestController
@RequestMapping("/api/v1/retention-policies")
@Tag(name = "Auditoría", description = "Bitácora de auditoría y eventos de seguridad.")
public class RetentionPolicyController {

  private final CommandBus commands;

  RetentionPolicyController(CommandBus commands) {
    this.commands = commands;
  }

  static RetentionPolicyResponse toResponse(RetentionPolicyView policy) {
    return new RetentionPolicyResponse(
        policy.dataClass(), policy.retentionDays(), policy.updatedBy(), policy.updatedAt());
  }

  @Operation(
      summary = "Ajustar el plazo de retención de una clase de datos",
      description =
          "Cambia los días que se conservan los datos de una clase: LOGIN_ATTEMPTS,"
              + " DEVICE_NONCES, SECURITY_EVENTS, REFRESH_TOKENS, DATA_ACCESS_LOG o"
              + " TELEMETRY_POINTS. El plazo va de 1 a 3.650 días. El cambio queda en la bitácora"
              + " de auditoría como RETENTION_POLICY_CHANGED. Requiere el permiso"
              + " RETENTION_MANAGE, que solo tiene la Junta.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Plazo actualizado")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: clase de datos desconocida o plazo fuera de rango")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso RETENTION_MANAGE")
  @PutMapping("/{data_class}")
  @PreAuthorize("hasAuthority('RETENTION_MANAGE')")
  public ResponseEntity<RetentionPolicyResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("data_class") @Pattern(regexp = FieldLimits.RETENTION_DATA_CLASS_PATTERN)
          String dataClass,
      @Valid @RequestBody UpdateRetentionPolicyRequest request,
      HttpServletRequest http) {
    RetentionPolicyView policy =
        commands.dispatch(
            new UpdateRetentionPolicyCommand(
                CallerFactory.from(jwt, http), dataClass, request.retentionDays()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toResponse(policy));
  }
}
