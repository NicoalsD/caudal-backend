package co.caudal.application.incident;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.SectorView;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.publication.PlainTextChannel;
import co.caudal.application.publication.ReportReceivedNotice;
import co.caudal.domain.incident.TrackingCode;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import co.caudal.shared.text.SafeText;

/**
 * Stores a damage report from the public page. The person gets a tracking code that is shown only
 * once; only its hash is kept, and the address only as an HMAC. Nothing about the person is stored.
 * The {@link PublicFormGuard} decides first whether the report is stored at all.
 *
 * @pattern P13 Command
 */
public class DamageReportCommandHandler
    implements CommandHandler<DamageReportCommand, DamageReportReceipt> {

  static final String CATALOG = "DAMAGE_CATEGORY";
  static final String SOURCE = "PUBLIC_FORM";
  static final String REPORTED = "REPORTED";
  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private final AqueductPort aqueducts;
  private final CatalogPort catalogs;
  private final NetworkPort network;
  private final IncidentPort incidents;
  private final PrivacyHasherPort hasher;
  private final UnitOfWorkPort unitOfWork;
  private final PublicFormGuard guard;

  /**
   * Creates the handler.
   *
   * @param aqueducts aqueducts by slug
   * @param catalogs damage categories
   * @param network sectors
   * @param incidents incidents
   * @param hasher HMAC of the address
   * @param unitOfWork transaction boundary
   * @param guard limits, minimum time and hidden field of the form
   */
  public DamageReportCommandHandler(
      AqueductPort aqueducts,
      CatalogPort catalogs,
      NetworkPort network,
      IncidentPort incidents,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork,
      PublicFormGuard guard) {
    this.aqueducts = aqueducts;
    this.catalogs = catalogs;
    this.network = network;
    this.incidents = incidents;
    this.hasher = hasher;
    this.unitOfWork = unitOfWork;
    this.guard = guard;
  }

  @Override
  public DamageReportReceipt handle(DamageReportCommand command) {
    AqueductRef aqueduct =
        aqueducts
            .findBySlug(command.aqueductSlug())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (!guard.admit(
        aqueduct.id(), command.ipAddress(), command.website(), command.formStartedAt())) {
      return receipt(TrackingCode.generate());
    }
    String description =
        SafeText.longText(
            "description",
            command.description(),
            FieldLimits.INCIDENT_DESCRIPTION_MIN,
            FieldLimits.INCIDENT_DESCRIPTION_MAX);
    String location =
        SafeText.optionalLine(
            "location_hint", command.locationHint(), FieldLimits.LOCATION_HINT_MAX);
    TrackingCode code = TrackingCode.generate();
    unitOfWork.executeAs(
        new SessionScope(null, aqueduct.id()),
        () -> {
          if (!catalogs.isActiveCode(CATALOG, command.categoryCode(), aqueduct.id())) {
            throw new FieldValidationException("category_code", NOT_ALLOWED);
          }
          if (command.sectorId() != null
              && network.sector(command.sectorId()).filter(SectorView::active).isEmpty()) {
            throw new FieldValidationException("sector_id", NOT_ALLOWED);
          }
          return incidents.insert(
              aqueduct.id(),
              new NewIncident(
                  SOURCE,
                  command.categoryCode(),
                  command.sectorId(),
                  description,
                  location,
                  code.hash(),
                  command.ipAddress() == null ? null : hasher.hmac(command.ipAddress())));
        });
    return receipt(code);
  }

  /** The same answer for a stored report and for one the hidden field stopped. */
  private static DamageReportReceipt receipt(TrackingCode code) {
    return new DamageReportReceipt(
        code.value(),
        REPORTED,
        new ReportReceivedNotice(new PlainTextChannel(), code.value()).publish());
  }

  @Override
  public Class<DamageReportCommand> commandType() {
    return DamageReportCommand.class;
  }
}
