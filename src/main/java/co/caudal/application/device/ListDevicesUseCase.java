package co.caudal.application.device;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.List;

/**
 * Lists the field devices of the aqueduct, the newest registration first (docs/API.md, section
 * 16.9). Row level security limits the aqueduct; the port never reads key material.
 */
public class ListDevicesUseCase {

  private final DevicePort devices;
  private final DeviceCursor cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param devices devices
   * @param cursors cursor codec of devices
   * @param unitOfWork transaction boundary
   */
  public ListDevicesUseCase(DevicePort devices, DeviceCursor cursors, UnitOfWorkPort unitOfWork) {
    this.devices = devices;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param limit page size, from 1 to 100
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws co.caudal.shared.error.DomainException with {@code CURSOR_INVALID} for an altered
   *     cursor, or {@code VALIDATION_ERROR} for a size out of range
   */
  public Page<DeviceListItemView> execute(Caller caller, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    DeviceCursor.Position position = cursor == null ? null : cursors.decode(cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<DeviceListItemView> rows =
              devices.list(
                  position == null ? null : position.createdAt(),
                  position == null ? null : position.id(),
                  size + 1);
          if (rows.size() <= size) {
            return new Page<>(rows, null);
          }
          List<DeviceListItemView> items = List.copyOf(rows.subList(0, size));
          DeviceListItemView last = items.get(size - 1);
          return new Page<>(items, cursors.encode(last.createdAt(), last.id()));
        });
  }
}
