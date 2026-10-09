package co.caudal.application.auth;

import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.LoginAttemptPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;

/**
 * The collaborators of {@link LoginUseCase}, grouped so the constructor stays readable.
 *
 * @param accounts login state of accounts
 * @param attempts login attempts
 * @param memberships memberships
 * @param passwords password hashing
 * @param hasher keyed hash for IPs and usernames
 * @param accessTokens access token issuer
 * @param refreshTokens refresh token service
 * @param events security events
 * @param unitOfWork transaction boundary
 */
public record LoginPorts(
    LoginAccountPort accounts,
    LoginAttemptPort attempts,
    MembershipPort memberships,
    PasswordHasherPort passwords,
    PrivacyHasherPort hasher,
    AccessTokenIssuerPort accessTokens,
    RefreshTokenService refreshTokens,
    SecurityEventPort events,
    UnitOfWorkPort unitOfWork) {}
