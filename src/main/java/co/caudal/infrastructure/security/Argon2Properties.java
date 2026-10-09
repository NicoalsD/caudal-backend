package co.caudal.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Argon2id parameters. The canonical values (19456 KiB, 2 iterations, 1 lane) are the defaults of
 * {@code application.yml}; only the test profile lowers them.
 *
 * @param memoryKib memory cost in KiB ({@code ARGON2_MEMORY_KIB})
 * @param iterations time cost ({@code ARGON2_ITERATIONS})
 * @param parallelism number of lanes ({@code ARGON2_PARALLELISM})
 * @param passwordHistorySize previous passwords that cannot be reused ({@code
 *     PASSWORD_HISTORY_SIZE})
 */
@ConfigurationProperties(prefix = "caudal.security.argon2")
public record Argon2Properties(
    int memoryKib, int iterations, int parallelism, int passwordHistorySize) {}
