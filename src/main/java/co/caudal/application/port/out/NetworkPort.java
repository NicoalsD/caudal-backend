package co.caudal.application.port.out;

import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.network.ValveView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the physical network (tanks, sectors, valves). Runs inside a transaction opened
 * with {@link UnitOfWorkPort#executeAs}: the tables have row level security.
 */
public interface NetworkPort {

  /**
   * Lists the tanks of the aqueduct of the session.
   *
   * @return the tanks ordered by name
   */
  List<TankView> tanks();

  /**
   * Finds a tank.
   *
   * @param id the tank
   * @return the tank, or empty if it is not in the aqueduct of the session
   */
  Optional<TankView> tank(UUID id);

  /**
   * Creates a tank in the aqueduct of the session.
   *
   * @param aqueductId the aqueduct
   * @param name the name
   * @param gaugeMin minimum of the gauge
   * @param gaugeMax maximum of the gauge
   * @param gaugeStep reading precision
   * @param capacityLiters capacity, or null
   * @return the tank
   */
  TankView insertTank(
      UUID aqueductId,
      String name,
      BigDecimal gaugeMin,
      BigDecimal gaugeMax,
      BigDecimal gaugeStep,
      Integer capacityLiters);

  /**
   * Lists the sectors of the aqueduct of the session.
   *
   * @return the sectors ordered by code
   */
  List<SectorView> sectors();

  /**
   * Finds a sector.
   *
   * @param id the sector
   * @return the sector, or empty
   */
  Optional<SectorView> sector(UUID id);

  /**
   * Lists the valves of a sector.
   *
   * @param sectorId the sector
   * @return the valves ordered by code
   */
  List<ValveView> valves(UUID sectorId);

  /**
   * Adds a sector unless its code is taken in the aqueduct.
   *
   * @param aqueductId the aqueduct of the session
   * @param code the code
   * @param name the name
   * @param parentSectorId the parent sector, or null
   * @param householdsCount households it serves
   * @return the sector, or empty if the code already exists
   */
  Optional<SectorView> insertSector(
      UUID aqueductId, String code, String name, UUID parentSectorId, int householdsCount);

  /**
   * Saves the changes of a sector.
   *
   * @param sectorId the sector
   * @param name the name
   * @param householdsCount households it serves
   * @param active whether it is in use
   * @return the sector
   */
  SectorView updateSector(UUID sectorId, String name, int householdsCount, boolean active);

  /**
   * Finds a valve of a sector of the aqueduct of the session.
   *
   * @param valveId the valve
   * @return the valve, or empty
   */
  Optional<ValveView> valve(UUID valveId);

  /**
   * Adds a valve unless its code is taken in the sector.
   *
   * @param sectorId the sector
   * @param code the code
   * @param name the name
   * @param locationHint where to find it, or null
   * @param motorized whether a device can move it
   * @param failSafePosition position when control is lost
   * @return the valve, or empty if the code already exists in the sector
   */
  Optional<ValveView> insertValve(
      UUID sectorId,
      String code,
      String name,
      String locationHint,
      boolean motorized,
      String failSafePosition);

  /**
   * Saves the changes of a valve.
   *
   * @param valve the valve with its new values
   * @return the valve
   */
  ValveView updateValve(ValveView valve);
}
