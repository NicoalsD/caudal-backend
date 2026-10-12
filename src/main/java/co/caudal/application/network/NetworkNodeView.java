package co.caudal.application.network;

import java.util.List;
import java.util.UUID;

/**
 * A part of the network tree as the API shows it.
 *
 * @param kind {@code SECTOR} or {@code VALVE}
 * @param id the part
 * @param code its code
 * @param name its name
 * @param active whether it is in use
 * @param households households served by the part and everything under it
 * @param activeValves active valves of the part and everything under it
 * @param children the parts it holds
 */
public record NetworkNodeView(
    String kind,
    UUID id,
    String code,
    String name,
    boolean active,
    int households,
    int activeValves,
    List<NetworkNodeView> children) {

  /** Copies the children so the view cannot change. */
  public NetworkNodeView {
    children = List.copyOf(children);
  }
}
