package co.caudal.infrastructure.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the local list of common passwords from the classpath. */
final class CommonPasswordsLoader {

  /** Classpath location of the list. */
  static final String RESOURCE = "security/common-passwords.txt";

  private static final String COMMENT_PREFIX = "#";

  private CommonPasswordsLoader() {}

  /**
   * Loads the list, skipping blank lines and comments.
   *
   * @return the common passwords as written in the file
   */
  static Set<String> load() {
    InputStream stream = CommonPasswordsLoader.class.getClassLoader().getResourceAsStream(RESOURCE);
    if (stream == null) {
      throw new IllegalStateException("Missing classpath resource " + RESOURCE);
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::strip)
          .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT_PREFIX))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException ex) {
      throw new UncheckedIOException("Cannot read " + RESOURCE, ex);
    }
  }
}
