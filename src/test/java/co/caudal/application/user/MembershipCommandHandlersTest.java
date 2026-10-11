package co.caudal.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Done;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipCommandHandlersTest {

  private static final Instant NOW = Instant.parse("2026-10-11T13:00:00Z");
  private static final UUID ADMIN = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID MEMBERSHIP = UUID.randomUUID();
  private static final Caller CALLER = new Caller(ADMIN, AQUEDUCT, "BOARD_ADMIN", "127.0.0.1");

  private final MembershipAdminPort memberships = mock(MembershipAdminPort.class);
  private final UserAccountPort accounts = mock(UserAccountPort.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final GrantMembershipCommandHandler grant =
      new GrantMembershipCommandHandler(memberships, unitOfWork, clock);
  private final RevokeMembershipCommandHandler revoke =
      new RevokeMembershipCommandHandler(memberships, accounts, unitOfWork, clock);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void runTransactionsInline() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
    when(memberships.roleExists("BOARD_MEMBER")).thenReturn(true);
    when(memberships.userExists(USER)).thenReturn(true);
    when(memberships.insert(any(), any(), any(), any(), any(), any())).thenReturn(MEMBERSHIP);
  }

  private static ErrorCode codeOf(Throwable failure) {
    return ((DomainException) failure).code();
  }

  @Test
  void grantsFromNowWhenNoStartIsGiven() {
    MembershipView view =
        grant.handle(
            new GrantMembershipCommand(CALLER, USER, AQUEDUCT, "BOARD_MEMBER", null, null));

    assertThat(view.id()).isEqualTo(MEMBERSHIP);
    assertThat(view.validFrom()).isEqualTo(NOW);
    assertThat(view.grantedBy()).isEqualTo(ADMIN);
    verify(memberships).insert(USER, AQUEDUCT, "BOARD_MEMBER", NOW, null, ADMIN);
    assertThat(grant.commandType()).isEqualTo(GrantMembershipCommand.class);
  }

  @Test
  void refusesAnotherAqueductAnInvertedRangeAndAnUnknownRole() {
    assertThatThrownBy(
            () ->
                grant.handle(
                    new GrantMembershipCommand(
                        CALLER, USER, UUID.randomUUID(), "BOARD_MEMBER", null, null)))
        .extracting(MembershipCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.FORBIDDEN);
    assertThatThrownBy(
            () ->
                grant.handle(
                    new GrantMembershipCommand(
                        CALLER, USER, AQUEDUCT, "BOARD_MEMBER", NOW, NOW.minusSeconds(1))))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(
            () ->
                grant.handle(
                    new GrantMembershipCommand(CALLER, USER, AQUEDUCT, "KING", null, null)))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  void refusesAnUnknownAccountAndAnOverlap() {
    UUID stranger = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                grant.handle(
                    new GrantMembershipCommand(
                        CALLER, stranger, AQUEDUCT, "BOARD_MEMBER", null, null)))
        .extracting(MembershipCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.NOT_FOUND);
    when(memberships.overlaps(USER, AQUEDUCT, NOW, null)).thenReturn(true);
    assertThatThrownBy(
            () ->
                grant.handle(
                    new GrantMembershipCommand(CALLER, USER, AQUEDUCT, "BOARD_MEMBER", null, null)))
        .extracting(MembershipCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.CONFLICT);
  }

  private void givenMembership(UUID user, Instant validTo) {
    when(memberships.find(AQUEDUCT, MEMBERSHIP))
        .thenReturn(
            Optional.of(
                new MembershipView(
                    MEMBERSHIP, user, AQUEDUCT, "OPERATOR", NOW.minusSeconds(60), validTo, ADMIN)));
  }

  @Test
  void revokingClosesTheMembershipAndRaisesTheTokenVersion() {
    givenMembership(USER, null);

    assertThat(revoke.handle(new RevokeMembershipCommand(CALLER, MEMBERSHIP))).isEqualTo(Done.DONE);

    verify(memberships).close(MEMBERSHIP, NOW);
    verify(accounts).incrementTokenVersion(USER);
    assertThat(revoke.commandType()).isEqualTo(RevokeMembershipCommand.class);
  }

  @Test
  void revokingAnEndedMembershipChangesNothing() {
    givenMembership(USER, NOW.minusSeconds(1));

    revoke.handle(new RevokeMembershipCommand(CALLER, MEMBERSHIP));

    verify(memberships, never()).close(any(), any());
    verify(accounts, never()).incrementTokenVersion(any());
  }

  @Test
  void anAdministratorCannotEndTheirOwnMembershipNorOneOutsideTheAqueduct() {
    givenMembership(ADMIN, null);
    assertThatThrownBy(() -> revoke.handle(new RevokeMembershipCommand(CALLER, MEMBERSHIP)))
        .extracting(MembershipCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.CONFLICT);

    assertThatThrownBy(() -> revoke.handle(new RevokeMembershipCommand(CALLER, UUID.randomUUID())))
        .extracting(MembershipCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.NOT_FOUND);
  }
}
