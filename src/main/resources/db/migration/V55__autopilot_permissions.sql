-- V55: permissions of the autopilot and the assistant (v2; docs/Actores-y-permisos.md, 3.2-3.3).
-- BOARD_ADMIN does not receive them by the rule of V45 (it only covered the permissions that
-- existed then), so each grant is explicit.

-- B203: new permissions and who has them.
INSERT INTO iam.permissions (code, description_es) VALUES
  ('AUTOPILOT_CONFIGURE', 'Cambiar el modo del piloto automático y sus parámetros.'),
  ('ASSISTANT_USE', 'Ver las decisiones del piloto y preguntar al asistente.'),
  ('REPORT_VIEW', 'Ver los reportes del periodo y su narrativa.');

INSERT INTO iam.role_permissions (role_code, permission_code) VALUES
  ('BOARD_ADMIN', 'AUTOPILOT_CONFIGURE'),
  ('BOARD_ADMIN', 'ASSISTANT_USE'),
  ('BOARD_ADMIN', 'REPORT_VIEW'),
  ('BOARD_MEMBER', 'ASSISTANT_USE'),
  ('BOARD_MEMBER', 'REPORT_VIEW'),
  ('OPERATOR', 'ASSISTANT_USE'),
  ('PROJECT_TEAM', 'ASSISTANT_USE'),
  ('PROJECT_TEAM', 'REPORT_VIEW');

-- B204: the plumber can send manual commands and accept recommendations.
INSERT INTO iam.role_permissions (role_code, permission_code)
VALUES ('OPERATOR', 'VALVE_COMMAND_MANUAL');
