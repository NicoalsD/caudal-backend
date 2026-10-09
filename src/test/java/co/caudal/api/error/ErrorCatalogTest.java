package co.caudal.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.shared.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class ErrorCatalogTest {

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyCodeHasSpanishMessage(ErrorCode code) throws IOException {
    assertThat(loadMessages().getProperty(code.messageKey())).isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyCodeMapsToAnErrorStatus(ErrorCode code) {
    HttpStatus status = ErrorStatusMapping.statusOf(code);

    assertThat(status.isError()).isTrue();
  }

  @Test
  void mapsRepresentativeCodesAsDocumented() {
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.VALIDATION_ERROR))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.INVALID_CREDENTIALS))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.DEMO_ONLY)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.DUPLICATE_READING))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.GAUGE_OUT_OF_RANGE))
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.RATE_LIMITED))
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(ErrorStatusMapping.statusOf(ErrorCode.PAYLOAD_TOO_LARGE))
        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
  }

  @Test
  void convertsPropertyPathsToSnakeCase() {
    assertThat(JsonFieldNames.toSnakeCase("gaugeValue")).isEqualTo("gauge_value");
    assertThat(JsonFieldNames.toSnakeCase("items[0].observedAt")).isEqualTo("items[0].observed_at");
  }

  private static Properties loadMessages() throws IOException {
    Properties properties = new Properties();
    try (InputStream in = ErrorCatalogTest.class.getResourceAsStream("/messages_es.properties")) {
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
