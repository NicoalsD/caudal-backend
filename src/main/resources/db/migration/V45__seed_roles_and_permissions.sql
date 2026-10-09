-- V45: roles, permissions and the role-permission matrix (docs/Actores-y-permisos.md, 3.2-3.3).
-- The code only checks permission codes; who has them lives here, in data.

INSERT INTO iam.roles (code, label_es, description_es) VALUES
  ('BOARD_ADMIN', 'Administración de la Junta', 'Presidente o administrador de la Junta. Gestiona usuarios, reglas, decisiones, actas y autorizaciones.'),
  ('BOARD_MEMBER', 'Miembro de la Junta', 'Propone borradores de reglas, genera y decide propuestas, publica horarios y genera actas.'),
  ('OPERATOR', 'Fontanero', 'Registra lecturas del tanque, la ejecución de los turnos y el cierre del día.'),
  ('PROJECT_TEAM', 'Equipo del proyecto', 'Evalúa la IA, importa historial simulado, registra dispositivos y revisa la auditoría.'),
  ('SUPPORT_ENTITY', 'Entidad de apoyo', 'Ve solo los resúmenes que la Junta autorizó y que siguen vigentes.');

INSERT INTO iam.permissions (code, description_es) VALUES
  ('USER_MANAGE', 'Gestionar usuarios y membresías.'),
  ('NETWORK_MANAGE', 'Crear y editar tanques, sectores y válvulas.'),
  ('NETWORK_VIEW', 'Ver tanques, sectores y válvulas.'),
  ('RULESET_VIEW', 'Ver las reglas vigentes e históricas.'),
  ('RULESET_DRAFT', 'Crear y editar borradores de reglas.'),
  ('RULESET_ACTIVATE', 'Activar una versión de reglas con motivo.'),
  ('READING_CREATE', 'Registrar lecturas del tanque.'),
  ('READING_VIEW', 'Ver lecturas y su estado de validación.'),
  ('READING_CORRECT', 'Corregir una lectura con motivo (el fontanero, solo las propias).'),
  ('DAY_CLOSE', 'Registrar la ejecución de turnos y el cierre del día.'),
  ('STATUS_VIEW', 'Ver estado, pronóstico y anomalías.'),
  ('FORECAST_RUN', 'Recalcular el pronóstico.'),
  ('ANOMALY_REVIEW', 'Confirmar o descartar una anomalía.'),
  ('PROPOSAL_GENERATE', 'Generar una propuesta de turnos.'),
  ('PROPOSAL_VIEW', 'Ver propuestas y su historial.'),
  ('PROPOSAL_DECIDE', 'Aprobar, modificar o rechazar una propuesta.'),
  ('SCHEDULE_PUBLISH', 'Publicar un horario aprobado.'),
  ('SCHEDULE_VIEW', 'Ver el horario publicado en el panel.'),
  ('INCIDENT_CREATE', 'Registrar un daño desde el panel.'),
  ('INCIDENT_VIEW', 'Ver reportes de daño.'),
  ('INCIDENT_MANAGE', 'Cambiar el estado de un incidente.'),
  ('MINUTES_GENERATE', 'Generar actas y su PDF.'),
  ('MINUTES_VIEW', 'Ver actas.'),
  ('SUMMARY_SHARE_MANAGE', 'Autorizar y revocar resúmenes para entidades.'),
  ('SUMMARY_VIEW', 'Ver resúmenes autorizados.'),
  ('EVALUATION_VIEW', 'Ver la evaluación IA frente a la estimación simple y la salud técnica.'),
  ('IMPORT_SIMULATED', 'Importar historial simulado (solo acueducto demo).'),
  ('DEVICE_MANAGE', 'Registrar dispositivos y sus claves.'),
  ('VALVE_COMMAND_MANUAL', 'Enviar un comando manual de válvula con motivo.'),
  ('AUDIT_VIEW', 'Ver la bitácora de auditoría y los eventos de seguridad.');

-- BOARD_ADMIN: every permission except IMPORT_SIMULATED.
INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'BOARD_ADMIN', code FROM iam.permissions WHERE code <> 'IMPORT_SIMULATED';

INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'BOARD_MEMBER', code FROM iam.permissions WHERE code IN (
  'NETWORK_VIEW', 'RULESET_VIEW', 'RULESET_DRAFT', 'READING_VIEW', 'READING_CORRECT',
  'STATUS_VIEW', 'FORECAST_RUN', 'ANOMALY_REVIEW', 'PROPOSAL_GENERATE', 'PROPOSAL_VIEW',
  'PROPOSAL_DECIDE', 'SCHEDULE_PUBLISH', 'SCHEDULE_VIEW', 'INCIDENT_CREATE', 'INCIDENT_VIEW',
  'INCIDENT_MANAGE', 'MINUTES_GENERATE', 'MINUTES_VIEW', 'SUMMARY_VIEW');

INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'OPERATOR', code FROM iam.permissions WHERE code IN (
  'NETWORK_VIEW', 'RULESET_VIEW', 'READING_CREATE', 'READING_VIEW', 'READING_CORRECT',
  'DAY_CLOSE', 'STATUS_VIEW', 'SCHEDULE_VIEW', 'INCIDENT_CREATE', 'INCIDENT_VIEW');

INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'PROJECT_TEAM', code FROM iam.permissions WHERE code IN (
  'NETWORK_VIEW', 'RULESET_VIEW', 'READING_VIEW', 'STATUS_VIEW', 'SCHEDULE_VIEW',
  'INCIDENT_CREATE', 'INCIDENT_VIEW', 'EVALUATION_VIEW', 'IMPORT_SIMULATED', 'DEVICE_MANAGE',
  'AUDIT_VIEW');

INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'SUPPORT_ENTITY', code FROM iam.permissions WHERE code IN ('SUMMARY_VIEW');
