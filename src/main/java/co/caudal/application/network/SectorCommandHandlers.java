package co.caudal.application.network;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.network.SectorHierarchy;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Handlers that add and change sectors (docs/API.md, sections 5.4 and 5.5). */
public final class SectorCommandHandlers {

  private SectorCommandHandlers() {}

  /**
   * Adds a sector; a code already used in the aqueduct is a conflict.
   *
   * @pattern P13 Command
   */
  public static final class Create implements CommandHandler<CreateSectorCommand, SectorView> {

    private final NetworkPort network;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param network the network
     * @param unitOfWork transaction boundary
     */
    public Create(NetworkPort network, UnitOfWorkPort unitOfWork) {
      this.network = network;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public SectorView handle(CreateSectorCommand command) {
      int households = command.householdsCount() == null ? 0 : command.householdsCount();
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            if (command.parentSectorId() != null
                && network.sector(command.parentSectorId()).isEmpty()) {
              throw new FieldValidationException("parent_sector_id", "FIELD_VALUE_NOT_ALLOWED");
            }
            return network
                .insertSector(
                    command.caller().aqueductId(),
                    command.code(),
                    command.name().strip(),
                    command.parentSectorId(),
                    households)
                .orElseThrow(
                    () -> new DomainException(ErrorCode.CONFLICT, Map.of("field", "code")));
          });
    }

    @Override
    public Class<CreateSectorCommand> commandType() {
      return CreateSectorCommand.class;
    }
  }

  /**
   * Changes the name, the households, the state or the parent of a sector. Making a sector a region
   * (a root) or moving it keeps the tree free of cycles and orphans.
   *
   * @pattern P13 Command
   */
  public static final class Update implements CommandHandler<UpdateSectorCommand, SectorView> {

    private final NetworkPort network;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param network the network
     * @param unitOfWork transaction boundary
     */
    public Update(NetworkPort network, UnitOfWorkPort unitOfWork) {
      this.network = network;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public SectorView handle(UpdateSectorCommand command) {
      if (command.parentSectorId() != null && Boolean.TRUE.equals(command.region())) {
        throw new FieldValidationException("is_region", "FIELD_VALUE_NOT_ALLOWED");
      }
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            SectorView current =
                network
                    .sector(command.sectorId())
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            boolean active = command.active() == null ? current.active() : command.active();
            UUID parent = newParent(command, current);
            checkTree(current, parent);
            return network.updateSector(
                current.id(),
                command.name() == null ? current.name() : command.name().strip(),
                command.householdsCount() == null
                    ? current.householdsCount()
                    : command.householdsCount(),
                active,
                parent);
          });
    }

    /** The parent after the change: a region has none, and no request leaves it as it is. */
    private static UUID newParent(UpdateSectorCommand command, SectorView current) {
      if (Boolean.TRUE.equals(command.region())) {
        return null;
      }
      return command.parentSectorId() == null ? current.parentSectorId() : command.parentSectorId();
    }

    /** The tree stays valid (P08): no cycles and no orphans. */
    private void checkTree(SectorView current, UUID parent) {
      Map<UUID, UUID> parentOf = new HashMap<>();
      for (SectorView sector : network.sectors()) {
        parentOf.put(sector.id(), sector.parentSectorId());
      }
      if (!Objects.equals(parent, current.parentSectorId())) {
        SectorHierarchy.requireValidMove(current.id(), parent, parentOf);
      }
    }

    @Override
    public Class<UpdateSectorCommand> commandType() {
      return UpdateSectorCommand.class;
    }
  }
}
