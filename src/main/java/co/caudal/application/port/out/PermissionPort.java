package co.caudal.application.port.out;

import java.util.Set;

/** Reads the permission matrix that lives in the database ({@code iam.role_permissions}). */
public interface PermissionPort {

  /**
   * Finds the permission codes granted to a role.
   *
   * @param roleCode the role, for example {@code OPERATOR}
   * @return the permission codes; empty for an unknown role (deny by default)
   */
  Set<String> findPermissionCodes(String roleCode);
}
