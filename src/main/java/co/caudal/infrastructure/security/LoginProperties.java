package co.caudal.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lock and limit settings of the login (docs/Seguridad.md, section 2.2).
 *
 * @param maxFailures consecutive failures that lock an account ({@code LOGIN_MAX_FAILURES})
 * @param lockMinutes failure window and first lock, in minutes ({@code LOGIN_LOCK_MINUTES})
 * @param ipMaxAttempts failed attempts from one IP in the window ({@code LOGIN_IP_MAX_ATTEMPTS})
 * @param ipWindowMinutes window of the IP limit, in minutes ({@code LOGIN_IP_WINDOW_MINUTES})
 */
@ConfigurationProperties(prefix = "caudal.security.login")
public record LoginProperties(
    int maxFailures, int lockMinutes, int ipMaxAttempts, int ipWindowMinutes) {}
