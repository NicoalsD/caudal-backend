package co.caudal.api.dto.response;

import java.util.List;

/**
 * The commands a device must run now (docs/API.md, section 16.2).
 *
 * @param items up to 100 commands, ordered by {@code not_before}
 */
public record ValveCommandsResponse(List<ValveCommandItemResponse> items) {}
