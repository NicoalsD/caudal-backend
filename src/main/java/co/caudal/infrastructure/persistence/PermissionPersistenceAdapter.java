package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.PermissionPort;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads {@code iam.role_permissions} with a parameterized query. */
@Component
public class PermissionPersistenceAdapter implements PermissionPort {

  private static final String QUERY =
      "SELECT permission_code FROM iam.role_permissions WHERE role_code = :role";

  private final JdbcClient jdbc;

  PermissionPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<String> findPermissionCodes(String roleCode) {
    return Set.copyOf(jdbc.sql(QUERY).param("role", roleCode).query(String.class).list());
  }
}
