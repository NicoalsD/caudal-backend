package co.caudal.application.demo;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.user.NormalizedPassword;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates the demo accounts of the demo aqueduct when they do not exist. The password comes from
 * the environment, never from the code. Accounts that already exist are left untouched.
 */
public class DemoUsersSeeder {

  /**
   * One demo account.
   *
   * @param username the username
   * @param fullName the full name
   * @param role the role code
   */
  public record DemoUser(String username, String fullName, String role) {}

  /** The accounts of the demo, the first one grants the memberships of the others. */
  public static final List<DemoUser> USERS =
      List.of(
          new DemoUser("junta.admin", "Administración de la Junta", "BOARD_ADMIN"),
          new DemoUser("junta.miembro", "Miembro de la Junta", "BOARD_MEMBER"),
          new DemoUser("fontanero", "Fontanero de la vereda", "OPERATOR"),
          new DemoUser("equipo.proyecto", "Equipo del proyecto", "PROJECT_TEAM"));

  private final AqueductPort aqueducts;
  private final UserAccountPort accounts;
  private final MembershipAdminPort memberships;
  private final PasswordHasherPort passwords;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the seeder.
   *
   * @param aqueducts aqueducts
   * @param accounts accounts
   * @param memberships memberships
   * @param passwords password hashing
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public DemoUsersSeeder(
      AqueductPort aqueducts,
      UserAccountPort accounts,
      MembershipAdminPort memberships,
      PasswordHasherPort passwords,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.aqueducts = aqueducts;
    this.accounts = accounts;
    this.memberships = memberships;
    this.passwords = passwords;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Creates the missing demo accounts.
   *
   * @param aqueductSlug slug of the demo aqueduct
   * @param password the password of every demo account
   * @return how many accounts were created
   */
  public int seed(String aqueductSlug, String password) {
    Optional<AqueductRef> aqueduct = aqueducts.findBySlug(aqueductSlug);
    if (aqueduct.isEmpty() || !aqueduct.get().demo()) {
      return 0;
    }
    UUID aqueductId = aqueduct.get().id();
    // One hash per account: each gets its own random salt.
    NormalizedPassword normalized = NormalizedPassword.of(password);
    UUID[] granter = new UUID[1];
    int created = 0;
    for (DemoUser user : USERS) {
      String hash = passwords.hash(normalized);
      SessionScope scope = new SessionScope(granter[0], aqueductId);
      boolean isNew = unitOfWork.executeAs(scope, () -> createOne(user, hash, aqueductId, granter));
      if (isNew) {
        created++;
      }
    }
    return created;
  }

  private boolean createOne(DemoUser user, String hash, UUID aqueductId, UUID[] granter) {
    Optional<UUID> id = accounts.insertIfAbsent(user.username(), user.fullName(), hash, granter[0]);
    if (id.isEmpty()) {
      return false;
    }
    Instant now = clock.instant();
    accounts.replacePassword(id.get(), hash, now, false);
    UUID grantedBy = granter[0] == null ? id.get() : granter[0];
    memberships.insert(id.get(), aqueductId, user.role(), now, null, grantedBy);
    if (granter[0] == null) {
      granter[0] = id.get();
    }
    return true;
  }
}
