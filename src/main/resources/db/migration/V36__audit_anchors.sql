-- V36: audit.audit_anchors.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE audit.audit_anchors (
  aqueduct_id                uuid,
  anchor_date                date,
  head_hash                  char(64) NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT audit_anchors_pk PRIMARY KEY (aqueduct_id, anchor_date)
);

COMMENT ON TABLE audit.audit_anchors IS 'Hash cabeza diario de la cadena (se imprime en las actas).';
COMMENT ON COLUMN audit.audit_anchors.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN audit.audit_anchors.anchor_date IS '[I] Día.';
COMMENT ON COLUMN audit.audit_anchors.head_hash IS '[I] Último row_hash del día.';
COMMENT ON COLUMN audit.audit_anchors.created_at IS '[I] Momento.';

ALTER TABLE audit.audit_anchors ADD CONSTRAINT audit_anchors_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;

REVOKE ALL ON audit.audit_anchors FROM PUBLIC;
GRANT SELECT ON audit.audit_anchors TO caudal_app;
