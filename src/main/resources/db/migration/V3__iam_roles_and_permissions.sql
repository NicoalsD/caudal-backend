-- V3: iam.roles, iam.permissions, iam.role_permissions.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.roles (
  code                       varchar(30) NOT NULL,
  label_es                   varchar(60) NOT NULL,
  description_es             varchar(255) NOT NULL,
  CONSTRAINT roles_pk PRIMARY KEY (code),
  CONSTRAINT roles_code_values CHECK (code IN ('BOARD_ADMIN', 'BOARD_MEMBER', 'OPERATOR', 'PROJECT_TEAM', 'SUPPORT_ENTITY'))
);

COMMENT ON TABLE iam.roles IS 'Catálogo de roles del sistema.';
COMMENT ON COLUMN iam.roles.code IS '[P] Código del rol.';
COMMENT ON COLUMN iam.roles.label_es IS '[P] Nombre visible en español.';
COMMENT ON COLUMN iam.roles.description_es IS '[P] Descripción.';

REVOKE ALL ON iam.roles FROM PUBLIC;
GRANT SELECT ON iam.roles TO caudal_app;

CREATE TABLE iam.permissions (
  code                       varchar(${permission_code_max}) NOT NULL,
  description_es             varchar(255) NOT NULL,
  CONSTRAINT permissions_pk PRIMARY KEY (code),
  CONSTRAINT permissions_code_format CHECK (code ~ '${permission_code_pattern}')
);

COMMENT ON TABLE iam.permissions IS 'Permisos finos que el código verifica.';
COMMENT ON COLUMN iam.permissions.code IS '[P] Código del permiso (READING_CREATE, RULESET_ACTIVATE, ...).';
COMMENT ON COLUMN iam.permissions.description_es IS '[P] Descripción.';

REVOKE ALL ON iam.permissions FROM PUBLIC;
GRANT SELECT ON iam.permissions TO caudal_app;

CREATE TABLE iam.role_permissions (
  role_code                  varchar(30),
  permission_code            varchar(${permission_code_max}),
  CONSTRAINT role_permissions_pk PRIMARY KEY (role_code, permission_code)
);

COMMENT ON TABLE iam.role_permissions IS 'Matriz rol-permiso guardada en datos (sin quemar en código).';
COMMENT ON COLUMN iam.role_permissions.role_code IS '[P] Rol.';
COMMENT ON COLUMN iam.role_permissions.permission_code IS '[P] Permiso.';

ALTER TABLE iam.role_permissions ADD CONSTRAINT role_permissions_role_code_fk
  FOREIGN KEY (role_code) REFERENCES iam.roles (code) ON DELETE RESTRICT;
ALTER TABLE iam.role_permissions ADD CONSTRAINT role_permissions_permission_code_fk
  FOREIGN KEY (permission_code) REFERENCES iam.permissions (code) ON DELETE RESTRICT;

CREATE INDEX role_permissions_permission_code_idx ON iam.role_permissions (permission_code);

REVOKE ALL ON iam.role_permissions FROM PUBLIC;
GRANT SELECT ON iam.role_permissions TO caudal_app;
