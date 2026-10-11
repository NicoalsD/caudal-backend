package co.caudal.application.minutes;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.DataAccessLogPort;
import co.caudal.application.port.out.DocumentRenderingPort;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Renders the PDF of some minutes (docs/API.md, section 13.3): a draft carries the mark "BORRADOR";
 * a final one is the same PDF whose hash was kept. Every download is written in the data access
 * log.
 */
public class GetMinutesPdfUseCase {

  static final String RESOURCE = "MINUTES_PDF";

  private final MinutesPort minutes;
  private final AqueductPort aqueducts;
  private final DocumentRenderingPort documents;
  private final DataAccessLogPort accessLog;
  private final PrivacyHasherPort hasher;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param minutes minutes
   * @param aqueducts name, demo mark and time zone
   * @param documents renders the PDF
   * @param accessLog data access log
   * @param hasher HMAC of the address
   * @param unitOfWork transaction boundary
   */
  public GetMinutesPdfUseCase(
      MinutesPort minutes,
      AqueductPort aqueducts,
      DocumentRenderingPort documents,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork) {
    this.minutes = minutes;
    this.aqueducts = aqueducts;
    this.documents = documents;
    this.accessLog = accessLog;
    this.hasher = hasher;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Renders the PDF.
   *
   * @param caller who downloads it
   * @param id the minutes
   * @return the PDF
   * @throws DomainException NOT_FOUND if they do not exist in the aqueduct
   */
  public MinutesPdf execute(Caller caller, UUID id) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          MinutesView found =
              minutes.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          AqueductRef aqueduct =
              aqueducts
                  .findById(caller.aqueductId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          boolean draft = !FinalizeMinutesCommandHandler.FINAL.equals(found.status());
          byte[] pdf =
              documents.minutes(
                  MinutesDocuments.of(
                      found,
                      aqueduct.name(),
                      aqueduct.demo(),
                      ZoneId.of(aqueduct.timezone()),
                      draft,
                      found.auditHeadHash()));
          accessLog.record(
              caller.aqueductId(),
              caller.userId(),
              RESOURCE,
              id.toString(),
              caller.ipAddress() == null ? null : hasher.hmac(caller.ipAddress()));
          return new MinutesPdf(
              pdf,
              found.pdfSha256(),
              "acta-" + found.periodFrom() + "-" + found.periodTo() + ".pdf");
        });
  }
}
