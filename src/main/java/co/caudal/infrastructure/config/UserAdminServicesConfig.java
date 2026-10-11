package co.caudal.infrastructure.config;

import co.caudal.application.auth.SessionInvalidator;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.PasswordResetGrantPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.application.port.out.UserAdminPort;
import co.caudal.application.user.GrantMembershipCommandHandler;
import co.caudal.application.user.IssueResetGrantCommandHandler;
import co.caudal.application.user.ListMembershipsUseCase;
import co.caudal.application.user.ListUsersUseCase;
import co.caudal.application.user.ResetGrantSettings;
import co.caudal.application.user.RevokeMembershipCommandHandler;
import co.caudal.application.user.UpdateUserCommandHandler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Use cases of the administration of accounts and memberships by the Board. */
@Configuration(proxyBeanMethods = false)
class UserAdminServicesConfig {

  @Bean
  SignedCursorCodec signedCursorCodec(PrivacyHasherPort hasher) {
    return new SignedCursorCodec(hasher);
  }

  @Bean
  ListUsersUseCase listUsersUseCase(
      UserAdminPort users, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    return new ListUsersUseCase(users, cursors, unitOfWork);
  }

  @Bean
  UpdateUserCommandHandler updateUserCommandHandler(
      UserAdminPort users, SessionInvalidator sessions, UnitOfWorkPort unitOfWork, Clock clock) {
    return new UpdateUserCommandHandler(users, sessions, unitOfWork, clock);
  }

  @Bean
  ListMembershipsUseCase listMembershipsUseCase(
      MembershipAdminPort memberships, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    return new ListMembershipsUseCase(memberships, cursors, unitOfWork);
  }

  @Bean
  GrantMembershipCommandHandler grantMembershipCommandHandler(
      MembershipAdminPort memberships, UnitOfWorkPort unitOfWork, Clock clock) {
    return new GrantMembershipCommandHandler(memberships, unitOfWork, clock);
  }

  @Bean
  RevokeMembershipCommandHandler revokeMembershipCommandHandler(
      MembershipAdminPort memberships,
      UserAccountPort accounts,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new RevokeMembershipCommandHandler(memberships, accounts, unitOfWork, clock);
  }

  @Bean
  IssueResetGrantCommandHandler issueResetGrantCommandHandler(
      UserAdminPort users,
      PasswordResetGrantPort grants,
      ResetGrantSettings settings,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new IssueResetGrantCommandHandler(users, grants, settings, unitOfWork, clock);
  }
}
