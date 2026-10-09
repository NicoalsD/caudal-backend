package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.port.out.PermissionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPermissionsConverterTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");

  private final PermissionPort port =
      role -> "OPERATOR".equals(role) ? Set.of("READING_CREATE", "DAY_CLOSE") : Set.of();
  private final JwtPermissionsConverter converter =
      new JwtPermissionsConverter(
          new PermissionCatalog(port, new PermissionProperties(60), Clock.systemUTC()));

  private static Jwt jwt(Map<String, Object> extraClaims) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("0190f3a2-0000-7000-8000-000000000001")
            .issuedAt(NOW)
            .expiresAt(NOW.plusSeconds(900));
    extraClaims.forEach(builder::claim);
    return builder.build();
  }

  @Test
  void authoritiesAreTheRoleAndItsPermissionsFromTheDatabase() {
    var authentication = converter.convert(jwt(Map.of("role", "OPERATOR")));

    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_OPERATOR", "READING_CREATE", "DAY_CLOSE");
    assertThat(authentication.getName()).isEqualTo("0190f3a2-0000-7000-8000-000000000001");
  }

  @Test
  void aRoleWithoutPermissionsOnlyGetsTheRoleAuthority() {
    var authentication = converter.convert(jwt(Map.of("role", "SUPPORT_ENTITY")));

    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_SUPPORT_ENTITY");
  }

  @Test
  void aTokenWithoutRoleGetsNoAuthorities() {
    var authentication = converter.convert(jwt(Map.of()));

    assertThat(authentication.getAuthorities()).isEmpty();
  }

  @Test
  void permissionsInsideTheTokenAreIgnored() {
    var authentication =
        converter.convert(
            jwt(Map.of("role", "SUPPORT_ENTITY", "permissions", java.util.List.of("USER_MANAGE"))));

    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .doesNotContain("USER_MANAGE");
  }
}
