package co.caudal.application.incident;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.SectorView;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.incident.TrackingCode;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.FieldValidationException;
import co.caudal.shared.text.SafeText;
import java.util.UUID;

/**
 * Registers an incident from the panel with source {@code OPERATOR} and status {@code REPORTED}.
 * The category comes from {@code DAMAGE_CATEGORY} and the sector must belong to the aqueduct. The
 * incident has no reporter, so nobody can follow it with a tracking code: the schema still wants a
 * hash, and a code nobody sees is generated only to fill it.
 *
 * @pattern P13 Command
 */
public class CreateIncidentCommandHandler
    implements CommandHandler<CreateIncidentCommand, IncidentView> {

  static final String CATALOG = "DAMAGE_CATEGORY";
  static final String SOURCE = "OPERATOR";
  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private final CatalogPort catalogs;
  private final NetworkPort network;
  private final IncidentPort incidents;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param catalogs damage categories
   * @param network sectors
   * @param incidents incidents
   * @param unitOfWork transaction boundary
   */
  public CreateIncidentCommandHandler(
      CatalogPort catalogs,
      NetworkPort network,
      IncidentPort incidents,
      UnitOfWorkPort unitOfWork) {
    this.catalogs = catalogs;
    this.network = network;
    this.incidents = incidents;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public IncidentView handle(CreateIncidentCommand command) {
    String description =
        SafeText.longText(
            "description",
            command.description(),
            FieldLimits.INCIDENT_DESCRIPTION_MIN,
            FieldLimits.INCIDENT_DESCRIPTION_MAX);
    String location =
        SafeText.optionalLine(
            "location_hint", command.locationHint(), FieldLimits.LOCATION_HINT_MAX);
    TrackingCode unusedCode = TrackingCode.generate();
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          UUID aqueductId = command.caller().aqueductId();
          if (!catalogs.isActiveCode(CATALOG, command.categoryCode(), aqueductId)) {
            throw new FieldValidationException("category_code", NOT_ALLOWED);
          }
          if (command.sectorId() != null
              && network.sector(command.sectorId()).filter(SectorView::active).isEmpty()) {
            throw new FieldValidationException("sector_id", NOT_ALLOWED);
          }
          UUID id =
              incidents.insert(
                  aqueductId,
                  new NewIncident(
                      SOURCE,
                      command.categoryCode(),
                      command.sectorId(),
                      description,
                      location,
                      unusedCode.hash(),
                      null,
                      command.caller().userId()));
          return incidents.find(id).orElseThrow();
        });
  }

  @Override
  public Class<CreateIncidentCommand> commandType() {
    return CreateIncidentCommand.class;
  }
}
