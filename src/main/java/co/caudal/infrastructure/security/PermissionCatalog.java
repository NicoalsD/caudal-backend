package co.caudal.infrastructure.security;

import co.caudal.application.port.out.PermissionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Permissions of each role, read from {@code iam.role_permissions} and kept for {@code
 * PERMISSION_CACHE_TTL_SECONDS}.
 *
 * <p>The code only knows permission codes; which role has which permission is data. A change in the
 * matrix therefore needs no deployment: it is seen when the cached entry expires. Unknown roles
 * have no permissions (deny by default).
 */
@Component
public class PermissionCatalog {

  private final PermissionPort permissions;
  private final PermissionProperties properties;
  private final Clock clock;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  PermissionCatalog(PermissionPort permissions, PermissionProperties properties, Clock clock) {
    this.permissions = permissions;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Returns the permissions of a role.
   *
   * @param role role code, possibly null or unknown
   * @return the permission codes; empty if the role has none or does not exist
   */
  public Set<String> permissionsOf(String role) {
    if (role == null || role.isBlank()) {
      return Set.of();
    }
    Instant now = clock.instant();
    Entry cached = cache.get(role);
    if (cached != null && now.isBefore(cached.expiresAt())) {
      return cached.permissions();
    }
    Set<String> loaded = permissions.findPermissionCodes(role);
    cache.put(role, new Entry(loaded, now.plus(properties.cacheTtl())));
    return loaded;
  }

  private record Entry(Set<String> permissions, Instant expiresAt) {}
}
