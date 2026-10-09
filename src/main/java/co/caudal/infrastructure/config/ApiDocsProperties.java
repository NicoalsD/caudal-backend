package co.caudal.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the OpenAPI document.
 *
 * @param serverUrl public base URL of this API, shown as the server in Swagger UI
 */
@ConfigurationProperties(prefix = "caudal.api-docs")
public record ApiDocsProperties(String serverUrl) {}
