-- V4: iam.memberships.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.memberships (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  user_id                    uuid NOT NULL,
  aqueduct_id                uuid NOT NULL,
  role_code                  varchar(30) NOT NULL,
  valid_from                 timestamptz NOT NULL DEFAULT now(),
  valid_to                   timestamptz,
  granted_by                 uuid NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT memberships_pk PRIMARY KEY (id),
  CONSTRAINT memberships_valid_to_rule CHECK (valid_to > valid_from),
  CONSTRAINT memberships_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE iam.memberships IS 'Pertenencia de un usuario a un acueducto con un rol y una vigencia.';
COMMENT ON COLUMN iam.memberships.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.memberships.user_id IS '[I] Usuario.';
COMMENT ON COLUMN iam.memberships.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN iam.memberships.role_code IS '[I] Rol en ese acueducto.';
COMMENT ON COLUMN iam.memberships.valid_from IS '[I] Inicio de vigencia.';
COMMENT ON COLUMN iam.memberships.valid_to IS '[I] Fin de vigencia.';
COMMENT ON COLUMN iam.memberships.granted_by IS '[I] Quién otorgó la membresía.';
COMMENT ON COLUMN iam.memberships.created_at IS '[I] Creación.';

ALTER TABLE iam.memberships ADD CONSTRAINT memberships_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE iam.memberships ADD CONSTRAINT memberships_role_code_fk
  FOREIGN KEY (role_code) REFERENCES iam.roles (code) ON DELETE RESTRICT;
ALTER TABLE iam.memberships ADD CONSTRAINT memberships_granted_by_fk
  FOREIGN KEY (granted_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX memberships_user_id_idx ON iam.memberships (user_id);
CREATE INDEX memberships_aqueduct_id_idx ON iam.memberships (aqueduct_id);
CREATE INDEX memberships_role_code_idx ON iam.memberships (role_code);
CREATE INDEX memberships_granted_by_idx ON iam.memberships (granted_by);
CREATE UNIQUE INDEX memberships_one_open ON iam.memberships (user_id, aqueduct_id) WHERE valid_to IS NULL;

REVOKE ALL ON iam.memberships FROM PUBLIC;
GRANT SELECT, INSERT ON iam.memberships TO caudal_app;
GRANT UPDATE (valid_to) ON iam.memberships TO caudal_app;
