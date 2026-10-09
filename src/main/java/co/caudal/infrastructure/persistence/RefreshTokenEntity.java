package co.caudal.infrastructure.persistence;

import co.caudal.shared.FieldLimits;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Row of {@code iam.refresh_tokens}. A row is inserted once; later changes (rotation, revocation)
 * are explicit update queries that touch only the columns the application role may update.
 */
@Entity
@Table(schema = "iam", name = "refresh_tokens")
public class RefreshTokenEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(
      name = "token_hash",
      nullable = false,
      updatable = false,
      length = FieldLimits.SHA256_HEX_LENGTH)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "last_used_at", updatable = false, insertable = false)
  private Instant lastUsedAt;

  @Column(name = "revoked_at", updatable = false, insertable = false)
  private Instant revokedAt;

  @Column(name = "revoked_reason", updatable = false, insertable = false)
  private String revokedReason;

  @Column(name = "replaced_by_id", updatable = false, insertable = false)
  private UUID replacedById;

  @Column(name = "created_ip_hmac", updatable = false)
  private String createdIpHmac;

  @Column(name = "user_agent", updatable = false)
  private String userAgent;

  /** Required by JPA. */
  protected RefreshTokenEntity() {}

  RefreshTokenEntity(
      UUID userId,
      UUID familyId,
      String tokenHash,
      Instant issuedAt,
      Instant expiresAt,
      String createdIpHmac,
      String userAgent) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.createdIpHmac = createdIpHmac;
    this.userAgent = userAgent;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public String getRevokedReason() {
    return revokedReason;
  }

  public UUID getReplacedById() {
    return replacedById;
  }
}
