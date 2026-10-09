package co.caudal.application.auth;

import java.util.List;

/**
 * Answer of {@code GET /auth/me}.
 *
 * @param profile the account
 * @param memberships memberships in force
 * @param permissions permission codes of the active role, sorted
 */
public record CurrentUser(
    UserProfile profile, List<MembershipSummary> memberships, List<String> permissions) {}
