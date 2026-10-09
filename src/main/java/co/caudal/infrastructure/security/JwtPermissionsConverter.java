package co.caudal.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a validated access token into an authentication whose authorities are the permissions of
 * the role in the token, read from the database through {@link PermissionCatalog}, plus {@code
 * ROLE_<role>}.
 *
 * <p>It is declared as a bean in {@link SecurityConfig} (and not scanned) so that web slice tests,
 * which scan every {@code Converter}, do not try to build it by accident.
 *
 * <p>The token carries only the role. It never carries permissions, so a change in the matrix
 * applies to tokens that are already issued.
 */
public class JwtPermissionsConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String ROLE_PREFIX = "ROLE_";

  private final PermissionCatalog catalog;

  /**
   * Creates the converter.
   *
   * @param catalog source of the permissions of each role
   */
  public JwtPermissionsConverter(PermissionCatalog catalog) {
    this.catalog = catalog;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String role = jwt.getClaimAsString(JwtAccessTokenIssuerAdapter.CLAIM_ROLE);
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    if (role != null && !role.isBlank()) {
      authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
      List<String> codes = catalog.permissionsOf(role).stream().sorted().toList();
      codes.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
    }
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }
}
