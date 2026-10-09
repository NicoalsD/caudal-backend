package co.caudal.application.auth;

import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PasswordResetGrantPort;

/**
 * The ports of {@link ResetPasswordCommandHandler}, grouped to keep its constructor small.
 *
 * @param accounts finds an account by username
 * @param grants one-time codes
 * @param memberships memberships, to find the aqueduct of the account
 * @param passwords hashing of passwords
 */
public record ResetPasswordPorts(
    LoginAccountPort accounts,
    PasswordResetGrantPort grants,
    MembershipPort memberships,
    PasswordHasherPort passwords) {}
