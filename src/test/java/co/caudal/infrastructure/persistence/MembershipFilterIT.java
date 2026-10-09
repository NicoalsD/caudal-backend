package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.user.Membership;
import co.caudal.support.AppRoleIT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Memberships and row level security, running as caudal_app. */
class MembershipFilterIT extends AppRoleIT {

  @Autowired private MembershipPort memberships;
  @Autowired private UnitOfWorkPort unitOfWork;
  @Autowired private JdbcClient jdbc;

  private List<Membership> activeFor(UUID user, SessionScope scope) {
    return unitOfWork.executeAs(scope, () -> memberships.findActive(user, Instant.now()));
  }

  @Test
  void anAccountSeesItsOwnActiveMembershipsBeforeChoosingAnAqueduct() {
    UUID user = newUser("member.own", UNUSABLE_HASH);
    UUID aqueduct = newAqueduct();
    newMembership(user, aqueduct, "OPERATOR");

    List<Membership> found = activeFor(user, SessionScope.ofUser(user));

    assertThat(found)
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.aqueductId()).isEqualTo(aqueduct);
              assertThat(m.role()).isEqualTo("OPERATOR");
            });
  }

  @Test
  void withoutTheScopeNoRowIsVisible() {
    UUID user = newUser("member.noscope", UNUSABLE_HASH);
    newMembership(user, newAqueduct(), "OPERATOR");

    assertThat(activeFor(user, new SessionScope(null, null))).isEmpty();
  }

  @Test
  void anotherAccountScopeDoesNotRevealTheMemberships() {
    UUID owner1 = newUser("member.owner", UNUSABLE_HASH);
    UUID other = newUser("member.other", UNUSABLE_HASH);
    newMembership(owner1, newAqueduct(), "OPERATOR");

    assertThat(activeFor(owner1, SessionScope.ofUser(other))).isEmpty();
  }

  @Test
  void expiredAndFutureMembershipsAreFiltered() {
    UUID user = newUser("member.dates", UNUSABLE_HASH);
    Instant now = Instant.now();
    newMembership(user, newAqueduct(), "OPERATOR", now.minusSeconds(7200), now.minusSeconds(3600));
    newMembership(user, newAqueduct(), "OPERATOR", now.plusSeconds(3600), null);
    UUID current = newAqueduct();
    newMembership(user, current, "BOARD_MEMBER", now.minusSeconds(60), now.plusSeconds(3600));

    List<Membership> found = activeFor(user, SessionScope.ofUser(user));

    assertThat(found).extracting(Membership::aqueductId).containsExactly(current);
  }

  @Test
  void theAqueductScopeShowsOnlyThatAqueductsRows() {
    UUID user = newUser("member.tenant", UNUSABLE_HASH);
    UUID one = newAqueduct();
    UUID two = newAqueduct();
    newMembership(user, one, "OPERATOR");
    newMembership(user, two, "BOARD_MEMBER");
    UUID admin = newUser("member.admin", UNUSABLE_HASH);

    Integer visible =
        unitOfWork.executeAs(
            new SessionScope(admin, one),
            () -> jdbc.sql("SELECT count(*) FROM iam.memberships").query(Integer.class).single());

    assertThat(visible).isEqualTo(1);
  }

  @Test
  void theScopeDoesNotOutliveTheTransaction() {
    UUID user = newUser("member.leak", UNUSABLE_HASH);
    newMembership(user, newAqueduct(), "OPERATOR");
    activeFor(user, SessionScope.ofUser(user));

    // A later call without scope on a (possibly reused) connection must see nothing.
    assertThat(unitOfWork.execute(() -> memberships.findActive(user, Instant.now()))).isEmpty();
  }
}
