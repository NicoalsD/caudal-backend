package co.caudal.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code iam.users} as the application role sees it. It maps only the columns that {@code
 * caudal_app} may read, and it is read only: every change goes through the explicit update queries
 * of {@link UserJpaRepository}, because the role can update just some columns.
 */
@Entity
@Table(schema = "iam", name = "users")
public class UserEntity {

  @Id private UUID id;

  @Column(name = "username", nullable = false, updatable = false)
  private String username;

  @Column(name = "full_name", nullable = false, updatable = false)
  private String fullName;

  @Column(name = "password_hash", nullable = false, updatable = false)
  private String passwordHash;

  @Column(name = "status", nullable = false, updatable = false)
  private String status;

  @Column(name = "must_change_password", nullable = false, updatable = false)
  private boolean mustChangePassword;

  @Column(name = "failed_login_count", nullable = false, updatable = false)
  private int failedLoginCount;

  @Column(name = "locked_until", updatable = false)
  private Instant lockedUntil;

  @Column(name = "lockout_level", nullable = false, updatable = false)
  private short lockoutLevel;

  @Column(name = "token_version", nullable = false, updatable = false)
  private int tokenVersion;

  /** Required by JPA. */
  protected UserEntity() {}

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getStatus() {
    return status;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public int getFailedLoginCount() {
    return failedLoginCount;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public short getLockoutLevel() {
    return lockoutLevel;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }
}
