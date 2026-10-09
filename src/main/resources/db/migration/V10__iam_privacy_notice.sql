-- V10: iam.privacy_notice_versions, iam.privacy_acceptances.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.privacy_notice_versions (
  version                    integer NOT NULL,
  text_es                    text NOT NULL,
  text_sha256                char(64) NOT NULL,
  effective_at               timestamptz NOT NULL,
  CONSTRAINT privacy_notice_versions_pk PRIMARY KEY (version)
);

COMMENT ON TABLE iam.privacy_notice_versions IS 'Versiones del aviso de privacidad (Ley 1581 de 2012).';
COMMENT ON COLUMN iam.privacy_notice_versions.version IS '[P] Número de versión.';
COMMENT ON COLUMN iam.privacy_notice_versions.text_es IS '[P] Texto del aviso.';
COMMENT ON COLUMN iam.privacy_notice_versions.text_sha256 IS '[P] Hash del texto.';
COMMENT ON COLUMN iam.privacy_notice_versions.effective_at IS '[P] Entrada en vigencia.';

REVOKE ALL ON iam.privacy_notice_versions FROM PUBLIC;
GRANT SELECT, INSERT ON iam.privacy_notice_versions TO caudal_app;

CREATE TABLE iam.privacy_acceptances (
  user_id                    uuid,
  version                    integer,
  accepted_at                timestamptz NOT NULL DEFAULT now(),
  ip_hmac                    varchar(64),
  CONSTRAINT privacy_acceptances_pk PRIMARY KEY (user_id, version)
);

COMMENT ON TABLE iam.privacy_acceptances IS 'Aceptación del aviso de privacidad por usuario.';
COMMENT ON COLUMN iam.privacy_acceptances.user_id IS '[I] Usuario.';
COMMENT ON COLUMN iam.privacy_acceptances.version IS '[I] Versión aceptada.';
COMMENT ON COLUMN iam.privacy_acceptances.accepted_at IS '[I] Momento.';
COMMENT ON COLUMN iam.privacy_acceptances.ip_hmac IS '[C] HMAC de la IP.';

ALTER TABLE iam.privacy_acceptances ADD CONSTRAINT privacy_acceptances_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE iam.privacy_acceptances ADD CONSTRAINT privacy_acceptances_version_fk
  FOREIGN KEY (version) REFERENCES iam.privacy_notice_versions (version) ON DELETE RESTRICT;

CREATE INDEX privacy_acceptances_version_idx ON iam.privacy_acceptances (version);

REVOKE ALL ON iam.privacy_acceptances FROM PUBLIC;
GRANT SELECT, INSERT ON iam.privacy_acceptances TO caudal_app;
