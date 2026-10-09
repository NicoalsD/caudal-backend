package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipTest {

  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-12-31T00:00:00Z");

  @Test
  void isActiveBetweenStartAndEnd() {
    Membership membership = new Membership(UUID.randomUUID(), "OPERATOR", FROM, TO);

    assertThat(membership.isActiveAt(FROM.minusSeconds(1))).isFalse();
    assertThat(membership.isActiveAt(FROM)).isTrue();
    assertThat(membership.isActiveAt(TO.minusSeconds(1))).isTrue();
    assertThat(membership.isActiveAt(TO)).isFalse();
  }

  @Test
  void anOpenMembershipNeverEnds() {
    Membership open = new Membership(UUID.randomUUID(), "OPERATOR", FROM, null);

    assertThat(open.isActiveAt(Instant.parse("2099-01-01T00:00:00Z"))).isTrue();
  }
}
