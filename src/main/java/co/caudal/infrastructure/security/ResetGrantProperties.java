package co.caudal.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the one-time activation and reset codes.
 *
 * @param ttlMinutes minutes a code works after it is issued ({@code RESET_GRANT_TTL_MINUTES})
 */
@ConfigurationProperties(prefix = "caudal.security.reset-grant")
public record ResetGrantProperties(int ttlMinutes) {}
