package co.caudal.infrastructure.config;

import co.caudal.api.error.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * OpenAPI document of {@code /api/v1}: info, servers, security schemes and common error responses
 * (see .agents/api-documentation.md).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiDocsProperties.class)
public class OpenApiConfig {

  /** Security scheme of the endpoints used by people (JWT access token). */
  public static final String BEARER_AUTH = "bearerAuth";

  /** Security scheme of the endpoints used by field devices (Ed25519 signature). */
  public static final String DEVICE_SIGNATURE = "deviceSignature";

  private static final String ERROR_SCHEMA = "ErrorResponse";
  private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
  private static final String UNKNOWN_VERSION = "dev";

  /** Responses added to every operation; endpoint specific codes are declared on each one. */
  private static final Map<HttpStatus, String> COMMON_ERRORS = commonErrors();

  @Bean
  OpenAPI caudalOpenApi(ApiDocsProperties properties, ObjectProvider<BuildProperties> build) {
    BuildProperties buildInfo = build.getIfAvailable();
    String version = buildInfo != null ? buildInfo.getVersion() : UNKNOWN_VERSION;
    return new OpenAPI()
        .info(
            new Info()
                .title("CAUDAL API")
                .version(version)
                .description(
                    "API del cuaderno digital del acueducto: lecturas del tanque, reglas de la"
                        + " Junta, pronóstico, turnos, publicación y actas. Los nombres de campos y"
                        + " los códigos están en inglés; los mensajes, en español."))
        .servers(
            List.of(new Server().url(properties.serverUrl()).description("Servidor de la API")))
        .components(
            new Components()
                .addSecuritySchemes(BEARER_AUTH, bearerScheme())
                .addSecuritySchemes(DEVICE_SIGNATURE, deviceScheme()));
  }

  @Bean
  OpenApiCustomizer commonErrorResponses() {
    return openApi -> {
      errorSchemas().forEach(openApi.getComponents()::addSchemas);
      if (openApi.getPaths() == null) {
        return;
      }
      openApi.getPaths().values().stream()
          .flatMap(path -> path.readOperations().stream())
          .forEach(OpenApiConfig::addCommonErrors);
    };
  }

  private static void addCommonErrors(Operation operation) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }
    for (Map.Entry<HttpStatus, String> entry : COMMON_ERRORS.entrySet()) {
      String code = String.valueOf(entry.getKey().value());
      if (!responses.containsKey(code)) {
        responses.addApiResponse(code, errorResponse(entry.getValue()));
      }
    }
  }

  private static ApiResponse errorResponse(String description) {
    Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
    return new ApiResponse()
        .description(description)
        .content(new Content().addMediaType(JSON, new MediaType().schema(ref)));
  }

  @SuppressWarnings("rawtypes")
  private static Map<String, Schema> errorSchemas() {
    Map<String, Schema> schemas =
        new LinkedHashMap<>(ModelConverters.getInstance().readAll(ErrorResponse.class));
    return schemas;
  }

  private static SecurityScheme bearerScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("Access token de 15 minutos. Se obtiene con `POST /api/v1/auth/login`.");
  }

  private static SecurityScheme deviceScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.APIKEY)
        .in(SecurityScheme.In.HEADER)
        .name("X-Signature")
        .description(
            "Firma Ed25519 de la petición del dispositivo. Requiere las cuatro cabeceras"
                + " `X-Device-Id`, `X-Timestamp`, `X-Nonce` y `X-Signature`. La cadena firmada"
                + " está en docs/Protocolo-de-dispositivos.md.");
  }

  private static Map<HttpStatus, String> commonErrors() {
    Map<HttpStatus, String> errors = new LinkedHashMap<>();
    errors.put(
        HttpStatus.BAD_REQUEST, "Formato, tipo, longitud, campo desconocido o JSON mal formado.");
    errors.put(HttpStatus.UNAUTHORIZED, "Token ausente, inválido o vencido.");
    errors.put(HttpStatus.FORBIDDEN, "Sin permiso para esta acción o recurso.");
    errors.put(HttpStatus.NOT_FOUND, "Recurso inexistente o fuera del acueducto.");
    errors.put(HttpStatus.CONTENT_TOO_LARGE, "Cuerpo demasiado grande.");
    errors.put(HttpStatus.TOO_MANY_REQUESTS, "Límite de peticiones alcanzado.");
    errors.put(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Sin detalles.");
    return Map.copyOf(errors);
  }
}
