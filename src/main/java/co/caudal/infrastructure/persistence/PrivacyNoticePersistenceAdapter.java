package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.PrivacyNoticePort;
import co.caudal.application.privacy.PrivacyAcceptance;
import co.caudal.application.privacy.PrivacyNotice;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads {@code iam.privacy_notice_versions} and writes {@code iam.privacy_acceptances}. */
@Component
public class PrivacyNoticePersistenceAdapter implements PrivacyNoticePort {

  private final JdbcClient jdbc;

  PrivacyNoticePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<PrivacyNotice> current(Instant now) {
    return jdbc.sql(
            "SELECT version, text_es, text_sha256, effective_at FROM iam.privacy_notice_versions"
                + " WHERE effective_at <= :now ORDER BY version DESC LIMIT 1")
        .param("now", SqlTime.offset(now), Types.TIMESTAMP_WITH_TIMEZONE)
        .query(
            (rs, row) ->
                new PrivacyNotice(
                    rs.getInt("version"),
                    rs.getString("text_es"),
                    rs.getString("text_sha256"),
                    SqlTime.instant(rs, "effective_at")))
        .optional();
  }

  @Override
  public PrivacyAcceptance accept(UUID userId, int version, String ipHmac, Instant at) {
    jdbc.sql(
            "INSERT INTO iam.privacy_acceptances (user_id, version, accepted_at, ip_hmac)"
                + " VALUES (:user, :version, :at, :ip) ON CONFLICT (user_id, version) DO NOTHING")
        .param("user", userId)
        .param("version", version)
        .param("at", SqlTime.offset(at), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("ip", ipHmac, Types.VARCHAR)
        .update();
    return jdbc.sql(
            "SELECT user_id, version, accepted_at FROM iam.privacy_acceptances"
                + " WHERE user_id = :user AND version = :version")
        .param("user", userId)
        .param("version", version)
        .query(
            (rs, row) ->
                new PrivacyAcceptance(
                    rs.getObject("user_id", UUID.class),
                    rs.getInt("version"),
                    SqlTime.instant(rs, "accepted_at")))
        .single();
  }
}
