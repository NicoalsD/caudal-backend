package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.port.out.PermissionPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionCatalogTest {

  private static final Instant START = Instant.parse("2026-10-09T13:00:00Z");

  private final Map<String, Set<String>> matrix = new HashMap<>();
  private int reads;
  private Instant now = START;

  private final PermissionPort port =
      role -> {
        reads++;
        return matrix.getOrDefault(role, Set.of());
      };

  private final Clock clock =
      new Clock() {
        @Override
        public ZoneOffset getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now;
        }
      };

  private PermissionCatalog catalog(int ttlSeconds) {
    return new PermissionCatalog(port, new PermissionProperties(ttlSeconds), clock);
  }

  @Test
  void readsThePermissionsOfTheRole() {
    matrix.put("OPERATOR", Set.of("READING_CREATE", "DAY_CLOSE"));

    assertThat(catalog(60).permissionsOf("OPERATOR"))
        .containsExactlyInAnyOrder("READING_CREATE", "DAY_CLOSE");
  }

  @Test
  void anUnknownRoleHasNoPermissions() {
    assertThat(catalog(60).permissionsOf("GHOST")).isEmpty();
  }

  @Test
  void aMissingRoleHasNoPermissionsAndDoesNotQuery() {
    PermissionCatalog catalog = catalog(60);

    assertThat(catalog.permissionsOf(null)).isEmpty();
    assertThat(catalog.permissionsOf(" ")).isEmpty();
    assertThat(reads).isZero();
  }

  @Test
  void keepsTheValueUntilTheTtlExpires() {
    matrix.put("OPERATOR", Set.of("READING_CREATE"));
    PermissionCatalog catalog = catalog(60);
    catalog.permissionsOf("OPERATOR");

    matrix.put("OPERATOR", Set.of());
    now = START.plus(Duration.ofSeconds(59));

    assertThat(catalog.permissionsOf("OPERATOR")).containsExactly("READING_CREATE");
    assertThat(reads).isEqualTo(1);
  }

  @Test
  void aChangeInTheMatrixAppliesAfterTheTtlWithoutRestart() {
    matrix.put("OPERATOR", Set.of("READING_CREATE"));
    PermissionCatalog catalog = catalog(60);
    catalog.permissionsOf("OPERATOR");

    matrix.put("OPERATOR", Set.of());
    now = START.plus(Duration.ofSeconds(60));

    assertThat(catalog.permissionsOf("OPERATOR")).isEmpty();
    assertThat(reads).isEqualTo(2);
  }

  @Test
  void aZeroTtlReadsTheDatabaseEveryTime() {
    matrix.put("OPERATOR", Set.of("READING_CREATE"));
    PermissionCatalog catalog = catalog(0);

    catalog.permissionsOf("OPERATOR");
    catalog.permissionsOf("OPERATOR");

    assertThat(reads).isEqualTo(2);
  }

  @Test
  void rolesAreCachedIndependently() {
    matrix.put("OPERATOR", Set.of("READING_CREATE"));
    matrix.put("BOARD_MEMBER", Set.of("PROPOSAL_DECIDE"));
    PermissionCatalog catalog = catalog(60);

    assertThat(catalog.permissionsOf("OPERATOR")).containsExactly("READING_CREATE");
    assertThat(catalog.permissionsOf("BOARD_MEMBER")).containsExactly("PROPOSAL_DECIDE");
  }

  @org.junit.jupiter.api.Test
  void aNegativeTtlIsRefused() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PermissionProperties(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
