package co.caudal.application.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.shared.error.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingCursorTest {

  private final SignedCursorCodec codec = new SignedCursorCodec(value -> "sig" + value.hashCode());
  private final ReadingCursor cursors = new ReadingCursor(codec);

  @Test
  void keepsTheMomentToTheMicrosecondAndTheId() {
    UUID id = UUID.randomUUID();
    Instant moment = Instant.parse("2026-10-11T12:00:00.123456789Z");

    ReadingCursor.Position position = cursors.decode(cursors.encode(moment, id));

    assertThat(position.observedAt()).isEqualTo(Instant.parse("2026-10-11T12:00:00.123456Z"));
    assertThat(position.id()).isEqualTo(id);
  }

  @Test
  void aSignedButMeaninglessPositionIsInvalid() {
    assertThatThrownBy(() -> cursors.decode(codec.encode("reading", "no-bar")))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> cursors.decode(codec.encode("reading", "yesterday|not-a-uuid")))
        .isInstanceOf(DomainException.class);
  }
}
