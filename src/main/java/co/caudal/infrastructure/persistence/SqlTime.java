package co.caudal.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversions between SQL timestamps and {@link Instant}. */
final class SqlTime {

  private SqlTime() {}

  /** Reads a nullable {@code timestamptz} column. */
  static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /** Converts an instant for a query parameter. */
  static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  /** Converts an instant for a typed {@code timestamptz} parameter that may be null. */
  static OffsetDateTime offset(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
