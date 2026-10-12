-- V53: permission to change the retention periods (docs/API.md, section 18.3).
-- Only the Board administrator: the periods decide how long evidence is kept, so the team that
-- reviews the audit log must not be able to shorten it. Granted to BOARD_ADMIN only.

INSERT INTO iam.permissions (code, description_es)
VALUES ('RETENTION_MANAGE', 'Cambiar los plazos de retención de los datos.');

INSERT INTO iam.role_permissions (role_code, permission_code)
VALUES ('BOARD_ADMIN', 'RETENTION_MANAGE');
