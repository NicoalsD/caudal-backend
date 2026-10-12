package co.caudal.application.publication;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.DocumentRenderingPort;
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
  private final DocumentRenderingPort documents;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param aqueducts aqueducts by slug
   * @param schedules the public reader (a proxy)
   * @param documents renders the poster
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GetPublicScheduleUseCase(
      AqueductPort aqueducts,
      PublicSchedulePort schedules,
      DocumentRenderingPort documents,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.aqueducts = aqueducts;
    this.schedules = schedules;
    this.documents = documents;
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
    AqueductRef aqueduct = aqueduct(slug);
    return find(aqueduct, day);
  }

  /**
   * Renders the poster of the schedule again; it is the same PDF whose hash was kept.
   *
   * @param slug the aqueduct
   * @param day the day, or null for today
   * @return the PDF and the stored hash
   * @throws DomainException NOT_FOUND as {@link #execute}
   */
  public PosterFile poster(String slug, LocalDate day) {
    AqueductRef aqueduct = aqueduct(slug);
    PublicSchedule schedule = find(aqueduct, day);
    byte[] pdf =
        documents.poster(
            SchedulePoster.of(
                schedule.aqueductName(),
                schedule.demo(),
                schedule.serviceDate(),
                ZoneId.of(aqueduct.timezone()),
                schedule.lines()));
    return new PosterFile(pdf, schedule.posterSha256());
  }

  private AqueductRef aqueduct(String slug) {
    return aqueducts.findBySlug(slug).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
  }

  private PublicSchedule find(AqueductRef aqueduct, LocalDate day) {
    LocalDate wanted =
        day != null ? day : LocalDate.ofInstant(clock.instant(), ZoneId.of(aqueduct.timezone()));
    return unitOfWork.executeAs(
        new SessionScope(null, aqueduct.id()),
        () -> schedules.find(wanted).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
  }
}
