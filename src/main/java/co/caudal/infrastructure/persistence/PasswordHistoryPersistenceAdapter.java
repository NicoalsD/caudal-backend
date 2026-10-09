package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.PasswordHistoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Persistence adapter of {@code iam.password_history}. */
@Component
public class PasswordHistoryPersistenceAdapter implements PasswordHistoryPort {

  private final JdbcClient jdbc;

  PasswordHistoryPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<String> findRecent(UUID userId, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return jdbc.sql(
            "SELECT password_hash FROM iam.password_history WHERE user_id = :userId"
                + " ORDER BY id DESC LIMIT :limit")
        .param("userId", userId)
        .param("limit", limit)
        .query(String.class)
        .list();
  }

  @Override
  public void add(UUID userId, String passwordHash) {
    jdbc.sql("INSERT INTO iam.password_history (user_id, password_hash) VALUES (:userId, :hash)")
        .param("userId", userId)
        .param("hash", passwordHash)
        .update();
  }
}
