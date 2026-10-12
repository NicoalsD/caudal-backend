-- V52: a valve has at most one active command (PENDING or DELIVERED) per not_before
-- (docs/Protocolo-de-dispositivos.md, section 5.5). Commands that expire, are confirmed or cancelled
-- leave the index, so the same moment can be used again later.
CREATE UNIQUE INDEX valve_commands_active_not_before_uq ON devices.valve_commands (valve_id, not_before)
  WHERE status IN ('PENDING', 'DELIVERED');
