package co.caudal.application.publication;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.PublicSchedulePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Shows the published schedule of a day to anyone, without login (docs/API.md, section 15.1). The
 * day is today in the time zone of the aqueduct unless one is asked for.
 */
public class GetPublicScheduleUseCase {

  private final AqueductPort aqueducts;
  private final PublicSchedulePort schedules;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param aqueducts aqueducts by slug
   * @param schedules the public reader (a proxy)
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GetPublicScheduleUseCase(
      AqueductPort aqueducts,
      PublicSchedulePort schedules,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.aqueducts = aqueducts;
    this.schedules = schedules;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Reads the schedule.
   *
   * @param slug the aqueduct
   * @param day the day, or null for today
   * @return the schedule
   * @throws DomainException NOT_FOUND for an unknown slug, a closed page or no publication
   */
  public PublicSchedule execute(String slug, LocalDate day) {
    AqueductRef aqueduct =
        aqueducts.findBySlug(slug).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    LocalDate wanted =
        day != null ? day : LocalDate.ofInstant(clock.instant(), ZoneId.of(aqueduct.timezone()));
    return unitOfWork.executeAs(
        new SessionScope(null, aqueduct.id()),
        () -> schedules.find(wanted).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
  }
}
