package co.caudal.api.controller;

import co.caudal.api.dto.request.AskAssistantRequest;
import co.caudal.api.dto.response.AssistantAnswerResponse;
import co.caudal.api.mapper.AutopilotMapper;
import co.caudal.api.security.CallerFactory;
import co.caudal.application.autopilot.AssistantFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Questions to the assistant about the state of the aqueduct. */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(
    name = "Piloto automático",
    description = "Modo del piloto, decisiones del asistente y su aceptación.")
public class AssistantController {

  private final AssistantFacade assistant;

  AssistantController(AssistantFacade assistant) {
    this.assistant = assistant;
  }

  @Operation(
      summary = "Preguntar al asistente",
      description =
          "Responde una pregunta en lenguaje natural (de 3 a 500 caracteres) sobre el estado del"
              + " acueducto. El backend toma el estado y lo envía al asistente; si el servicio"
              + " no responde, contesta con una plantilla de reglas (source RULES). Requiere el"
              + " permiso ASSISTANT_USE.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "200", description = "Respuesta del asistente")
  @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: la pregunta no es válida")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN: falta el permiso ASSISTANT_USE")
  @ApiResponse(responseCode = "422", description = "NO_LEVEL_DATA: el acueducto no tiene tanque")
  @PostMapping("/ask")
  @PreAuthorize("hasAuthority('ASSISTANT_USE')")
  public ResponseEntity<AssistantAnswerResponse> ask(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AskAssistantRequest request,
      HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AutopilotMapper.toResponse(
                assistant.ask(CallerFactory.from(jwt, http), request.question())));
  }
}
