package co.caudal.application.incident;

import co.caudal.application.catalog.CatalogItemView;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.incident.IncidentState;
import co.caudal.domain.incident.TrackingCode;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;

/**
 * Lets the person who reported damage follow it with the code (docs/API.md, section 15.5). The code
 * is looked up by its hash; an unknown code gets the same generic NOT_FOUND as any other.
 */
public class TrackDamageReportUseCase {

  private final IncidentPort incidents;
  private final CatalogPort catalogs;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param incidents incidents
   * @param catalogs labels of the categories
   * @param unitOfWork transaction boundary
   */
  public TrackDamageReportUseCase(
      IncidentPort incidents, CatalogPort catalogs, UnitOfWorkPort unitOfWork) {
    this.incidents = incidents;
    this.catalogs = catalogs;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads the status of a report.
   *
   * @param typed the code as the person typed it
   * @return what the person may see
   * @throws FieldValidationException for a code with the wrong format
   * @throws DomainException NOT_FOUND for an unknown code
   */
  public PublicReportStatus execute(String typed) {
    TrackingCode code =
        TrackingCode.parse(typed)
            .orElseThrow(
                () -> new FieldValidationException("tracking_code", "FIELD_FORMAT_INVALID"));
    return unitOfWork.execute(
        () -> {
          TrackedReport report =
              incidents
                  .findByTrackingHash(code.hash())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          String category =
              catalogs.activeItems(DamageReportCommandHandler.CATALOG, report.aqueductId()).stream()
                  .filter(item -> item.code().equals(report.categoryCode()))
                  .map(CatalogItemView::labelEs)
                  .findFirst()
                  .orElse(report.categoryCode());
          IncidentState state = IncidentState.of(report.status());
          return new PublicReportStatus(
              state.code(), state.publicLabel(), category, report.reportedAt(), report.updatedAt());
        });
  }
}
