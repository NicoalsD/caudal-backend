package co.caudal.domain.network;

import java.util.List;
import java.util.UUID;

/**
 * A part of the water network of an aqueduct: a sector, which may hold other sectors and valves, or
 * a valve. Code that walks the network treats both the same way.
 *
 * @pattern P08 Composite
 */
public sealed interface NetworkNode permits SectorNode, ValveNode {

  /**
   * The id of the part.
   *
   * @return the id
   */
  UUID id();

  /**
   * Its short code.
   *
   * @return the code
   */
  String code();

  /**
   * Its name.
   *
   * @return the name
   */
  String name();

  /**
   * Whether it is in use.
   *
   * @return true if active
   */
  boolean active();

  /**
   * The parts it holds; a valve holds none.
   *
   * @return the children
   */
  List<NetworkNode> children();

  /**
   * Households served by this part and everything under it, counting only active sectors.
   *
   * @return the households
   */
  int households();

  /**
   * The active valves of this part and everything under it.
   *
   * @return the valves, in network order
   */
  List<ValveNode> activeValves();
}
