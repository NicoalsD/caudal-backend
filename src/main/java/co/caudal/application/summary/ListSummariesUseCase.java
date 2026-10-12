package co.caudal.application.summary;

import co.caudal.application.auth.Caller;
import co.caudal.application.minutes.MinutesAssembler;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.DataAccessLogPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.MinutesSourcePort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SummarySharePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.minutes.Minutes;
import co.caudal.domain.minutes.MinutesSection;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lists the summaries the Board has shared (docs/API.md, section 13.6; docs/Reglas-de-negocio.md,
 * section 16.3). An entity ({@code SUPPORT_ENTITY}) sees only its own authorizations that are not
 * revoked, not expired and whose membership is current; a {@code MINUTES} authorization also needs
 * a final minutes covering the period. Each summary is aggregated counts, and each one an entity
 * reads is written to the access log with its authorization. The Board sees every current
 * authorization of the aqueduct, without writing to the log.
 */
public class ListSummariesUseCase {

  static final String LIST = "summary";
  static final String SUPPORT_ENTITY = "SUPPORT_ENTITY";
  private static final String MINUTES = "MINUTES";

  private final SummarySharePort shares;
  private final MembershipPort memberships;
  private final MinutesPort finalMinutes;
  private final MinutesSourcePort sources;
  private final AqueductPort aqueducts;
  private final DataAccessLogPort accessLog;
  private final PrivacyHasherPort hasher;
  private final SignedCursorCodec cursors;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param shares authorizations
   * @param memberships memberships of the caller
   * @param finalMinutes final minutes
   * @param sources the records of a period
   * @param aqueducts the time zone of the aqueduct
   * @param accessLog reads of the entities
   * @param hasher HMAC of the address
   * @param cursors signs the cursor
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  @SuppressWarnings("checkstyle:ParameterNumber")
  public ListSummariesUseCase(
      SummarySharePort shares,
      MembershipPort memberships,
      MinutesPort finalMinutes,
      MinutesSourcePort sources,
      AqueductPort aqueducts,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      SignedCursorCodec cursors,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.shares = shares;
    this.memberships = memberships;
    this.finalMinutes = finalMinutes;
    this.sources = sources;
    this.aqueducts = aqueducts;
    this.accessLog = accessLog;
    this.hasher = hasher;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param from first day requested, or null to use the period of each authorization
   * @param to last day requested, or null; both or none
   * @param limit page size, from 1 to 100
   * @param cursor cursor of the page, or null for the first
   * @return the page; it may hold fewer items than the limit when some authorization is hidden
   * @throws DomainException with {@code VALIDATION_ERROR} for a half period or an inverted one, and
   *     {@code CURSOR_INVALID} for an altered cursor
   */
  public Page<SummaryView> execute(
      Caller caller, LocalDate from, LocalDate to, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    if ((from == null) != (to == null) || (from != null && to.isBefore(from))) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    UUID before = cursor == null ? null : position(cursors.decode(LIST, cursor));
    Instant now = clock.instant();
    boolean entity = SUPPORT_ENTITY.equals(caller.role());
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          if (entity && !isCurrentSupportEntity(caller, now)) {
            return new Page<>(List.of(), null);
          }
          List<SummaryShareView> rows =
              shares.listActive(entity ? caller.userId() : null, from, to, now, before, size + 1);
          List<SummaryShareView> window = rows.subList(0, Math.min(size, rows.size()));
          ZoneId zone = zone(caller.aqueductId());
          List<SummaryView> items = new ArrayList<>();
          for (SummaryShareView share : window) {
            visible(caller, share, from, to, zone, entity).ifPresent(items::add);
          }
          String next =
              rows.size() > size
                  ? cursors.encode(LIST, window.get(window.size() - 1).id().toString())
                  : null;
          return new Page<>(List.copyOf(items), next);
        });
  }

  /** The summary of one authorization, or empty when its minutes condition is not met. */
  private Optional<SummaryView> visible(
      Caller caller,
      SummaryShareView share,
      LocalDate from,
      LocalDate to,
      ZoneId zone,
      boolean entity) {
    LocalDate periodFrom = from != null ? from : share.periodFrom();
    LocalDate periodTo = to != null ? to : share.periodTo();
    if (MINUTES.equals(share.scope()) && !finalMinutes.hasFinalCovering(periodFrom, periodTo)) {
      return Optional.empty();
    }
    if (entity) {
      String ip = caller.ipAddress() == null ? null : hasher.hmac(caller.ipAddress());
      accessLog.recordSummary(caller.aqueductId(), caller.userId(), share.id(), ip);
    }
    return Optional.of(
        new SummaryView(
            share.id(), share.scope(), periodFrom, periodTo, sections(periodFrom, periodTo, zone)));
  }

  private boolean isCurrentSupportEntity(Caller caller, Instant now) {
    return memberships.findActive(caller.userId(), now).stream()
        .anyMatch(
            membership ->
                caller.aqueductId().equals(membership.aqueductId())
                    && SUPPORT_ENTITY.equals(membership.role()));
  }

  private ZoneId zone(UUID aqueductId) {
    return aqueducts
        .findById(aqueductId)
        .map(aqueduct -> ZoneId.of(aqueduct.timezone()))
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
  }

  /** Counts the records of the period by section; the text of the records is not kept. */
  private SummarySections sections(LocalDate from, LocalDate to, ZoneId zone) {
    Minutes minutes =
        MinutesAssembler.assemble(
            from,
            to,
            sources.facts(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant()));
    return new SummarySections(
        minutes.section(MinutesSection.OBSERVED).size(),
        minutes.section(MinutesSection.ESTIMATED).size(),
        minutes.section(MinutesSection.INFERRED).size(),
        minutes.section(MinutesSection.CONFIRMED).size());
  }

  private static UUID position(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException bad) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
